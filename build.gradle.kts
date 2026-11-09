// The build of one node, "<minecraft>-<loader>" (see settings.gradle.kts). Stonecutter runs it once
// per node, from versions/<node>/, with that node's gradle.properties.
plugins {
    id("dev.architectury.loom")
}

val loader = project.name.substringAfterLast('-')
val minecraft = property("minecraft_version") as String
val javaVersion = (property("java_version") as String).toInt()
// 26.1 and newer: Minecraft is not obfuscated, and Loom runs without remapping (applied from
// stonecutter.gradle.kts): no mappings, plain configurations, and the jar is `jar`, not `remapJar`.
val unobfuscated = stonecutter.current.parsed >= "26.1"
val modImpl = if (unobfuscated) "implementation" else "modImplementation"
val modJar = if (unobfuscated) "jar" else "remapJar"

group = property("maven_group") as String
version = property("mod_version") as String

base {
    // e.g. mobstacker-1.20.1-fabric-1.12.0.jar
    archivesName = "mobstacker-$minecraft-$loader"
}

repositories {
    if (loader == "neoforge") maven("https://maven.neoforged.net/releases/")
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraft")
    // A string, not the accessor: an unobfuscated node has no `mappings` configuration at all.
    if (!unobfuscated) "mappings"(loom.officialMojangMappings())

    when (loader) {
        "fabric" -> {
            modImpl("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
            // Fabric API: networking, keys, client commands, world render events. Nothing else.
            modImpl("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
        }
        "forge" -> "forge"("net.minecraftforge:forge:${property("forge_version")}")
        // A string, like the others: unobfuscated nodes have no neoForge accessor.
        "neoforge" -> "neoForge"("net.neoforged:neoforge:${property("neoforge_version")}")
    }
}

// The access widener is written in Mojang's names, which are the "named" namespace while Minecraft is
// obfuscated and the "official" one from 26.1. Unobfuscated nodes get a copy with that header (Loom
// reads it while configuring, so the copy is written here), and the jar gets the same (processResources).
val accessWidenerSource = rootProject.file("src/main/resources/mobstacker.accesswidener")
// Widener lines for members a version no longer has (Loom refuses a line it cannot find), by the
// "Class member" text in the line and the version they are gone from.
val widenerBefore = mapOf(
    "LivingEntity deathScore" to "1.21.2", // the kill score is no longer a field
)
val droppedWidenerLines = widenerBefore.filter { (_, version) -> stonecutter.current.parsed >= version }.keys
fun nodeAccessWidenerLine(line: String) =
    if (droppedWidenerLines.any { line.contains(it) }) "" else if (unobfuscated) officialAccessWidener(line) else line
fun officialAccessWidener(text: String) = text.replaceFirst("accessWidener v2 named", "accessWidener v2 official")
val accessWidener = if (!unobfuscated && droppedWidenerLines.isEmpty()) accessWidenerSource else
    layout.buildDirectory.file("mobstacker.accesswidener").get().asFile.also { copy ->
        val text = accessWidenerSource.readLines().joinToString("\n") { nodeAccessWidenerLine(it) } + "\n"
        if (!copy.isFile || copy.readText() != text) {
            copy.parentFile.mkdirs()
            copy.writeText(text)
        }
    }

loom {
    accessWidenerPath = accessWidener

    if (loader == "forge") {
        forge {
            convertAccessWideners = true
            extraAccessWideners.add("mobstacker.accesswidener")
            mixinConfig("mobstacker.mixins.json")
            mixinConfig("mobstacker-forge.mixins.json")
        }
    }
}

// NeoForge reads access transformers, not wideners: the jar gets the widener converted into
// META-INF/accesstransformer.cfg (named in neoforge.mods.toml). In dev Loom applies it by itself.
if (loader == "neoforge" && !unobfuscated) {
    tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
        atAccessWideners.add("mobstacker.accesswidener")
    }
}

// From 26.1 there is no remapJar to convert it, and the widener's names are the names the game runs
// with: the access transformer is written from the node's widener lines here (like the widener copy
// above, while configuring) and packed as a resource.
fun accessTransformerOf(widener: String): String = widener.lines().mapNotNull { line ->
    val parts = line.trim().split(Regex("\\s+"))
    val access = when (parts.firstOrNull()) {
        "accessible" -> "public"
        "mutable", "extendable" -> "public-f"
        else -> return@mapNotNull null
    }
    when {
        parts.size == 3 && parts[1] == "class" -> "$access ${parts[2].replace('/', '.')}"
        parts.size == 5 && parts[1] == "field" -> "$access ${parts[2].replace('/', '.')} ${parts[3]}"
        parts.size == 5 && parts[1] == "method" -> "$access ${parts[2].replace('/', '.')} ${parts[3]}${parts[4]}"
        else -> throw GradleException("Cannot turn this access widener line into an access transformer: $line")
    }
}.joinToString("\n", postfix = "\n")
if (loader == "neoforge" && unobfuscated) {
    val generated = layout.buildDirectory.dir("generated/accesstransformer").get().asFile
    val at = generated.resolve("META-INF/accesstransformer.cfg")
    val text = accessTransformerOf(accessWidener.readText())
    if (!at.isFile || at.readText() != text) {
        at.parentFile.mkdirs()
        at.writeText(text)
    }
    sourceSets.main { resources.srcDir(generated) }
    // The widener itself is Fabric's; NeoForge reads the transformer (remapJar leaves it out below 26.1).
    tasks.named<Jar>("jar") { exclude("mobstacker.accesswidener") }
}

// Every loader's code is in the one source tree, in its own package; a node compiles its own.
val otherLoaders = listOf("fabric", "forge", "neoforge") - loader
sourceSets.main {
    java {
        otherLoaders.forEach { exclude("com/frikinjay/mobstacker/$it/**") }
    }
    resources {
        if (loader != "fabric") exclude("fabric.mod.json", "mobstacker-fabric.mixins.json")
        if (loader != "forge") exclude("META-INF/mods.toml", "mobstacker-forge.mixins.json", "pack.mcmeta")
        if (loader != "neoforge") exclude("META-INF/neoforge.mods.toml", "mobstacker-neoforge.mixins.json")
    }
}

java {
    withSourcesJar()
    // Each node compiles with the JDK its Minecraft ships with (17 for 1.20.1, 21 from 1.20.5),
    // while Gradle itself runs on 25 (Stonecutter needs it). If Gradle does not find the JDK, list
    // it in ~/.gradle/gradle.properties: org.gradle.java.installations.paths=<jdk17>,<jdk21>
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
    // -PallErrors: every error at once, not the first hundred that stop at the first failing phase
    // (handy when a new version is added).
    if (project.hasProperty("allErrors")) {
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "5000", "-XDshould-stop.ifError=FLOW"))
    }
}

