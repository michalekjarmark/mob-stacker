import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lists every NBT key vanilla reads or writes on an entity, for strict data matching (MobData): every
 * string constant in a method of a {@code net/minecraft/world/entity/**} class that takes or returns a
 * {@code CompoundTag} (lambdas included - they take the tag they write into).
 *
 * <p>Run once per Minecraft version, on the <b>vanilla</b> jar (not Forge's or NeoForge's patched one:
 * their own data, ForgeData and the like, is exactly what strict matching compares), and the output
 * kept as {@code src/main/vanilla-keys/<version>.txt}:
 * {@code java -cp asm.jar:asm-tree.jar tools/VanillaKeys.java <minecraft jar> > src/main/vanilla-keys/<version>.txt}
 */
public final class VanillaKeys {
    public static void main(String[] args) throws IOException {
        TreeSet<String> keys = new TreeSet<>();
        try (ZipFile jar = new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("net/minecraft/world/entity/") || !entry.getName().endsWith(".class")) {
                    continue;
                }
                ClassNode node = new ClassNode();
                try (InputStream in = jar.getInputStream(entry)) {
                    new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
                }
                for (MethodNode method : node.methods) {
                    if (!method.desc.contains("Lnet/minecraft/nbt/CompoundTag;")) {
                        continue;
                    }
                    for (AbstractInsnNode insn : method.instructions) {
                        // A key has no spaces; what has them is a log or crash report message.
                        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text && !text.isEmpty()
                                && text.chars().noneMatch(Character::isWhitespace)) {
                            keys.add(text);
                        }
                    }
                }
            }
        }
        keys.forEach(System.out::println);
    }
}
