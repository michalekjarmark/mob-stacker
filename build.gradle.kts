// The build of one node, "<minecraft>-<loader>" (see settings.gradle.kts). Stonecutter runs it once
// per node, from versions/<node>/, with that node's gradle.properties.
plugins {
    id("dev.architectury.loom")
}

val loader = project.name.substringAfterLast('-')
val minecraft = property("minecraft_version") as String
val javaVersion = (property("java_version") as String).toInt()

group = property("maven_group") as String
version = property("mod_version") as String

base {
    // e.g. mobstacker-1.20.1-fabric-1.12.0.jar
    archivesName = "mobstacker-$minecraft-$loader"
}

dependencies {
    minecraft("net.minecraft:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())

    when (loader) {
        "fabric" -> {
            modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
            // Fabric API: networking, keys, client commands, world render events. Nothing else.
            modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
        }
        "forge" -> "forge"("net.minecraftforge:forge:${property("forge_version")}")
    }
}

loom {
    accessWidenerPath = rootProject.file("src/main/resources/mobstacker.accesswidener")

    if (loader == "forge") {
        forge {
            convertAccessWideners = true
            extraAccessWideners.add("mobstacker.accesswidener")
            mixinConfig("mobstacker.mixins.json")
            mixinConfig("mobstacker-forge.mixins.json")
        }
    }
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
// syntax), minecraft_range_maven and forge_loader_range (Forge/NeoForge), pack_format.
val metadata = mapOf("version" to project.version.toString(), "java_version" to javaVersion.toString()) +
    listOf("minecraft_range", "minecraft_range_maven", "forge_loader_range", "pack_format")
        .mapNotNull { key -> findProperty(key)?.let { key to it.toString() } }

tasks.processResources {
    inputs.properties(metadata)
    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")) {
        expand(metadata)
    }
    // Mixins whose target is gone from a version on: left out of the mixin config there (the class
    // still compiles - an accessor is only an annotation - but Mixin would fail on the missing field).
    val mixinsBefore = mapOf(
        "ParrotAccessor" to "1.20.5", // Parrot.TAME_FOOD became the item tag #parrot_food
    )
    val droppedMixins = mixinsBefore.filter { (_, version) -> stonecutter.current.parsed >= version }.keys
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
        filesMatching("*.mixins.json") {
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
        if (otherJar != null) mods.from(otherJar) else mods.from(tasks.named("remapJar"))
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
    from(tasks.named("remapJar"))
    into(rootProject.layout.buildDirectory.dir("libs/${project.version}"))
    dependsOn("build")
}
