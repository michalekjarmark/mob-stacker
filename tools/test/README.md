# Test tools

Shared, tracked tools for checking a build without a player. (Each maintainer's own test rounds and
the old harness stay in the git-ignored `tests/`.) Python 3, no packages needed. `JAVA_HOME` must be a
JDK 25 for anything that runs Gradle.

| Tool | What it does |
|---|---|
| `run_server.py <node> [scenarios...]` | Starts a test server for a node, waits for it, runs scenario files over RCON, stops it. Fabric: a production server (`prodServer -PstrictMixins`); Forge/NeoForge: the dev server (`runServer -PstrictMixins`, run dir `versions/<node>/run`), which on NeoForge 1.20.5+ runs on the same names as production. `--jar <jar> --dir <name>` runs another jar (e.g. the last release) in its own run dir, for the same test on both. Writes `eula.txt` on first use, which accepts the Minecraft EULA for that test server. |
| `rcon.py` | RCON client: `rcon.py <command>...` or `rcon.py -f <scenario>`, against a server that is already running. |
| `scenarios/basic.txt` | Self-test, stacking, damage overflow (expected: x5, then x3). |
| `scenarios/strict_data.txt` | Strict data matching against the version's vanilla keys (zombies with random spawn bonuses stack). |
| `scenarios/shear_dispenser.txt` | A dispenser's shears on a stack of 5 sheep: wool for all five (any world; builds its own pen). 26.3-1 found only the top sheep's wool from 1.21.5. |
| `mcclient.py <name>` | A scripted **player** (offline mode, 1.20.1's protocol 763 only): logs in, speaks MobStacker's config channel and prints every snapshot, chat line and command tree it is sent; `--cmd` types a command as that player (RCON is always an operator), `--edit` sends a GUI edit, `--modded` prints stack labels and names, `--stay`/`--wait` keep it connected. Against Forge no snapshot comes (no FML handshake); it goes on after 3 s. |
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
- Despawning needs a player in range: log `mcclient.py` in (1.20.1 only) and `tp` it 2000 blocks
  away, which despawns everything that may despawn at once.
- **Offline mode and `op <name>`**: a name the server has never seen is looked up at Mojang, so log
  each test player in once (`mcclient.py <name> --stay 0.3`) before `op`, `region owner` or `region
  share` name them.
- `/summon` without NBT runs `finalizeSpawn` (random colours, jockeys, gear on Hard) and records the
  origin COMMAND; with any NBT it does neither (`{Color:0b}`, `{HandItems:[{},{}]}` give plain mobs).
- `/kill` takes a whole stack; `/damage <n> minecraft:generic` is world damage and hurts every member
  (since 1.10.0): for a hit use `damage <target> <n> minecraft:mob_attack by <some mob>`.
- A stack's remainder appears about a second after a kill (the death animation): wait 1.1 s between
  kills. Build a big stack with one summon and `/mobstacker stacksize <target> <n>`: forty summons on
  one spot cram each other first.
- Idle mobs stop wandering after 100 ticks with no player around (vanilla `noActionTime`): to see a
  stack move, give it a target (zombies next to a villager).
- `difficulty` comes back from `server.properties` on every start; snow golems melt in rain and
  undead burn by day (`weather clear`, `time set`).
- Forge/NeoForge have no production server task: `run_server.py` uses `:<node>:runServer` for them
  (dev mappings; on Forge 1.20.1 not the SRG-remapped jar). The dev run logs "access transformer file
  does not exist" on NeoForge: the file is made when the jar is packed, and in dev Loom applies the
  widener itself.
- Two servers at once need different `--rcon-port`/`--server-port` (set when the run dir is created;
  afterwards edit its `server.properties`).

## Scenarios from the 1.20.1 test rounds

Written for 1.20.1 (rounds 9-13) on a **flat** world: run them with `run_server.py --flat --dir <new
dir>` (a normal world puts their mobs inside stone). Their NBT is 1.20.x's (`HandItems`, `carriedBlockState`, ...);
1.20.5 and 1.21.5 changed parts of it. Each sets up its own area on a fresh flat world and prints what
to compare; the comments in the file say what to expect.

| File | What it checks |
|---|---|
| `loader_basics.txt` | Stacking, inspect, a region, stats: the first check of a new loader (Forge round 1). |
| `never_stack.txt` | What never stacks: the unstackable tag, NoAI, love mode, raiders, bosses (1.12.0). |
| `shed_claims.txt` | A stack hands a claim to one member: an enderman's block, a villager's bed and job. |
| `member_copies.txt` | A mob made out of a stack is a copy of it (no armour or leader bonus re-rolled; #35). |
| `picked_gear.txt` | Picked-up gear drops when its mob is merged away (#57). |
| `sweep_fixes.txt` | The 1.12.0 code sweep: creepers, tadpoles, axolotl and frog breeding (the creeper needs `mcclient.py`). |
| `original_options.txt` | multiplyGiftDrops, turtles' scutes, killBatchSize, stackOutgoingDamage. |
| `despawn_setup.txt` | Old spawn caps moved to vanilla; despawnPickedUpMobs in a region (then `mcclient.py`, see Traps). |
| `regions_beyond_boxes.txt` | Whole-dimension regions, on/off, type changes. |
| `region_biomes.txt` | Biome filters on regions. |
| `forge_regions.txt` | The same on a Forge dev server. |
| `harvest_off.txt` | stackedHarvest off: shears take one sheep out. |
| `cure.txt` | A zombie villager being cured stays out of its stack (round 13). |
| `origins.txt` | Spawn origins recorded per member, stackPlacedMobs, unstack. |
| `rotation.txt` | A merge does not turn the stack (Rotation). |

Wiki tools are in `tools/wiki/`: `wiki_check.py wiki/docs` (MDX hazards, `$page#anchor` links) and the
settings page generator (`settings_dump.py` + `wiki_settings.py`, see their first lines).
