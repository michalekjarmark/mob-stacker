# Building for several Minecraft versions (Stonecutter)

One source tree (`src/main`) builds a jar for every Minecraft version and loader listed in
`settings.gradle.kts`. Each such pair is a **node**, named `<minecraft>-<loader>` (e.g.
`1.20.1-fabric`), and this folder holds one `gradle.properties` per node: the Minecraft version, the
Java version and the loader's own versions. Everything the same on every node (`mod_version`) is in
the root `gradle.properties`.

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

## Testing a jar on a real server (Fabric)

`./gradlew :<node>:prodServer` installs a Fabric server with the built jar (remapped, as players get
it) and Fabric API, with `-Dmobstacker.selftest=true`. The run dir is `versions/<node>/run/prod`
(git-ignored); put `eula.txt` and a `server.properties` with RCON there first, then send
`mobstacker selftest` over RCON. `-PprodJar=<jar> -PprodDir=<name>` runs another jar (e.g. the last
release) in `run/<name>`, so the same test can be run on both.

There is no refmap in the jars any more: Loom writes the remapped names straight into the mixin
annotations (intermediary on Fabric, SRG on Forge). To check a mixin target in a jar, `javap -v` its
class and look for the `method_…` / `m_…_` names.