// What the jar says it runs on comes from the node's gradle.properties: minecraft_range (Fabric's
// syntax), minecraft_range_maven and forge_loader_range (Forge/NeoForge), neoforge_range, pack_format.
val metadata = mapOf("version" to project.version.toString(), "java_version" to javaVersion.toString()) +
    listOf("minecraft_range", "minecraft_range_maven", "forge_loader_range", "neoforge_range", "pack_format")
        .mapNotNull { key -> findProperty(key)?.let { key to it.toString() } }

// The NBT keys vanilla writes on an entity in this Minecraft, for strict data matching (MobData).
// Generated once per version by tools/VanillaKeys.java (see versions/README.md).
val vanillaKeys = rootProject.file("src/main/vanilla-keys/$minecraft.txt")

tasks.processResources {
    inputs.properties(metadata)
    inputs.file(vanillaKeys)
    if (!vanillaKeys.isFile) {
        throw GradleException("No $vanillaKeys: generate it with tools/VanillaKeys.java (versions/README.md)")
    }
    from(vanillaKeys) {
        rename { "mobstacker_vanilla_keys.txt" }
    }
    inputs.property("droppedWidenerLines", droppedWidenerLines.sorted().joinToString())
    inputs.property("unobfuscated", unobfuscated)
    if (unobfuscated || droppedWidenerLines.isNotEmpty()) {
        filesMatching("mobstacker.accesswidener") { filter { line -> nodeAccessWidenerLine(line) } }
    }
    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")) {
        expand(metadata)
    }
    // Mixins whose target is gone from a version on: left out of the mixin config there (the class
    // still compiles - an accessor is only an annotation - but Mixin would fail on the missing field).
    val mixinsBefore = mapOf(
        "ParrotAccessor" to "1.20.5", // Parrot.TAME_FOOD became the item tag #parrot_food
        // 26.1: the region boxes are gizmos (client.LevelRendererGizmosMixin), and the sheep's wool
        // reads its name from a render state.
        "client.LevelRendererMixin" to "26.1",
        "client.SheepFurLayerMixin" to "26.1",
        // 26.1 (unconfirmed): these conversions are convertTo (MobMixin carries the stack), spawning
        // reads the level's entities in NaturalSpawner, the easter-egg names are read off render
        // states, and armour stands no longer tick on their own.
        "ServerChunkCacheMixin" to "26.1",
        "ArmorStandMixin" to "26.1",
        "mobs.PigMixin" to "26.1",
        "mobs.VillagerMixin" to "26.1",
        "mobs.TadpoleMixin" to "26.1",
        "mobs.ZombieMixin" to "26.1",
        "client.LivingEntityRendererMixin" to "26.1",
        "client.RabbitRendererMixin" to "26.1",
    )
    // And the other way round: mixins for a target that only exists from a version on.
    val mixinsFrom = mapOf(
        "client.LevelRendererGizmosMixin" to "26.1",
        "client.EntityRenderStateMixin" to "26.1",
        "NaturalSpawnerMixin" to "26.1",
        // 1.21.6 (unconfirmed): a clicked command in a chat panel is a custom click action.
        "ServerCommonPacketListenerImplMixin" to "1.21.6",
    )
    val droppedMixins = mixinsBefore.filter { (_, version) -> stonecutter.current.parsed >= version }.keys +
        mixinsFrom.filter { (_, version) -> stonecutter.current.parsed < version }.keys
    inputs.property("droppedMixins", droppedMixins.sorted().joinToString())
    if (droppedMixins.isNotEmpty()) {
        filesMatching("*.mixins.json") {
            filter { line -> if (droppedMixins.any { line.trim().removeSuffix(",") == "\"$it\"" }) "" else line }
        }
    }
    // -PstrictMixins: every injector must find its target (unless it says require = 0), so a target
    // a new Minecraft version moved fails at once instead of silently doing nothing. For testing.
    val strictMixins = project.hasProperty("strictMixins")
    inputs.property("strictMixins", strictMixins)
    if (strictMixins) {
        // Forge's own config already says defaultRequire 1; a second "injectors" key would be invalid.
        filesMatching(listOf("mobstacker.mixins.json", "mobstacker-fabric.mixins.json", "mobstacker-neoforge.mixins.json")) {
            filter { line -> line.replace("\"required\": true,", "\"required\": true, \"injectors\": { \"defaultRequire\": 1 },") }
        }
    }
    // 1.21 renamed the data pack folders to the singular (tags/entity_types -> tags/entity_type).
    if (stonecutter.current.parsed >= "1.21") {
        eachFile { path = path.replace("/tags/entity_types/", "/tags/entity_type/") }
    }
}

