import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks every mixin of the mod against one Minecraft version, without running the game: that each
 * target class exists, that every @Shadow, @Accessor and @Invoker finds its member, that every
 * injector's method exists and that what its @At points at (an INVOKE, a FIELD, a NEW, a CONSTANT) is
 * really in that method's bytecode. Since Loom 1.17 no annotation processor checks this at compile
 * time, and the game only reports a missing target one class at a time, when that class loads - or,
 * for an injector without require, never.
 *
 * <p>Run by {@code ./gradlew :<node>:checkMixins}; by hand:
 * {@code java -cp asm.jar:asm-tree.jar tools/MixinCheck.java <compiled classes dir> <processed resources dir>
 * <named minecraft jar or @file listing jars>...}
 */
public final class MixinCheck {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");

    private final List<ZipFile> jars = new ArrayList<>();
    private final Map<String, ClassNode> cache = new HashMap<>();
    private final List<String> problems = new ArrayList<>();
    private int checked;

    public static void main(String[] args) throws IOException {
        MixinCheck check = new MixinCheck();
        // The mixins the node's configs list (processed resources), so one left out for this version
        // is not checked against it.
        Set<String> listed = new java.util.HashSet<>();
        try (Stream<Path> walk = Files.list(Path.of(args[1]))) {
            for (Path config : walk.filter(p -> p.toString().endsWith(".mixins.json")).toList()) {
                String json = Files.readString(config);
                java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                String prefix = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
                java.util.regex.Matcher arrays = java.util.regex.Pattern
                        .compile("\"(?:mixins|client|server)\"\\s*:\\s*\\[([^\\]]*)]").matcher(json);
                while (arrays.find()) {
                    java.util.regex.Matcher entry = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(arrays.group(1));
                    while (entry.find()) {
                        listed.add(prefix + entry.group(1).replace('.', '/'));
                    }
                }
            }
        }
        for (int i = 2; i < args.length; i++) {
            // @file: one jar per line (the compile classpath is too long for a Windows command line).
            List<String> paths = args[i].startsWith("@") ? Files.readAllLines(Path.of(args[i].substring(1))) : List.of(args[i]);
            for (String path : paths) {
                if (!path.isBlank() && path.endsWith(".jar") && Files.isRegularFile(Path.of(path))) {
                    check.jars.add(new ZipFile(path));
                }
            }
        }
        List<Path> classes;
        try (Stream<Path> walk = Files.walk(Path.of(args[0]))) {
            classes = walk.filter(p -> p.toString().endsWith(".class")).toList();
        }
        for (Path file : classes) {
            ClassNode node = read(Files.readAllBytes(file));
            boolean isMixin = annotation(node.invisibleAnnotations, MIXIN) != null || annotation(node.visibleAnnotations, MIXIN) != null;
            if (isMixin && listed.contains(node.name)) {
                check.mixin(node);
            } else if (isMixin) {
                System.out.println("(not in this version's mixin configs: " + node.name + ")");
            }
        }
        if (listed.isEmpty()) {
            check.problems.add("no mixin config found in " + args[1]);
        }
        check.problems.forEach(System.out::println);
        System.out.println("MixinCheck: " + check.checked + " members checked, " + check.problems.size() + " problem(s)");
        if (!check.problems.isEmpty()) {
            System.exit(1);
        }
    }

