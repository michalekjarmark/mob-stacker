pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://files.minecraftforge.net/maven/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        gradlePluginPortal()
    }
}

plugins {
    // One source tree, one jar per Minecraft version and loader. Version-specific code is written
    // as `//? if >=1.21 {` ... `//?}` comments; see versions/README.md.
    id("dev.kikugie.stonecutter") version "0.10"
}

rootProject.name = "mobstacker"

stonecutter {
    create(rootProject) {
        // A node is "<minecraft>-<loader>"; its properties live in versions/<node>/gradle.properties.
        fun mc(minecraft: String, vararg loaders: String) =
            loaders.forEach { version("$minecraft-$it", minecraft) }

        mc("1.20.1", "fabric", "forge")
        mc("1.21.1", "fabric", "neoforge")

        // The version the sources are committed in. Switch back to it before every commit.
        vcsVersion = "1.20.1-fabric"
    }
}
