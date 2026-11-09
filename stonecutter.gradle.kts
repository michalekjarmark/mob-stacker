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

    // Classes Mojang renamed or moved to another package, said once for the whole source tree instead
    // of a condition at every import. Each is a regular expression both ways, whole names only, so
    // switching the active version back gives the committed source again. The versions are the best
    // known ones (upstream's 1.21.11 code imports the new packages for sheep, cows, cats, foxes,
    // wolves, villagers and zombie villagers); a node in between confirms or moves them.
    fun replaceBothWays(since: String, from: String, to: String, wholeWord: Boolean) {
        val end = if (wholeWord) "\\b" else ""
        replacements.regex(current.parsed >= since) {
            replace("\\b" + Regex.escape(from) + end, to, "\\b" + Regex.escape(to) + end, from)
        }
    }
    fun moved(since: String, from: String, to: String) = replaceBothWays(since, from, to, wholeWord = true)

    // ResourceLocation became Identifier, and with it ResourceLocationArgument and the rest.
    replaceBothWays("1.21.11", "ResourceLocation", "Identifier", wholeWord = false)
    // The horses' package became "equine".
    replaceBothWays("1.21.11", "net.minecraft.world.entity.animal.horse.", "net.minecraft.world.entity.animal.equine.", wholeWord = false)
    moved("1.21.11", "net.minecraft.world.entity.animal.Cat", "net.minecraft.world.entity.animal.feline.Cat")
    moved("1.21.11", "net.minecraft.world.entity.animal.Ocelot", "net.minecraft.world.entity.animal.feline.Ocelot")
    moved("1.21.11", "net.minecraft.world.entity.animal.Chicken", "net.minecraft.world.entity.animal.chicken.Chicken")
    moved("1.21.11", "net.minecraft.world.entity.animal.Cow", "net.minecraft.world.entity.animal.cow.Cow")
    moved("1.21.11", "net.minecraft.world.entity.animal.MushroomCow", "net.minecraft.world.entity.animal.cow.MushroomCow")
    moved("1.21.11", "net.minecraft.world.entity.animal.Fox", "net.minecraft.world.entity.animal.fox.Fox")
    moved("1.21.11", "net.minecraft.world.entity.animal.IronGolem", "net.minecraft.world.entity.animal.golem.IronGolem")
    moved("1.21.11", "net.minecraft.world.entity.animal.SnowGolem", "net.minecraft.world.entity.animal.golem.SnowGolem")
    moved("1.21.11", "net.minecraft.world.entity.animal.Panda", "net.minecraft.world.entity.animal.panda.Panda")
    moved("1.21.11", "net.minecraft.world.entity.animal.Parrot", "net.minecraft.world.entity.animal.parrot.Parrot")
    moved("1.21.11", "net.minecraft.world.entity.animal.Pig", "net.minecraft.world.entity.animal.pig.Pig")
    moved("1.21.11", "net.minecraft.world.entity.animal.Rabbit", "net.minecraft.world.entity.animal.rabbit.Rabbit")
    moved("1.21.11", "net.minecraft.world.entity.animal.Sheep", "net.minecraft.world.entity.animal.sheep.Sheep")
    moved("1.21.11", "net.minecraft.world.entity.animal.TropicalFish", "net.minecraft.world.entity.animal.fish.TropicalFish")
    moved("1.21.11", "net.minecraft.world.entity.animal.Turtle", "net.minecraft.world.entity.animal.turtle.Turtle")
    moved("1.21.11", "net.minecraft.world.entity.animal.Wolf", "net.minecraft.world.entity.animal.wolf.Wolf")
    moved("1.21.11", "net.minecraft.world.entity.animal.Bucketable", "net.minecraft.world.entity.Bucketable")
    moved("1.21.11", "net.minecraft.world.entity.monster.Zombie", "net.minecraft.world.entity.monster.zombie.Zombie")
    moved("1.21.11", "net.minecraft.world.entity.monster.ZombieVillager", "net.minecraft.world.entity.monster.zombie.ZombieVillager")
    moved("1.21.11", "net.minecraft.world.entity.monster.ZombifiedPiglin", "net.minecraft.world.entity.monster.zombie.ZombifiedPiglin")
    moved("1.21.11", "net.minecraft.world.entity.npc.AbstractVillager", "net.minecraft.world.entity.npc.villager.AbstractVillager")
    moved("1.21.11", "net.minecraft.world.entity.npc.Villager", "net.minecraft.world.entity.npc.villager.Villager")
    moved("1.21.11", "net.minecraft.world.entity.npc.VillagerProfession", "net.minecraft.world.entity.npc.villager.VillagerProfession")
    moved("26.1", "net.minecraft.world.entity.monster.Slime", "net.minecraft.world.entity.monster.cubemob.Slime")
    moved("26.1", "EnderMan", "Enderman")
    moved("26.1", "net.minecraft.client.GuiMessage", "net.minecraft.client.multiplayer.chat.GuiMessage")
    moved("26.1", "net.minecraft.client.GuiMessageTag", "net.minecraft.client.multiplayer.chat.GuiMessageTag")
}

wiki {
    docs {
        // The name is the wiki project id from wiki/sinytra-wiki.json.
        register("mobstacker-restacked") {
            root = file("wiki")
        }
    }
}