// `./gradlew :<node>:checkMixins`: every mixin against this node's Minecraft, without running it
// (tools/MixinCheck.java): target classes, @Shadow, @Accessor/@Invoker, injector methods and what
// their @At points at. Loom 1.17 has no annotation processor to say so at compile time.
val mixinCheckAsm = configurations.detachedConfiguration(dependencies.create("org.ow2.asm:asm-tree:9.8"))
tasks.register<JavaExec>("checkMixins") {
    group = "verification"
    dependsOn("classes")
    val classpathList = layout.buildDirectory.file("mixin-check-classpath.txt")
    val compileJars = sourceSets.main.get().compileClasspath
    val classesDir = sourceSets.main.get().java.destinationDirectory
    val resourcesDir = sourceSets.main.get().output.resourcesDir!!
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    classpath = mixinCheckAsm
    // A source file as the main class: java runs it straight from source.
    mainClass = rootProject.file("tools/MixinCheck.java").absolutePath
    doFirst {
        classpathList.get().asFile.writeText(compileJars.files.joinToString("\n") { it.absolutePath })
    }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(classesDir.get().asFile.absolutePath, resourcesDir.absolutePath, "@" + classpathList.get().asFile.absolutePath)
    })
}

// `./gradlew :<node>:prodServer` (Fabric): a real server with the built jar - remapped, as players
// get it, not the dev environment - and the self-test command on. Run dir versions/<node>/run/prod
// (git-ignored); put eula.txt and server.properties (RCON) there first. To run another jar instead
// (e.g. a released one, for the same test on both), pass -PprodJar=<path> -PprodDir=<run dir name>.
if (loader == "fabric") {
    val fabricApi = configurations.detachedConfiguration(
        dependencies.create("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    ).apply { isTransitive = false }
    val fabricLoader = property("fabric_loader_version") as String
    tasks.register<net.fabricmc.loom.task.prod.ServerProductionRunTask>("prodServer") {
        group = "verification"
        val otherJar = providers.gradleProperty("prodJar").orNull
        if (otherJar != null) mods.from(otherJar) else mods.from(tasks.named(modJar))
        mods.from(fabricApi)
        minecraftVersion = minecraft
        loaderVersion = fabricLoader
        jvmArgs.add("-Dmobstacker.selftest=true")
        programArgs.add("nogui")
        runDir = layout.projectDirectory.dir("run/" + providers.gradleProperty("prodDir").getOrElse("prod"))
    }
}

// `./gradlew buildAndCollect`: every node's jar in build/libs/<mod_version>/ at the root.
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.named(modJar))
    into(rootProject.layout.buildDirectory.dir("libs/${project.version}"))
    dependsOn("build")
}
