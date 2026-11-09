# Building for several Minecraft versions (Stonecutter)

One source tree (`src/main`) builds a jar for every Minecraft version and loader listed in
`settings.gradle.kts`. Each such pair is a **node**, named `<minecraft>-<loader>` (e.g.
`1.20.1-fabric`), and this folder holds one `gradle.properties` per node: the Minecraft version, the
Java version and the loader's own versions. Everything the same on every node (`mod_version`) is in
the root `gradle.properties`.

**Before adding a version, read [`KNOWN-CAVEATS.md`](KNOWN-CAVEATS.md)**: what changed between the
versions built so far that fails silently (invisible text, mouse buttons, typing, shearing loot) or
refuses to load, and where the code handles it.

## Build

Gradle runs on **JDK 25** (Stonecutter 0.10 needs it); each node compiles with the JDK its Minecraft
ships with (17 for 1.20.1, 21 from 1.20.5), through Gradle toolchains. If Gradle cannot find one, list
the JDKs in `~/.gradle/gradle.properties`:

```properties
org.gradle.java.installations.paths=C:/path/to/jdk-17,C:/path/to/jdk-21
```

```sh
JAVA_HOME=<jdk 25> ./gradlew buildAndCollect          # every node -> build/libs/<mod_version>/
JAVA_HOME=<jdk 25> ./gradlew :1.20.1-forge:build      # one node -> versions/1.20.1-forge/build/libs/
```

## Version-specific code

Code that differs between versions is written once, with comments Stonecutter understands. The code
of the versions that are not active stays commented out, so the compiler and the IDE see one version
at a time:

```java
//? if >=1.21 {
ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ns, path);
//?} else {
/*ResourceLocation id = new ResourceLocation(ns, path);
*///?}
```

Loaders are constants: `//? if fabric {`, `//? if forge {`, `//? if neoforge {` (set in
`stonecutter.gradle.kts`). A loader's own classes do not need them: they live in their own package
(`com.frikinjay.mobstacker.fabric`, `.forge`, `.neoforge`), and `build.gradle.kts` leaves the other
loaders' packages and resource files (`fabric.mod.json`, `META-INF/mods.toml`, ...) out of each node.
Where an API changed completely (rendering, the GUI), prefer a separate class per version over long
`if` blocks.

## Switching versions

The IDE shows the **active** version (`stonecutter active` in `stonecutter.gradle.kts`). Switch with
the Gradle task `Set active project to <node>` (group `stonecutter`); it rewrites the comments in
`src/`. **The sources are committed in the `vcsVersion` (`1.20.1-fabric`)**: run `Reset active
project` before every commit, or the diff is full of flipped comments.

Small renamed things (ids, item saving, text JSON, enchantments, mixin target descriptors) are said
once in `compat/Compat.java`; screens extend `client/CompatScreen` (background, mouse wheel, text
boxes). A mixin whose target is gone from a version on goes into `mixinsBefore` in
`build.gradle.kts`, which leaves it out of that version's mixin config.

## Adding a version

1. `mc("<version>", "fabric", ...)` in `settings.gradle.kts`, and `versions/<node>/gradle.properties`
   (copy the nearest one; `minecraft_range` in Fabric's syntax, `minecraft_range_maven` and
   `forge_loader_range` for Forge/NeoForge, `pack_format`).
2. The NBT keys vanilla writes on an entity in that version, for strict data matching (`MobData`):
   once per Minecraft version, on the **vanilla** jar (Loom's
   `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/<version>-*/*.jar`, not a
   Forge/NeoForge patched one - their own data is what strict matching compares):
   `java -cp asm.jar;asm-tree.jar tools/VanillaKeys.java <jar> > src/main/vanilla-keys/<version>.txt`.
   The build refuses a version without its list. Check what the new keys mean for
   `MobStacker.TOP_ONLY_KEYS` (what a copy of a stack's top mob must not get).
3. `./gradlew :<node>:compileJava -PallErrors` lists every compile error at once.
4. `./gradlew :<node>:checkMixins` checks every mixin against that version without running it
   (`tools/MixinCheck.java`): target classes, `@Shadow`, `@Accessor`/`@Invoker`, injector methods,
   handler arguments and return types, and that what each `@At` points at is in the method's bytecode.
   Loom no longer checks any of this at compile time, and the game reports one mixin at a time.
5. `./gradlew :<node>:prodServer -PstrictMixins` (Fabric): a real server; strict, an injector that
   finds nothing fails at once instead of silently doing nothing. Then the self-test over RCON.

## Unobfuscated versions (26.1 and newer)

From 26.1 Minecraft ships with Mojang's own names, so nothing is remapped. `stonecutter.gradle.kts`
applies Architectury Loom's `dev.architectury.loom-no-remap` to those nodes (it only marks the project
and applies Loom, and has to come before it); `build.gradle.kts` (`unobfuscated`) then adds no
mappings, puts Fabric Loader and Fabric API on `implementation` instead of `modImplementation`, and
takes the jar from `jar` instead of `remapJar`. The access widener is written in Mojang's names, which
are the `named` namespace on obfuscated versions and `official` from 26.1: Loom and the jar get a copy
with that header. Java 25. Entities save through `ValueInput`/`ValueOutput` from 1.21.6, which
`tools/VanillaKeys.java` reads as well as `CompoundTag`.

## Testing a jar on a real server (Fabric)

`tools/test/run_server.py` does all of this in one go (run dir, RCON, scenarios, stop); see
`tools/test/README.md`. By hand:

`./gradlew :<node>:prodServer` installs a Fabric server with the built jar (remapped, as players get
it) and Fabric API, with `-Dmobstacker.selftest=true`. The run dir is `versions/<node>/run/prod`
(git-ignored); put `eula.txt` and a `server.properties` with RCON there first, then send
`mobstacker selftest` over RCON. `-PprodJar=<jar> -PprodDir=<name>` runs another jar (e.g. the last
release) in `run/<name>`, so the same test can be run on both.

There is no refmap in the jars any more: Loom writes the remapped names straight into the mixin
annotations (intermediary on Fabric, SRG on Forge). To check a mixin target in a jar, `javap -v` its
class and look for the `method_…` / `m_…_` names.