    private void mixin(ClassNode mixin) {
        AnnotationNode at = annotation(mixin.invisibleAnnotations, MIXIN);
        if (at == null) {
            at = annotation(mixin.visibleAnnotations, MIXIN);
        }
        List<String> targets = new ArrayList<>();
        for (Object value : list(at, "value")) {
            targets.add(((Type) value).getInternalName());
        }
        for (Object value : list(at, "targets")) {
            targets.add(((String) value).replace('.', '/'));
        }
        String name = mixin.name.substring(mixin.name.lastIndexOf('/') + 1);
        for (String target : targets) {
            ClassNode node = find(target);
            checked++;
            if (node == null) {
                problems.add(name + ": target class " + target + " does not exist");
                continue;
            }
            boolean isInterface = (mixin.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
            for (FieldNode field : mixin.fields) {
                if (annotation(field.invisibleAnnotations, SHADOW) != null || annotation(field.visibleAnnotations, SHADOW) != null) {
                    checked++;
                    if (!hasField(node, field.name, field.desc)) {
                        problems.add(name + ": @Shadow field " + field.name + " " + field.desc + " not in " + target);
                    }
                }
            }
            for (MethodNode method : mixin.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
                if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
                for (AnnotationNode annotation : annotations) {
                    if (annotation.desc.equals(SHADOW)) {
                        checked++;
                        String shadowName = method.name.startsWith("shadow$") ? method.name.substring(7) : method.name;
                        if (!hasMethod(node, shadowName, method.desc)) {
                            problems.add(name + ": @Shadow method " + shadowName + method.desc + " not in " + target);
                        }
                    } else if (annotation.desc.equals(ACCESSOR)) {
                        checked++;
                        String field = string(annotation, "value", accessorName(method.name));
                        if (!hasFieldNamed(node, field)) {
                            problems.add(name + ": @Accessor field " + field + " not in " + target);
                        }
                    } else if (annotation.desc.equals(INVOKER)) {
                        checked++;
                        String invoked = string(annotation, "value", invokerName(method.name));
                        String desc = invoked.equals("<init>") ? null : method.desc;
                        if (!hasMethod(node, invoked, desc)) {
                            problems.add(name + ": @Invoker " + invoked + method.desc + " not in " + target);
                        }
                    } else if (INJECTORS.contains(annotation.desc)) {
                        injector(name, node, method, annotation);
                    }
                }
            }
            if (isInterface) {
                continue;
            }
        }
    }

    private void injector(String mixin, ClassNode target, MethodNode handler, AnnotationNode injector) {
        String kind = injector.desc.substring(injector.desc.lastIndexOf('/') + 1, injector.desc.length() - 1);
        int require = integer(injector, "require", -1);
        List<MethodNode> methods = new ArrayList<>();
        for (Object selector : list(injector, "method")) {
            checked++;
            List<MethodNode> found = select(target, (String) selector);
            if (found.isEmpty()) {
                if (require != 0) {
                    problems.add(mixin + "." + handler.name + ": @" + kind + " method \"" + selector + "\" not in " + target.name);
                }
            }
            methods.addAll(found);
        }
        if (methods.isEmpty()) {
            return;
        }
        if (kind.equals("Inject")) {
            // The handler takes either nothing but the callback, or every argument of the target
            // method first (then the callback, then any captured locals).
            Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
            int callback = -1;
            for (int i = 0; i < handlerArgs.length; i++) {
                if (handlerArgs[i].getInternalName().startsWith("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")) {
                    callback = i;
                    break;
                }
            }
            for (MethodNode method : methods) {
                checked++;
                Type[] targetArgs = Type.getArgumentTypes(method.desc);
                boolean ok = callback == 0 || (callback == targetArgs.length
                        && java.util.Arrays.equals(java.util.Arrays.copyOf(handlerArgs, callback), targetArgs));
                if (!ok) {
                    problems.add(mixin + "." + handler.name + ": @Inject handler " + handler.desc
                            + " does not take the arguments of " + target.name + "." + method.name + method.desc);
                }
                // A method that returns something needs CallbackInfoReturnable, a void one CallbackInfo.
                if (callback >= 0) {
                    boolean returns = Type.getReturnType(method.desc).getSort() != Type.VOID;
                    boolean returnable = handlerArgs[callback].getInternalName().endsWith("CallbackInfoReturnable");
                    if (returns != returnable && !method.name.equals("<init>")) {
                        problems.add(mixin + "." + handler.name + ": @Inject takes " + handlerArgs[callback].getClassName()
                                + " but " + target.name + "." + method.name + method.desc
                                + (returns ? " returns a value" : " returns nothing"));
                    }
                }
            }
        }
        List<AnnotationNode> ats = new ArrayList<>();
        Object at = value(injector, "at");
        if (at instanceof AnnotationNode node) {
            ats.add(node);
        } else if (at instanceof List<?> nodes) {
            nodes.forEach(n -> ats.add((AnnotationNode) n));
        }
        for (AnnotationNode point : ats) {
            String type = string(point, "value", "");
            String atTarget = string(point, "target", "");
            boolean found;
            switch (type) {
                case "INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING" -> found = atTarget.isEmpty() || methods.stream().anyMatch(m -> hasInvoke(m, atTarget));
                case "FIELD" -> found = atTarget.isEmpty() || methods.stream().anyMatch(m -> hasField(m, atTarget));
                case "NEW" -> found = atTarget.isEmpty() || methods.stream().anyMatch(m -> hasNew(m, atTarget));
                case "CONSTANT" -> found = methods.stream().anyMatch(m -> hasConstant(m, list(point, "args")));
                default -> found = true; // HEAD, TAIL, RETURN, LOAD, STORE, JUMP...
            }
            checked++;
            if (!found) {
                String problem = mixin + "." + handler.name + ": @" + kind + " @At(" + type + ") "
                        + (atTarget.isEmpty() ? list(point, "args") : atTarget) + " not found in " + target.name
                        + "." + methods.get(0).name + methods.get(0).desc;
                // require = 0 is allowed to miss (another mod may have rewritten the spot), but on plain
                // Minecraft a miss means the feature is silently off: said, not failed.
                if (require == 0) {
                    System.out.println("warning (require = 0): " + problem);
                } else {
                    problems.add(problem);
                }
            }
        }
    }

    // --- Bytecode searches ------------------------------------------------------------------------

    private static boolean hasInvoke(MethodNode method, String target) {
        Member member = Member.parse(target);
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && member.matches(call.owner, call.name, call.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasField(MethodNode method, String target) {
        Member member = Member.parse(target);
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof FieldInsnNode field && member.matches(field.owner, field.name, field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNew(MethodNode method, String target) {
        String type = target.startsWith("L") && target.endsWith(";") ? target.substring(1, target.length() - 1) : target.replace('.', '/');
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof TypeInsnNode node && node.getOpcode() == org.objectweb.asm.Opcodes.NEW && node.desc.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConstant(MethodNode method, List<Object> args) {
        for (Object arg : args) {
            String text = (String) arg;
            int eq = text.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = text.substring(0, eq);
            String value = text.substring(eq + 1);
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof LdcInsnNode ldc && String.valueOf(ldc.cst).equals(value)
                        && (key.equals("stringValue") == ldc.cst instanceof String)) {
                    return true;
                }
            }
            if (!key.equals("stringValue")) {
                return true; // small int constants are not LDCs; not worth decoding here
            }
            return false;
        }
        return true;
    }

    // --- Class lookups ----------------------------------------------------------------------------

    private List<MethodNode> select(ClassNode target, String selector) {
        String name = selector;
        String desc = null;
        if (name.startsWith("L") && name.contains(";")) {
            name = name.substring(name.indexOf(';') + 1);
        }
        int paren = name.indexOf('(');
        if (paren >= 0) {
            desc = name.substring(paren);
            name = name.substring(0, paren);
        }
        List<MethodNode> found = new ArrayList<>();
        for (MethodNode method : target.methods) {
            if ((name.equals("*") || method.name.equals(name)) && (desc == null || method.desc.equals(desc))) {
                found.add(method);
            }
        }
        return found;
    }

    private boolean hasMethod(ClassNode node, String name, String desc) {
        for (ClassNode c = node; c != null; c = c.superName == null ? null : find(c.superName)) {
            for (MethodNode method : c.methods) {
                if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) {
                    return true;
                }
            }
            for (String itf : c.interfaces) {
                ClassNode i = find(itf);
                if (i != null && hasMethod(i, name, desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasField(ClassNode node, String name, String desc) {
        for (ClassNode c = node; c != null; c = c.superName == null ? null : find(c.superName)) {
            for (FieldNode field : c.fields) {
                if (field.name.equals(name) && field.desc.equals(desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasFieldNamed(ClassNode node, String name) {
        for (ClassNode c = node; c != null; c = c.superName == null ? null : find(c.superName)) {
            for (FieldNode field : c.fields) {
                if (field.name.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ClassNode find(String internalName) {
        if (cache.containsKey(internalName)) {
            return cache.get(internalName);
        }
        ClassNode node = null;
        for (ZipFile jar : jars) {
            ZipEntry entry = jar.getEntry(internalName + ".class");
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    node = read(in.readAllBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
        }
        cache.put(internalName, node);
        return node;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        return node;
    }

    // --- Annotation values ------------------------------------------------------------------------

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (value == null) {
            return List.of();
        }
        return value instanceof List<?> l ? (List<Object>) l : List.of(value);
    }

    private static String string(AnnotationNode annotation, String key, String fallback) {
        Object value = value(annotation, key);
        return value == null ? fallback : (String) value;
    }

    private static int integer(AnnotationNode annotation, String key, int fallback) {
        Object value = value(annotation, key);
        return value == null ? fallback : (Integer) value;
    }

    private static String accessorName(String method) {
        for (String prefix : new String[]{"get", "set", "is"}) {
            if (method.startsWith(prefix) && method.length() > prefix.length()) {
                String rest = method.substring(prefix.length());
                return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
            }
        }
        return method;
    }

    private static String invokerName(String method) {
        for (String prefix : new String[]{"call", "invoke"}) {
            if (method.startsWith(prefix) && method.length() > prefix.length()) {
                String rest = method.substring(prefix.length());
                return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
            }
        }
        return method;
    }

    /** {@code Lowner;name(desc)ret} or {@code Lowner;name:desc}, any part may be left out. */
    private record Member(String owner, String name, String desc) {
        static Member parse(String text) {
            String owner = null;
            if (text.startsWith("L") && text.contains(";")) {
                owner = text.substring(1, text.indexOf(';'));
                text = text.substring(text.indexOf(';') + 1);
            }
            String desc = null;
            int paren = text.indexOf('(');
            int colon = text.indexOf(':');
            if (paren >= 0) {
                desc = text.substring(paren);
                text = text.substring(0, paren);
            } else if (colon >= 0) {
                desc = text.substring(colon + 1);
                text = text.substring(0, colon);
            }
            return new Member(owner, text, desc);
        }

        boolean matches(String owner, String name, String desc) {
            return (this.owner == null || this.owner.equals(owner)) && this.name.equals(name)
                    && (this.desc == null || this.desc.equals(desc));
        }
    }
}
