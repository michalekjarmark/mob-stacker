# Test tools

Shared, tracked tools for checking a build without a player. (Each maintainer's own test rounds and
the old harness stay in the git-ignored `tests/`.) Python 3, no packages needed. `JAVA_HOME` must be a
JDK 25 for anything that runs Gradle.

| Tool | What it does |
|---|---|
| `run_server.py <node> [scenarios...]` | Starts a production **Fabric** test server for a node (`prodServer -PstrictMixins`), waits for it, runs scenario files over RCON, stops it. `--jar <jar> --dir <name>` runs another jar (e.g. the last release) in its own run dir, for the same test on both. Writes `eula.txt` on first use, which accepts the Minecraft EULA for that test server. |
| `rcon.py` | RCON client: `rcon.py <command>...` or `rcon.py -f <scenario>`, against a server that is already running. |
| `scenarios/basic.txt` | Self-test, stacking, damage overflow (expected: x5, then x3). |
| `scenarios/strict_data.txt` | Strict data matching against the version's vanilla keys (zombies with random spawn bonuses stack). |
| `compare_jars.py <old> <new>` | Class-by-class `javap -c` comparison of two builds, constant-pool order ignored. |
| `mc_jar.py <version>` | Path of Loom's vanilla Mojmap jar for a version, for `javap` and `tools/VanillaKeys.java`. |

Checks that need no server are Gradle tasks (see `versions/README.md`): `checkMixins`,
`compileJava -PallErrors`.

## A port's check, in order

```sh
./gradlew :1.21.1-fabric:checkMixins
python tools/test/run_server.py 1.21.1-fabric tools/test/scenarios/basic.txt tools/test/scenarios/strict_data.txt
python tools/test/run_server.py 1.20.1-fabric tools/test/scenarios/basic.txt tools/test/scenarios/strict_data.txt
```

The two transcripts should say the same (self-test count, x5 then x3, the zombies in one stack).

## Traps

- **No player, no ticking**: entities in the spawn chunks stop ticking 15 s after the last player
  left (or the server started without one). Test mobs go in a `forceload`ed chunk elsewhere, or are
  merged with `/mobstacker stack`.
- Despawning needs a player in range, so it cannot be tested this way (the old harness logs a
  scripted client in for that: `tests/harness/mcclient.py`, 1.20.1 protocol only).
- Forge/NeoForge have no production server task: test those with `:<node>:runServer` (dev mappings,
  not the remapped jar) or in a real instance.
- Two servers at once need different `--rcon-port`/`--server-port` (set when the run dir is created;
  afterwards edit its `server.properties`).
