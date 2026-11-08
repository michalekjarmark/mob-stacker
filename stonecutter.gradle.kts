plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17-SNAPSHOT" apply false
    // The Modded MC Wiki's toolkit: `./gradlew previewDocs` shows the wiki in /wiki locally
    // (http://localhost:3000, needs Node.js 20+), before anything is published, and
    // `revalidateDocs` refreshes the published wiki. previewDocs always runs the newest
    // @sinytra/wiki-previewer; 0.1.188 (2026-10-30) fails with "Missing environment variable
    // BACKEND_API_KEY". Until that is fixed, run an older one by hand from the repo root:
    //   PowerShell: $env:LOCAL_DOCS_ROOTS = "$PWD\wiki"; npx -y @sinytra/wiki-previewer@0.1.170
    id("org.moddedmc.wiki.toolkit") version "0.5.1"
}

stonecutter active "1.20.1-fabric"

// From 26.1 Minecraft ships unobfuscated, with Mojang's names: those nodes build with Architectury
// Loom's no-remap mode. That plugin only marks the project and applies Loom, and it has to come before
// Loom itself, so it is applied here, before the node's build.gradle.kts applies Loom.
subprojects {
    if (!name.startsWith("1.")) pluginManager.apply("dev.architectury.loom-no-remap")
}

stonecutter parameters {
    // `//? if fabric {` / `//? if forge {` / `//? if neoforge {` in the sources.
    constants.match(current.project.substringAfterLast('-'), "fabric", "forge", "neoforge")
}

wiki {
    docs {
        // The name is the wiki project id from wiki/sinytra-wiki.json.
        register("mobstacker-restacked") {
            root = file("wiki")
        }
    }
}
