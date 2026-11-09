# Changelog

All notable changes to **MobStacker: Restacked** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
via the `mod_version` in `gradle.properties`. This is an independently-developed fork of
[MobStacker](https://github.com/frikinjay/mob-stacker) by frikinjay, under LGPL v3.

## [Unreleased]
### Added
- **Minecraft 1.21.1 (and 1.21) on Fabric** - in progress, not released yet. The same features as
  on 1.20.1, from the same code. Checked so far on a server: the self-test, stacking and damage
  overflow; not yet the client (screens, region boxes, labels), NeoForge or Forge.
- **Minecraft 1.21 and 1.21.1 on NeoForge** - in progress, not released yet: its own jar
  (NeoForge 21.0 or newer). Shears - a player's and a dispenser's - go through NeoForge's
  `IShearable` there, and harvest every member as on the other loaders. Checked on a dev server: the
  self-test, stacking, damage overflow and strict data matching; not yet the client or shearing in
  game.

### Changed
- **A stack keeps the gear in the body slot too** (from 1.20.5: horse and wolf armour): drop chances
  are read and set through vanilla's own methods instead of its hand and armour arrays.
- **Sweeping Edge on 1.21 reads the attacker's `sweeping_damage_ratio` attribute**, which is what the
  enchantment became there, so a weapon from another mod that sweeps counts as well. 1.20.1 keeps the
  enchantment's level / (level + 1).
- **Internal: one source tree for every Minecraft version.** The build moved from three Gradle
  modules (`common/`, `fabric/`, `forge/`) to [Stonecutter](https://stonecutter.kikugie.dev/): the
  sources are in `src/main`, and every Minecraft version and loader is a node in `versions/` (see
  `versions/README.md`). Gradle 9.9 on JDK 25, Architectury Loom 1.17. The 1.20.1 jars have the same
  classes as 1.12.0's (compared class by class) and pass the self-test; they no longer carry a mixin
  refmap, because Loom now writes the remapped names into the mixins themselves. Nothing changes in
  game.

### Fixed
- **The Fabric jar claimed to run on Minecraft 1.20.2-1.20.6** (`~1.20.1` in `fabric.mod.json`),
  loaded there and crashed. It now asks for 1.20.1 exactly, as the Forge jar already did. Every jar
  takes its Minecraft range, Java version and pack format from its own version's settings.

## [1.12.0] - 2026-11-07
### Added
- **Mobs that should stay single now do, without any setting** (stacks that already exist are left
  as they are; `/mobstacker unstack` splits them):
  - **a datapack tag, `#mobstacker:unstackable`**: the wandering trader (its trades), the allay (what
    it holds), the bee (its hive) and the vex (its evoker). A data pack can add to it or empty it;
  - **an animal in love mode**: two cows a player is breeding no longer merge into the stack next to
    them;
  - **a zombie villager being cured**;
  - **an iron golem a player built**. The ones a village makes, an iron farm's included, still stack;
  - **a raider in an active raid, and a patrol leader**. A raid counts its raiders one by one, so a
    merged one looked dead to it and a wave could end early;
  - **a villager that has claimed a bed, a workstation or a bell**;
  - **an enderman carrying a block**;
  - **a mob with the `NoAI` flag**, which a player or a map set on purpose.
- **A stack hands a claim over to one member, who leaves the stack with it.** A stacked villager that
  claims a bed or a workstation, or a stacked enderman that picks up a block, used to do it for the
  whole stack: twenty villagers shared one bed and one set of trades, and twenty endermen one block.
  Now one member takes it and steps out, and the rest stay stacked, so breeders, trading halls and
  iron farms count their villagers one by one again. A villager caught after it already took the job
  takes the profession, the trades and the experience with it.
- **Bosses do not stack unless `stackBosses` is on** (off by default). A boss is a mob with a boss
  bar of its own, one in the new `#mobstacker:bosses` tag (the ender dragon, the wither, the warden,
  the elder guardian, plus the loaders' own `#c:bosses` / `#forge:bosses`), or one whose base health
  reaches **`bossHealthThreshold`** (150; 0 turns that test off), which catches other mods' bosses
  that have no boss bar. Both settings can differ per region.
- **Mobs with different data from other mods no longer merge** (`strictDataComparison`, on): a level,
  an affix, a champion's rank or anything else another mod saved on a mob used to be thrown away when
  it merged into a mob without it. The mod now compares what other mods saved (vanilla's own data is
  still matched by the mod's own rules) and keeps such mobs apart. `/mobstacker inspect` names the key
  that differs; **`ignoredDataKeys`** lets a key through, for a mod that keeps a timer on every mob
  (`Key`, or `Container.key` for one entry in e.g. `ForgeCaps`). Both per region.
- **Tags in the mob lists**: an entity list takes `#minecraft:raiders`, `#minecraft:skeletons` or any
  mod's or data pack's tag next to single mobs - in the commands (`/mobstacker list deny entity add
  #raiders`), in the GUI (the entry box completes tags too: type `#`) and per region. A tag that is
  not loaded is kept and marked, like a modded mob.
- **Every mob in a stack counts towards the spawn caps** (`countStackMembers`, on, global): a world with
  stacks has as many mobs as vanilla, in fewer entities. Off, a whole stack counts as one mob, as
  before, and far more mobs can exist.
- **`despawnPickedUpMobs` (off)**: a mob that picks up an item can still despawn, and drops what it
  picked up when it goes; an enderman carrying a block drops the block. Vanilla keeps such mobs
  forever. The part of Let Me Despawn that matters with stacks; use one or the other, not both.
- **`persistentStackSize` (0, off)**: a stack of at least this many mobs never despawns on its own.
- **The original mod's remaining options**, so the fork now has all of them that exist on 1.20.1:
  - `multiplyGiftDrops` (on): a stack of chickens lays an egg for every chicken, a stack of baby turtles
    leaves a scute for every turtle when it grows up;
  - `killBatchSize` (1): at least this many mobs die per kill, each with its own loot;
  - `damageWeaponPerHit` (off): when a stack dies as a whole, every hit wears the weapon for every mob;
  - `stackOutgoingDamage` (off) with `maxDamageMultiplier` (16): a stack's melee attack hits as hard
    as all its mobs together;
  - the **assimilator** (`enableAssimilator` off, `assimilatorItem` emerald, `consumeAssimilator` on):
    sneak and right-click a mob with it to switch whether that mob or stack may stack. Sneaking,
    unlike the original, so that trading with emeralds in hand does not switch a villager. When
    something else keeps the mob out anyway - a golem a player built never stacks - it says so.
- **Regions beyond boxes:**
  - **a whole dimension**: `/mobstacker region add <name> <allow|deny> dimension [dimension]`, or
    "whole dimension" in the area editor. No box is drawn; a box inside it wins at the same priority,
    as a smaller region always does;
  - **biome filters**: `/mobstacker region biomes <name> add <biome|#tag>` (and `remove`, `clear`), or
    the editor's "only in biomes" box, which completes biomes and tags as you type and turns yellow
    for one this game does not know. The region then covers only the parts of its box, or of its
    dimension, in those biomes: `#minecraft:is_forest`, `minecraft:desert`, any mod's biome or tag;
  - **an on/off switch**: `/mobstacker region disable <name>` (and `enable`), or the button on the
    region screen. A region that is off keeps everything it carries and does nothing - its box is
    drawn grey;
  - **`/mobstacker region list` is clickable** and comes in pages of ten: the name opens
    `region show`, `[on]`/`[off]` switches the region, the arrows turn the page; what can be clicked
    is underlined. With the mod on your game the list and a region's page are redrawn in place of the
    old ones, so clicking leaves one up-to-date list instead of a pile of them (vanilla cannot change
    a message it has shown, so without the mod the new one comes under the old).
  `/mobstacker inspect` says when a region covers the spot but is off or leaves out its biome.
- **Stack labels are never saved on the mob any more**, and each player sees them the way their game
  can (thanks to our friend for the idea):
  - **with the mod**, the label is a line of its own **above the mob's name**, which stays as it is: a
    stack of sheep called `jeb_` stays `jeb_` - rainbow wool included - with `Sheep x12` above it;
  - **without the mod**, the count is in the name as before, but only in what that player is sent;
  - either way **in the player's own language**, and shown when they look at the stack;
  - new settings: `showStackLabels` (on), `alwaysShowStackLabels` (off), `separateStackLabel` (on)
    and `stackLabelFormat` (`{type} x{count}`; `{type} ({count})` gives `Sheep (12)`). The colours
    (`stackNameColor`, by size) apply to the label as before.
- **A search on the config screen.** Start typing anywhere on it: the settings that match, from
  every page, take the page's place, each with the page it lives on and the letters that matched,
  and can be changed right there. It forgives the way an editor's "go to" does - the starts of the
  words (`kwsod` finds `killWholeStackOnDeath`, `max size` finds `maxStackSize`), words in any order,
  a typo (`seperator`) or a word from what the setting does (`wool`, `whitelist`). Enter, or a click
  on a name, shows the setting on its own page; Esc empties the box; Ctrl+F goes back to it.
- **`/mobstacker help <words>`** asks the same search (`help seperator`, `help wool`), and a command
  that names no setting asks "Did you mean …?", with a click that puts the corrected command back in
  the chat box.
- **API: `MobStackerAPI.getStackSize(mob)` and `isStacked(mob)`**, for other mods.
- **The mod has its own icon** (thanks to our graphic designer), on the mod list of both loaders.
### Changed
- **A stack's name is its own again**: older versions wrote the label into the mob's name
  (`Cow x12`), where it stayed if the mod was removed and where other mods read it as a real name. A
  world from an older version is cleaned as its stacks load - a named stack keeps the name it was
  given, the others lose the label. Another mod's or a command's name is no longer turned into plain
  text in the server's language.
- **The config screen's Stacking page is two**: "Stacking" (where, how big, how fast, what a stack
  does) and "Which mobs stack" (the list mode, gear, names, other mods' data, bosses).
  `/mobstacker help mobs` shows the new one.
- `consumeSeparator` and `separatorItem` are greyed out while `enableSeparator` is off, as the
  assimilator's two are while it is off.
- `/mobstacker inspect` names every rule that keeps a mob out of stacks, not only the first, and says
  "built by a player" for such an iron golem where it said "Origin: not recorded".
- **The spawn caps are vanilla's** (70/10/15/5/5/5/20). They were lowered to 22/5/7/2/2/2/8, even with
  stacking off, which made a fresh install spawn about a third of vanilla's monsters. A config that
  still holds exactly the old numbers is moved to vanilla's the first time it loads (the server log
  says so); caps somebody changed stay. The config page is called "Spawning & despawning" now.
- The Fabric jar no longer suggests installing Let Me Despawn.
- **New configs leave `cobblemon` and `pixelmon` mobs alone** (the deny mod list), as the original
  mod does: a Pokémon's identity lives in its own data, which no stack can keep. An existing config
  keeps its list.
- **A mob that comes out of a stack is a copy of the stack**: what is left after the top mob dies, and
  a mob taken out of one (taming, riding, a bucket, shears, the separator, `/mobstacker unstack`). It
  used to be a new mob that the game set up from scratch and the mod then patched, so whatever the
  patches missed was rolled again. Now it keeps everything the stack has - its variant and age,
  effects, fire, scoreboard tags and team, a turtle's home beach, a zombie that is drowning or a
  zombie villager being cured, attributes set with `/attribute`, and other mods' data. What belongs
  to the top mob alone stays with it: what it picked up, a saddle, a chest or a carried block, its bed
  and job, love, a turtle's egg, a creeper's lit fuse, a zombie's leader bonus. Each member's own
  gear, horse stats, origin and wounds are handed over as before.
### Fixed
- **What was left of a stack after a kill could come back with things no member had**: on Hard a
  stack of bare zombies now and then came back in armour or holding a sword - and then took no more
  zombies in, being equipped - or as a leader zombie with up to five times the health. A turtle taken
  out of a stack got a new home where it stood.
- The wound that damage overflow leaves on the next member is no longer lost when the top mob had
  more max health than the rest.
- **A saddle on a stack of pigs or striders saddled the whole stack**: the player rode all of them, the
  stack never took another mob in, and only one saddle ever came back. One animal steps out for the
  saddle now, as a horse does.
- **With `stackEquippedMobs` on and `keepMemberEquipment` off, what a mob had picked up vanished when
  it was merged into a stack.** It is dropped on the ground now, as was always intended (the check
  looked for a marker nothing writes). What the mob spawned with still goes with it.
- **One dye coloured a whole stack of sheep, and one golden apple cured a whole stack of zombie
  villagers** (each giving you the cure's discount). Vanilla takes one per mob, so one steps out of the
  stack for it now, as for a saddle - and a zombie villager being cured stays out of stacks until it
  is a villager.
- **Completion lists in the config screens stayed open** once something had been typed, even after
  clicking elsewhere, and lay over what was under them. They close when the box loses focus or a
  click lands anywhere else.
- Long lines ran off the screens: the one under a region's settings (its dimension and corners) and
  the answer to an edit, which names every biome a region is limited to. A line that does not fit is
  cut now, and hovering it shows all of it - the region's line with every biome it is limited to.
- **A stack of creepers that blew up next to a player was gone entirely**: only one creeper exploded,
  but the rest went with it. The others stay now and come at you one by one. And a creeper lit with
  flint and steel no longer leaves a second stack behind when it is unloaded or goes through a portal
  before it explodes.
- **A stack of tadpoles grew up into a single frog.** It grows up into a stack of frogs.
- **Breeding a stack of axolotls ate the bucket** along with the tropical fish; the water bucket comes
  back, as in vanilla. **A stack of frogs ate the slime balls and laid nothing**: frogs breed the vanilla
  way now (they lay frogspawn, which a stack could not).
- A rare "failed to save chunk" error: a stack's own data was handed to the save while the server went
  on changing it.
- **A stack of endermen carrying a block duplicated the block**: every mob the stack gave back (the
  rest of a stack after a kill, one taken out) carried it again, so each kill dropped one more. And two
  endermen carrying the same block merged and one block was gone.

## [1.11.0] - 2026-11-04
### Added
- **A Forge version for Minecraft 1.20.1** (Forge 47 and up; it needs nothing else). Same features
  and the same per-world config file as the Fabric jar, and a client without the mod can still join a
  Forge server with it, as on Fabric. Shears in a
  player's hand go through Forge's own shearing (`IForgeShearable.onSheared`), so a stack is sheared
  the same way as on Fabric. Not ported: the `mobstacker.regions.viewall` permission node (on Forge
  it falls back to operator level 2).
- **`alwaysShowFeedback` (off by default)**: the mod's command replies reach the player who typed the
  command even while the gamerule `sendCommandFeedback` is false. Servers turn the gamerule off
  because of command blocks spamming the chat, which also left `/mobstacker` with no answer at all.
  Command blocks and functions stay quiet, also when they run the command as a player. Errors were
  never hidden. Global only, in the "Feedback" category (the old "Kill feedback").
### Changed
- **Right-click steps a cycling button backwards** on every MobStacker screen (colours, modes, on/off,
  a region's type and dimension, the box style), as the region colour button already did. Colour
  settings (`stackNameColor` and the others) show their value in that colour, like the region colour.
- **`/mobstacker region set <region> <setting> <value>` with what the server already says makes the
  region follow the server again**, as picking that value in the region screen always did. For
  `privateRegions` this mattered: it is the one setting a region keeps even while it agrees with the
  server, so `region set farm privateRegions true` on a private server pinned the region private, and
  `/mobstacker set privateRegions false` later left it hidden. Switching `privateRegions` now also
  names the regions that keep a value of their own and therefore did not follow.
- Internal: the screens, the region boxes, the picker, the HUD line and both halves of the networking
  moved into the shared code, so both loaders run the same code for them.
### Fixed
- **On a server, region boxes and the `Region:` line stayed empty until the config screen had been
  opened once** after joining: the client asked for the region list only when the screen opened. It
  asks as soon as it knows the server has the mod.

## [1.10.1] - 2026-10-31
### Fixed
- **A region box under water could not be seen from above the surface under "Fast" and "Fancy"
  graphics** - only once the player was under water too - while "Fabulous" drew it. Water, stained
  glass and clouds write depth there into the buffer the boxes were tested against. The boxes and
  their names are now tested against the solid world's depth, kept just before the translucent
  terrain is drawn (a new client mixin on `LevelRenderer.renderLevel`, after Iris has drawn the mobs
  it holds back), so terrain and mobs hide a box and nothing see-through does, in every graphics
  mode. The frame's own depth is put back once the boxes are drawn.
- **With X-ray on, a region's name was grey under "Fast" and "Fancy"** wherever water or a cloud was
  in front of it - only its faint see-through copy got through. Under "Fabulous" it was the other way
  round: the name was drawn at full strength even through a mountain. It is now faint behind terrain
  and full strength in view, in every mode.

## [1.10.0] - 2026-10-31
### Added
- **`/mobstacker inspect [target]`** - look at a mob (or name one) and see its stack up close: size,
  health (the top mob's and that of the mobs under it), which region it stands in and what that region
  sets, the ceiling in force and where it comes from, and what the stack remembers (members' gear,
  horse stats, breeding, a player's name). Then the answer to the question every "stacking is broken"
  report comes down to: **whether it can stack, and if not, the first rule that says no** - stackMode,
  a DENY region, a list, a name, gear, a player's claim, its size - and what happens between it and the
  nearest mob of its kind: they merge (and which one survives), or why not, down to "they differ in
  wool colour". The rules exist once; the scan and the command read the same answer.
- **`/mobstacker stats [radius]`** - how many stacks stand in the loaded chunks (or within a radius),
  how many mobs they stand for, how many entities that saves, and the ten busiest kinds.
- **`environmentDamagePerMob` (on by default): damage from the world hurts every mob in a stack.**
  A fall, fire, lava, drowning, suffocation, cramming, magma, a cactus, freezing, the void, poison,
  an explosion or a falling anvil hurts each member as it would a crowd standing loose, the ones it
  kills drop their own loot, and the survivors keep the wound. Until now a 30-block drop that kills
  every zombie killed **one of a stack of ten** and left nine standing at the bottom of the farm, so
  stacks broke fall-damage and "one hit" farms unless whole stacks died together - which also made a
  sword kill whole stacks. A player's or a mob's hit is still one hit, with the leftover carried down
  by `damageOverflow` as before. Greyed out while `killWholeStackOnDeath` is on, which already does
  this. Per region, like every setting.
- **`stackAi`: what a stack does with its AI** (the user's own idea). `NORMAL` (the default) changes
  nothing. `PHYSICS_ONLY` stops a stack thinking - it attacks nothing, follows nobody and stands
  where it is - while the world still acts on it: it falls, water carries it, a hit knocks it back,
  and it stays afloat if its kind would have swum, so farms built on water and drops keep working.
  `FROZEN` is vanilla's NoAI: it does not move at all, not even to fall, which saves the server the
  most. Only stacks of two or more are affected, so a stack killed down to its last mob wakes up again,
  and nothing is written into the mob: remove the mod and every mob thinks again. Worth knowing before
  choosing: a sheep that does not think does not eat grass, so its wool does not grow back, and
  villagers do not work or breed. Per region, like every setting.
- **Stacking by spawn origin**: a new *Spawn origin* page with four switches, all on by default -
  `stackNaturalMobs` (spawned on their own), `stackSpawnerMobs` (monster spawners), `stackBredMobs`
  (born from breeding, vanilla's or a stack's) and `stackPlacedMobs` (spawn eggs, dispensers, buckets,
  `/summon`). So a server can stack the spawner farm and leave the wildlife alone, or the other way
  round, per region too. Each mob remembers how it came into the world from 1.10.0 on; a mob from
  before that, or one hatched from an egg, counts as natural. What is left of a stack after a kill, a
  mob taken out of one and a mob that converts all keep the origin they came from. `inspect` shows it.
- **`/mobstacker unstack <radius|loaded>`** splits stacks back into the mobs they stand for - **run it
  before removing the mod**, because without the mod a stack is one mob and every other mob it stood
  for is gone. The mobs come back a hundred a tick and are set down apart from each other where there
  is room, never on the far side of a wall or fence, since sixty-four let out on one spot would be hurt
  by vanilla's crowding limit; a stack with no room around it is left partly stacked and reported. More
  than 500 mobs asks for `confirm`. It warns while stacking is still on (they would merge again) and
  reaches loaded chunks only.
- **`/mobstacker stack <radius>`** merges what can merge there now, whatever `stackMode` and the regions
  say - every rule about the mobs themselves still holds (their kind, the ceiling, names, gear,
  variants, the stack radius). For tidying up a crowd where stacking is otherwise off.
- **Region names in the world, and a "Region: ..." line.** With the region boxes on, each box has its
  name written on it the way a mob's name tag is, in the region's colour, on the side nearest you and
  growing with distance so it stays readable; with a few regions on screen it was hard to tell which
  box was which. Inside a region, a line at the top left of the screen names the one whose settings
  apply where you stand (and how many more overlap there). Both go away with the boxes, F1 and F3.
  Names follow the boxes through walls only where the server allows it.
- **Private regions (`privateRegions`, on by default).** Every modded client that opened the GUI was
  sent every region on the server, corners included - a map of every farm and base, and an invitation
  to grief them. Now the server sends a player only the regions they may see, so nothing else can be
  drawn, read off the wire or dug out of a modified client: **admins** see every region (operators,
  the player whose singleplayer world it is, and anyone a permissions mod such as LuckPerms gives
  `mobstacker.regions.viewall`), a region's **owner** sees it, and so does everybody it was **shared
  with**. `/mobstacker region owner <name> <player|none>` (operators) gives a region an owner, and the
  owner may then use `/mobstacker region share <name> add|remove <player>` without being an operator -
  the one command a player who is not an operator gets, and only for their own regions. Switch it off
  for a server where everybody may see everything, or set it to `false` on one region to make just that
  one public (a spawn, a market); a region's own value is kept even while it matches the server's, so a
  private region stays private when the server is switched to public and back. A player's snapshot is
  sent again by itself when anything about it changes - a share, a new owner, an op, a permission.

### Fixed
- **Mobs of different origins merged into one stack lost theirs** (round 8): the stack kept the origin
  of the mob on top, and every mob taken out of it - or brought back by `unstack` - came out with that
  one. A stack now counts its members' origins, `inspect` lists them, a stack holding a mob whose origin
  is switched off takes no more in, and taking mobs out hands out the odd ones first.
- **A stack turned round every time a mob joined it** (round 8, seen with `stackAi FROZEN`): the merge
  copied the joining mob's facing onto the stack along with the rest of its data. The stack now keeps
  its own, and what is left of a stack after a kill keeps the head's direction too, where it used to
  start out looking a random way - which a stack without AI then kept, its body slowly turning after it.
- **An operator's change in the GUI reached only that operator's screen**; everybody else's boxes and
  config screen kept the old values until they opened the GUI again. Every change now reaches every
  player who has the config open or its boxes showing.
- **Now and then a stack came back from a kill carrying a rider nobody could see.** Every mob the
  mod creates - what is left of a stack after a kill, a mob taken out of one - goes through vanilla's
  spawn setup, and that setup sometimes adds a rider: a skeleton on one spider in a hundred, a
  zombified piglin (with a saddle) or a baby strider on a strider, a chicken under a baby zombie. The
  mod added only the mob, so the rider travelled along out of sight, the stack became a mount that no
  other mob would join, and the next time the chunk was loaded the rider appeared for real - a
  skeleton from nowhere. Found by the first scripted server test (3 in 250 spider kills; none in 250
  after the fix, nor in 98 strider kills). A random effect a spider may roll on Hard is dropped the
  same way.
- **Shearing a stack of mooshrooms left a stack of cows named "Mooshroom x10"** that no other cow
  would join. Vanilla copies a sheared mooshroom's name onto the cow it becomes, and that name was
  the stack's own label; the cow stack read it as a name a player had given. The cow stack is now
  labelled "Cow x10" and takes cows like any other. Found by the first scripted server test.
- **A mob taken out of a stack came out healed**, even when every mob under the top one carried a
  wound (a sweep, a fall, a fire); it now comes out with that wound. And under `stackHealth`, taking
  mobs out lowered the stack's health but never its maximum, so a stack split back down to one mob kept
  the whole pool as its maximum - a single cow with 640 max health.
- **A stack a player named keeps its name when lightning turns it into something else** (a pig into a
  zombified piglin, a villager into a witch), as it already did when a zombie drowned. Every
  conversion now goes through one path, which also keeps a name given before 1.7.0 began
  recording them.

## [1.9.2] - 2026-09-23
### Added
- **`-Dmobstacker.overlayDebug=true`**, a Java argument that has the region boxes write to the log,
  every few seconds, what they find when they are drawn: which graphics mode, which buffer, what state
  the rest of the game left behind. For a modpack in which the boxes do not show; silent without it.

### Changed
- **A stack of snow golems gives a carved pumpkin for every golem** when it is sheared with
  `stackedHarvest` on — a player's shears or a dispenser's — and the shears wear down once per golem,
  as they do for sheep. It gave one pumpkin for the whole stack, while the whole stack lost its
  pumpkins. With `stackedHarvest` off, one golem is still taken out and sheared on its own.
- **The ceilings tab's size box takes the number it shows.** Its grey hint is now the `maxStackSize`
  in force there (the region's own, or the global one) rather than a fixed 16, and **Set** with the box
  left empty uses it — it used to refuse, as if there were no number at all.
- **Enter on an id typed without its namespace** (`cow`) writes the whole id into the box
  (`minecraft:cow`) before it is used, so the box shows what is stored. There was nothing to complete —
  `cow` already is an id — and a list that did not open read as Enter not working.

### Fixed
- **With "Fabulous" graphics, water and clouds showed through a region box standing in front of them**
  *(1.9.0)*. Fabulous puts the frame together from separate buffers by depth, and a box — which writes
  no depth, so it can never hide another box — sat at the depth of whatever was behind it. Every box is
  now drawn at the very end, into the finished frame, tested against the depth of the solid world
  (kept aside for it before Fabulous wipes it). Clouds, water, leaves and mobs are now in front of a
  box or behind it as they really are; the one thing that does not tint a box any more is water it is
  under, which it is now drawn over. Fast and Fancy draw the boxes at the end too.
- **With Iris installed — even with no shader pack — region boxes were drawn at the wrong moment**
  *(1.8.0)*: under water and clouds, and with "Fabulous" graphics hardly at all (only the faces, and
  only with clouds on). The boxes were drawn through the game's shared buffer, and while the world is
  being drawn Iris hands out a buffered one of its own instead, which draws what it is given when Iris
  decides to. The boxes now have a buffer of their own and are drawn on the spot.
- **Removing a per-type ceiling in the mob list screen crashed the game** *(1.9.0)*. In singleplayer
  the screen reads the config while the game's own server changes it, and a list or map being read
  while the other side changed it threw an error. Every list and map in the config — the mob lists,
  the ceilings, the regions and each region's own settings, lists and ceilings — is now replaced
  whole when it changes rather than changed in place, so whatever a screen (or the region boxes,
  drawn every frame) is reading stays as it was until it is done with it.
- **A dispenser with shears treats a stack the way a player does** *(stackedHarvest, 1.8.0)*. With
  `stackedHarvest` on it sheared one sheep's worth of wool off a whole stack; it now shears every
  member (and a stack of mooshrooms gives five mushrooms for each), with one snip, and the shears wear
  down once per animal as a player's do. With `stackedHarvest` off it sheared the whole stack for one
  sheep's wool; it now takes one animal out of the stack and shears only that one, as a player's
  shears do.

## [1.9.1] - not released on its own
Everything below shipped as part of **1.9.2**; 1.9.1 was the development line it was built on.

### Added
- **Region boxes through walls — where the server allows it.** An **X-ray** button on the region
  screen (and a key binding, unbound by default) draws every box through walls and terrain, so a
  region can be seen from anywhere around it. It changes only what hides a box, not how the box
  looks: the same faces, the same edges, the same colours and style. The box you are drawing with
  **Pick in world…** follows it too, which is when it helps most.
  **A box seen through a mountain is a small x-ray of the world, so it is the server's to allow:**
  start the server with the Java argument `-Dmobstacker.xray=true` (in singleplayer, and for whoever
  hosts a LAN game, the game itself is the server, so the argument goes in the launcher). Without it
  the button is greyed out and says how to allow it, and the key binding says it is not allowed here.
  The server says yes or no in the config snapshot; a client's own flag counts for nothing on
  somebody else's server, and a server older than 1.9.2 says no.
  Where it is allowed, whether it is on is your own view, one choice for every world, kept in
  `config/mobstacker-overlay.json`; off by default. Built from render types of the mod's own that
  switch the depth test off themselves — vanilla's "no depth test" only assumes the test is already
  off, and right after the particles are drawn it is not. Boxes seen through walls are drawn after
  everything else in the world, so clouds, water, leaves and mobs do not cover them either; only your
  hand and the HUD are drawn over them.
- **`separatorItem` completes item ids as you type**, on the config screen and on the region screen,
  exactly the way the mob list editor completes entity ids: Tab, Enter or a click takes the
  highlighted one, the arrows walk the list, Esc closes it. The three boxes share one piece of code
  now, so they cannot drift apart.

### Fixed
- **Every horse in a stack keeps its own speed, jump and health** *(1.9.0 stopped the re-roll; this
  finishes it)*. A stack of horses had one set of numbers — the top horse's — and every horse that
  came out of it got those. So a good horse merged into a herd of poor ones came back out poor, and,
  the other way round, a herd merged under one good horse came out one good horse at a time. The
  stack now remembers each member's numbers, the way it remembers each member's equipment: the horse
  that comes out is the next one in line with its own, and when the top horse dies the next one
  takes over with its own. Donkeys, mules, llamas and the rest of the horse family too. A stack that
  formed before this carries no numbers for its members, and hands out the top horse's, as it did.
- **On a server, typing `max` or `default` into a config box showed the word** until the server
  answered *(1.9.0)*. It shows the number the word stands for straight away, as the server stores it.

## [1.9.0] - 2026-09-23
### Added
- **Whitelists.** Until now the only mob filter was a blacklist — "everything stacks except these".
  The other half now exists: set `mobListMode` to `WHITELIST` and *nothing* stacks except what is on
  the allow lists. The two halves keep separate lists, so switching the mode to have a look does not
  invert the meaning of a list you spent an evening building; switch it back and your list is intact.
- **Mob lists per region.** A region can carry its own "these never stack here" or "only these stack
  here", instead of every region sharing one global pair. A region that sets nothing inherits the
  global list, exactly like every other per-region setting; a region that sets one means that list
  and no other. Entity lists and mod lists inherit independently.
- **A list editor in the GUI**, reached from **Mob lists…** on the config screen and on each region.
  One screen covers all eight lists and the ceilings below, with tabs, add and remove, and a note
  when the list you are looking at is not the one `mobListMode` is currently reading — with the
  command that would change that, so the answer is not "go and find the setting". The entry box
  **completes ids as you type**, the way the command line does: Tab, Enter or a click takes the
  highlighted one, the arrows walk the list, Esc closes it. (Enter leaves an id that is already whole
  alone — `minecraft:pig` stays a pig, whatever is listed under it.) Handing a region's list back to the global one asks
  once before it discards what the region had.
- **A stack ceiling per mob type** — `/mobstacker maxstack minecraft:cow 64`, globally or inside one
  region. Anything not named follows `maxStackSize` as before. Looked up by entity id, so modded
  mobs work without the mod knowing they exist.
- **Mobs stack the moment they appear** (`stackOnSpawn`, default on, per region). A spawner batch, a
  bred baby or a handful of spawn eggs used on one block no longer wait out a scan interval standing
  around as separate entities.
- **Draw a region by clicking two blocks.** The area editor has a **Pick in world…** button: the
  screen steps aside and the next two blocks you right-click become the corners, with the box drawn
  live from the first corner to whatever you are looking at, so you can see the reach before you
  commit to it. Sneak and click to cancel. Nothing is saved until you press Save — the picker fills
  in the same six numbers you could have typed, and every rule about it is still the server's.
- **`separationCooldown`** (seconds, default `0`, per region): how long a mob taken out of a stack
  for you — by taming, riding, a bucket, shears or the separator — or poured out of a bucket stays
  out of the stacks before it may merge again. It used to be a fixed fifteen seconds; the last test
  round found the wait was all anyone noticed of it, so it is off unless you want it. A horse you
  have started taming stays out for good regardless (see Fixed).

### Changed
- `/mobstacker ignore <entity|mod> …` is now `/mobstacker list deny <entity|mod> …`, with
  `list allow` for the whitelist half and `region mobs <name> …` for a region's own. **The old
  `ignore` spelling still works** and reaches exactly the same code.
- The settings registry is now **46 settings, 37 of them overridable per region**.
- **The button that leaves a screen is always the last one in its row.** The config screen had Done
  on the left and the region screen on the right; both have it on the right now, with **Mob lists…**
  beside it on both, and the area editor's Cancel moved to the end of its row for the same reason.
- **A list will not take a vanilla mob that does not exist.** `minecraft:cwo` is a typo and nothing
  will ever arrive to make it mean something, so it is refused outright. A *modded* id is still
  accepted whether the mod is installed or not — a list has to survive its mod being away for a
  week — but it is marked `(not loaded)` in the editor and says so in chat, so an entry that means
  nothing never looks like one that is working. The per-type stack ceilings are judged the same way.
- **The box colour button on the region screen is labelled**, and says what "auto" means on hover.
  **Right-click steps it backwards**, so going back one colour is not sixteen clicks forward.
- **A region remembers its two corners the way you gave them.** Picking two blocks and reopening the
  editor used to show two *other* blocks — the same box, sorted into a minimum and a maximum corner,
  but with coordinates matching neither block you clicked. Corner 1 is now the first block and
  corner 2 the second, in the editor, in `region list` and in `region show`. The config file keeps
  the minimum and maximum it always had, so what is inside a region is decided exactly as before and
  a world taken back to an older version still has every region where it was; a region saved before
  this simply shows its corners sorted, as it always did, until it is next redrawn.
- **With `stackedHarvest` off, shears take one animal out of the stack and shear that one.** "Off"
  was meant to make a stack give what a single mob would, but shearing changes the mob and the stack
  is one mob — so one click left all sixteen sheep sheared for one sheep's wool, and a stack of
  mooshrooms turned into a stack of cows. Now each click shears one sheep, the rest keep their wool,
  and a sheared sheep never merges back into an unsheared stack. With the setting on, nothing changed:
  one click still shears the whole stack for the whole stack's wool.
- **`maxStackSize` accepts anything from 1 to 2147483647 again.** The mod this one is forked from
  took any whole number from 1 upwards; this fork had quietly capped it at 100000, which was its own
  invention and had nothing behind it. The per-type ceilings go as high, and so do the three settings
  that are shaped by a stack size — `stackSizeMediumThreshold`, `stackSizeLargeThreshold` and
  `sweepingEdgeMaxKills` — which were capped at the same number and would have left a name unable to
  change colour on a stack the new ceiling allows. The one sum that could have overflowed past a
  ceiling that large — "would these two stacks together fit?" — is done in long, so a count can never
  wrap round to a negative.
- **`max` can be typed wherever a whole number or a decimal is asked for**, and means as high as that
  setting goes: `/mobstacker set maxStackSize max` rather than remembering 2147483647. It works for
  every numeric setting, for the per-type ceilings (`/mobstacker maxstack minecraft:cow max`,
  offered by tab-completion beside `default`) and in the GUI's own boxes. It is **stored as the
  number it means**, so the config file stays plain and `get` never answers with a word you would
  then have to look up.
- **`default` can be typed the same way**, for every setting: `/mobstacker set maxStackSize default`
  is `reset maxStackSize` under the word `maxstack <entity> default` already used, and tab-completion
  offers it. It works in `region set`, in the GUI's boxes, and in the ceilings tab's size box, where
  — as in the command — it removes that mob's ceiling. The size box there also takes all ten digits a
  ceiling can have now; it stopped at six.

### Fixed

> **Where these come from.** 1.7.0 and 1.8.0 both shipped without a test pass, so most of what is
> fixed below is not a 1.9.0 regression — it is the first time developers had tested those two versions
> and written down what happened. The version each bug arrived in is named, because "fixed in 1.9.0"
> on its own would suggest 1.9.0 broke it.

- **Taking one animal out of a stack no longer re-rolls it** *(older than this fork)*. A horse's
  speed, jump and health are rolled when the entity is created, and every horse that came out of a
  stack got a fresh roll of all three — so stacking and unstacking a horse was a re-roll button you
  could keep pressing until the numbers came out well. Its markings were being re-rolled with them
  *(1.7.0: it taught horses to keep their colour, but colour is only the low byte of the variant)*.
  Both now come out of the stack exactly as they went in, at full health.
- **Taming an animal out of a stack works** *(1.7.0 for horses, 1.8.0 for wolves, cats and parrots;
  neither version was tested)*. The animal handed over rejoined the herd within a second, so the
  next click peeled off a fresh one with its progress lost, and a horse was never tamed. A horse with
  any taming progress at all — vanilla calls it temper — now stays out of stacks for good, the way a
  tamed one does. A wolf, cat or parrot has no progress to lose (every bone or fish is a fresh roll),
  so one that shrugs off a bone may rejoin the pack; `separationCooldown` keeps it out for as long as
  you like.
- **Bucketing a stacked fish takes one fish** *(as old as fish stacking at all)*. It used to take
  the whole shoal: everything but the name went into the bucket, and one fish came back out of it.
- **…and the shoal it came out of stays visible** *(new in 1.9.0's fix for the above)*. The game
  removes a bucketed mob on your screen the moment you click, before the server has answered; the
  server then kept the rest of the stack, and nothing ever told your client it was still there. The
  stack went on existing out of sight — it still merged, still swallowed fish poured back next to it,
  and came back into view only when something re-sent it. It is re-sent the moment the bucket is
  filled now. The same went for axolotls and tadpoles.
- **A fish poured out of a bucket is seen before it joins the shoal** instead of vanishing into the
  shoal beside it on its very first tick, which looked exactly like the bucket having eaten it. It
  joins on the next scan, or once `separationCooldown` runs out if that is set.
- **The mob list editor could crash the game** *(new in 1.9.0)*. Removing entries quickly left the
  screen rebuilding from a list the server thread had already shortened, which came out as an
  `IndexOutOfBoundsException` on the next click.
- **The id completion in that editor is drawn in front of the screen** *(new in 1.9.0)* instead of
  behind the screen's own text, and a message about the last edit now has a line of its own rather
  than landing on a row.
- **"New region…" will not overwrite an existing region** *(1.7.0, when the area editor learned to
  create one)*. Typing a name that was already taken moved that region onto the box around you,
  silently, with everything it carried. It is refused now and points at the region's own editor,
  exactly as `/mobstacker region add` always has. Creating an allow region while stacking is off
  also *says* that it switched stacking on — in singleplayer as well as over the network, where the
  message was being written and then thrown away.
- **The X/Y/Z header in the area editor no longer sits on the dimension button** *(1.7.0)*, and is
  drawn in the same grey as the labels beside it rather than one that disappeared into the background.
- **A region's wireframe is visible through another region's fill** *(1.8.0)*, and through its own
  near face. The faces are drawn with a render type of the mod's own that writes no depth, so nothing
  the overlay draws can hide anything else it draws. Drawing the edges first had been tried and was
  not enough: with *Fabulous* graphics the edges go into a buffer of their own, and a face that had
  written its depth still covered them when the frame was put together — which is also why stepping
  inside a box made everything reappear. Drawing boxes through *walls* is a separate job and still
  to come.
- **Shearing a stack makes one snip, not one per sheep** *(1.8.0)*. With `stackedHarvest` on, every
  sheep under the top one was sheared with its own sound, all on the same tick — a stack of sixty-four
  was sixty-four snips at once. The wool is unchanged.
- **The mob list editor keeps the cursor in the entry box after Add** *(new in 1.9.0)*, so a run of
  ids can be typed one after another. It used to be taken away twice: by the Add button taking focus
  once it had been clicked, and by the screen repainting when the edit came back from the server.
  That repaint now also keeps whatever you had started typing, so a new row is shown straight away
  instead of waiting until the box lost focus.
- **`keepMemberEquipment` no longer reads `ON` while greyed out** *(the greying is 1.6.0; 1.7.0's
  setting is the first one it got wrong, being the first whose default is on)*. A setting that is
  doing nothing reads as off, whichever way its own default points — and can always be switched
  off, which for a setting whose default was on was being refused. It also **cannot be switched on**
  while `stackEquippedMobs` is off, in either of the two ways that slipped through: asking for its
  default, which counted as "switching it off", or asking for `true` while `true` was what sat in the
  file, which answered "already true" to a switch that reads OFF. That second one was what
  `/mobstacker selftest` kept reporting. `toggle` flips what a switch reads as, and `reset` may always
  put the default back — that is a write to the file, not a request for the setting to do anything.
- **`/mobstacker set killWholeStackOnDeath false` says it is forced on** *(1.6.0)* while
  `stackHealth` holds it there, instead of answering "it is already false" because `false` is what
  sits in the file. This was the one failure `/mobstacker selftest` had been reporting.
- **A stack no longer jumps sideways when it swallows a passing mob** *(as old as this fork)*.
  Whichever of the two mobs moved or scanned first was the one that survived the merge, and the
  survivor keeps its own position — so roughly one merge in twenty was a stack of thirty cows
  teleporting a block or two to stand where a single cow had been. The **bigger stack always
  survives** now, whoever set the merge off. Two side effects worth knowing: a mob you named is no
  longer merged away into a bigger unnamed stack, because its name would vanish, and the per-type
  ceiling that decides whether a merge fits is now the one where the **survivor** stands rather than
  the one where the mob that moved happened to be.
- **Which region boxes you are looking at is remembered per world** *(1.8.0)*. It was one list for
  the whole game, so "show all" switched on in a test world came along to the next world and onto
  the server, drawing boxes nobody had asked for — and a region called `farm` in one world decided
  what `farm` looked like in another. A singleplayer world is known by its **save folder**, not the
  name on the world list, so two worlds both called "New World" are two worlds. The style (wireframe,
  filled, both) is still one choice for everywhere, because that one really is a preference rather
  than a fact about a world. Existing `config/mobstacker-overlay.json` files are read as before; the
  old blanket switch is dropped once, which costs one click to set again.
- The self-test now covers everything above that can be checked without a world: settings held
  inert by another (globally and inside a region), what a list will accept, creating a region versus
  redrawing one, and a region keeping its corners as given.

## [1.8.0] - 2026-09-19
### Added
- **Regions can be drawn in the world.** Switch a region's box on and its bounds appear as a coloured
  cuboid, so "where does this region actually reach" stops being a question you answer by walking to
  a corner and reading coordinates off the debug screen. Each region carries a **colour of its own** —
  `/mobstacker region color <name> <colour|auto>`, or the button beside its priority in the region
  screen — so a sheep pen and a cow pen are told apart at a glance. A region nobody has coloured is
  drawn green if it allows stacking and red if it denies it.
  Which boxes are drawn, and in which of three styles (**wireframe**, **filled**, **both**), is each
  player's own view: it is kept client-side, never sent anywhere, and available even to a player who
  may not edit anything. There is a key binding (unbound by default) for showing and hiding them all.
  Needs the mod on the client; without it there is simply nothing to see.
- **Milking a stack fills as many buckets as you brought**, up to one per mob (`stackedHarvest`,
  default on). Cows, mooshrooms and goats. Vanilla still milks the top mob out of the bucket in your
  hand, so the sound and creative mode behave exactly as before.
- **Taming a pack takes one animal out of it.** Offer a bone to a stack of wolves, cod to cats or
  seeds to parrots and exactly one animal steps out to be tamed, instead of all sixteen at once —
  the same thing 1.7.0 did for horses. Only when you are actually holding the right item, so walking
  past a pack and right-clicking it with an empty hand still does nothing at all.
- **A region can be renamed** — `/mobstacker region rename <name> <newname>`, or just type over the
  name in the area editor. It keeps the region's area, its settings, its priority and its colour;
  deleting and re-adding it, which was the only way before, threw all of that away.

### Fixed
- **A pack of wild wolves could be bred.** Vanilla lets an untamed wolf fall in love and then refuses
  to let it mate; the stack breeding code counted fed members and spawned babies without ever asking,
  so feeding meat to a stack of wild wolves produced puppies vanilla would never have given.
- **A trusting ocelot or a fox that knows you is left out of stacks**, the way tamed animals already
  were. Neither is tamed and neither has an owner, so nothing the mod checked could see the fish
  spent earning that trust — and a wild one walking into the stack handed its indifference to all of
  them.
- **`sweepingEdgeSingleHit` no longer sits on `ON` doing nothing.** With `killWholeStackOnDeath` on
  (or `stackHealth`, which forces it) there are no separate members left to wound, so the sweep goes
  into one hit whatever the setting says. It is now greyed out with the reason, like every other
  setting that cannot currently do anything.
- **`/mobstacker selftest` puts your settings back exactly as they were**, instead of resetting the
  ones it had to change to their defaults.
- **Shearing gave one mob too much.** A stack was sheared its full size *on top of* the shearing
  vanilla had already done — and an unstacked mob counts as a stack of one — so **every lone sheep
  dropped double wool** and cost two points of shear durability, a stack of 16 gave 17 sheep's worth,
  and a lone mooshroom dropped 10 mushrooms instead of 5. Inherited from upstream.

### Changed
- New setting **`stackedHarvest`** (default `true`, overridable per region) covers shearing and
  milking together. Off makes a stack give what a single mob would.

## [1.7.0] - 2026-09-19
### Added
- **Every stacked mob keeps its own equipment** (`keepMemberEquipment`, default `true`, needs
  `stackEquippedMobs`). A stack now stores what each of its members wears and holds — item by item,
  with the drop chance of each piece — instead of keeping only the survivor's gear and losing the
  rest on the merge. When a member is killed, its own gear is put back on the entity just before that
  member's death loot is rolled, so **Looting, the drop chances and the damage vanilla rolls onto
  dropped armor all apply by themselves**, under every death mode (`killWholeStackOnDeath`, damage
  overflow, or a plain one-at-a-time kill). Because the gear is stored exactly as the game stores it,
  **other mods' items and any enchantment work without the mod knowing anything about them**. The
  remainder of a killed stack, and a mob pulled out with the separator item, take the next member's
  gear with them.
- **A region's area can be changed** — `/mobstacker region bounds <name> <corner1> <corner2>` moves or
  resizes a region **keeping its settings, its priority and its name**. Until now the only way to
  redraw a region was to delete it and add it again, which threw all of that away.
  `/mobstacker region type <name> <allow|deny>` likewise flips a region's kind without redrawing it.
- **Regions can be created and edited from the GUI.** The region screen gained **New region…** and
  **Edit area…** buttons, which open an editor for the region's name, kind, dimension and both
  corners, with a **Here** button per corner that fills it from where you are standing. A new region
  starts as a box around the player. The same screen deletes a region, behind a second click. Works
  in singleplayer and on a server, operator-gated exactly like every other GUI edit.
- **Name tags work on stacks** (`stackNamedMobs`, default `false`). Renaming a stack with a name tag
  now labels the stack — `Bella x16` — and **the stack goes on accepting mobs**, instead of silently
  refusing every new one. What a name means is recorded when the tag is used rather than guessed from
  the text afterwards, so a name tag on a *single* mob still keeps it out of stacks (that is how
  players protect a pet), and `stackNamedMobs` opts into stacking those too, with mobs of the same
  name only. A stack keeps its name through kills and through conversions (zombie → drowned).
- **Touching a stacked mount hands you one animal out of it.** A horse is tamed, saddled, given armor
  and ridden one at a time, so right-clicking a herd now steps a single horse out of the stack and
  applies everything you do to that horse — including climbing onto it. It rejoins the herd afterwards
  unless what you did was to keep it. Covers horses, donkeys, mules, llamas, camels and skeleton and
  zombie horses.

### Fixed
- **A tamed, saddled or loaded mob is never stacked again.** A horse's saddle, armor and chest sit in
  an inventory of the horse's own rather than in its equipment slots, so `stackEquippedMobs` never
  protected them, and taming, ownership and temper were copied straight off the mob being merged
  away onto the survivor. **A wild horse wandering into your tamed, saddled, armored horse wiped all
  of it.** Stacking now leaves alone anything a player has put something into: tamed or owned mobs,
  saddled ones, horse armor, a donkey's or llama's chest, a leashed mob, and anything currently
  riding or being ridden. Wild herds — and the foals a breeding pen produces, which are born
  untamed — still stack exactly as before.
- **Mobs of different kinds no longer merge and then change appearance.** Which mobs count as the
  same was checked in one place and carried onto a respawned stack in another, and the two had come
  apart; horses were in neither, so a herd merged regardless of colour and came back in a colour
  rolled at random. Both halves now live in one table, and it covers the types that were missing:
  **horses, llamas** (colour and strength), **rabbits, parrots, pandas** (both genes), **goats**
  (screaming, horns), **tropical fish, charged creepers, shulkers, snow golems** and the block an
  **enderman** is carrying. Cats keep their collar colour and mooshrooms their stewed effect.
- **Vanilla's name easter eggs work on stacks.** `Dinnerbone` and `Grumm` hang a stack upside down,
  `jeb_` makes a stack of sheep cycle the dye colours, and `Toast` gives a stack of rabbits the
  memorial skin. All four compare the mob's whole name to a literal, which `Dinnerbone x16` never
  matched. Rendering only, and it needs the mod on the client.
- **A merge no longer overwrites the surviving stack's name or gear** with the name and gear of the
  mob being merged away. Merging a plain cow into a stack named `Bella x16` used to turn it into
  `Cow x17`.
- **Naming a mob something that looks like a stack label no longer confuses the mod.** A cow named
  literally `Cow x5` was treated as if the name were the mod's own label, and a name ending in ` xN`
  had that suffix eaten. The live count is now only ever appended to the name as typed, never parsed
  back off it — the one exception being retyping the label you can see, which is taken to mean the
  name without it.
- **The self-test exercised settings it could not switch on.** A setting that depends on another one
  refuses to be enabled while that one is off, which made `/mobstacker selftest` report a failure for
  `sweepingEdgeSingleHit`. The test now switches a setting's prerequisites on for the duration.

### Changed
- `/mobstacker region add` now points at `region bounds` when the name is taken, instead of only
  saying the region exists.
- Different colours of sheep, and sheared and unsheared ones, still refuse to stack together. That
  was already true and stays true on purpose — a stack shows one body, and it is what a per-stack
  shearing rule will need.

## [1.6.0] - 2026-09-17
### Added
- **Vanilla-style Sweeping Edge** (`sweepingEdgePerMob`, default `false`). With it on, the mob you
  hit takes the full hit and **every other mob in the stack takes its own sweep hit** —
  `1 + attack damage x (level / (level + 1))`, the vanilla formula — instead of the stack receiving a
  single flat bonus. Since that attack damage is the damage *after* Sharpness, Smite and Bane of
  Arthropods, and a stack is always one mob type, each enchantment automatically scales the sweep
  against the mobs it is meant for. The sweep wounds every member, and those wounds add up from swing
  to swing, so a stack wears down the way a herd of loose mobs does under repeated sweeps instead of
  shrugging the sweep off whenever one swing cannot kill outright. Note that a sweep strong enough to
  kill a healthy mob of that type clears the whole stack in one swing — exactly what vanilla would do
  to those mobs standing loose, but a big jump in power, so three tuning options come with it.
  Where a stack has no separate mobs to wound — `stackHealth` pools its health into one bar, or
  `killWholeStackOnDeath` makes it die as one — every other member's sweep is added to that single
  hit instead, which costs the stack the same total health and so takes the same number of swings.
- `sweepingEdgeSingleHit` (default `false`, needs `sweepingEdgePerMob`): put the whole sweep into the
  one hit even when the members *are* separate, and let damage overflow carry it down the stack. Ten
  mobs with Sweeping Edge III means the top mob takes the weapon's damage plus nine sweeps' worth, so
  a swing kills several outright instead of wounding all of them. It takes `damageOverflow` to reach
  past the mob you struck; without it the swing simply kills that one mob, which is the point of the
  option — all the damage in one place.
- `sweepingEdgeVanillaConditions` (default `false`): only sweep when vanilla actually would — a fully
  charged swing, no critical hit, not sprinting, standing on the ground, sword in hand.
- `sweepingEdgeMaxKills` (default `0` = no cap): the most mobs one swing's sweep may kill.
- `stackScanInterval` (default `20` ticks): how often a mob re-checks for a nearby stack to join. Set
  it to `0` for the old behaviour, where mobs only ever merge on crossing a block boundary.
- **Settings per region.** A region can now carry its own value for almost every setting, so a cow
  farm can stack to 64 while a mob grinder next door stays at 16, with its own combat, feedback,
  breeding, drops and display behaviour. Anything a region does not override simply follows the
  global config, so you only state the differences and existing config files keep working untouched.
  Manage it with `/mobstacker region set|unset <region> <setting> <value>`, see everything a region
  does with `/mobstacker region show <region>`, and settle overlapping regions with
  `/mobstacker region priority <region> <n>` (higher wins, ties go to the smaller region). The only
  settings that stay global are `stackMode` and `playerStackRadius` — which decide where the region
  system applies at all — and the seven mob caps, which are world-level spawn limits. `deny` regions
  still override everything, in every mode. The config GUI has it all too, behind a new **Regions…**
  button: pick a region, browse the categories, and each row shows a gold label where the region sets
  the value itself and a grey one where it follows the global config, with a button to drop the
  override. It works on a remote server the same way the rest of the GUI does — operators edit, other
  players look.
- **Colour control for stack names and kill holograms.** The `Cow x16` name above a stack is drawn in
  `stackNameColor` (any of the sixteen Minecraft colours), and the floating `-N` kill hologram in
  `killHologramColor`, so stacks no longer all look alike. Turn on `stackNameColorBySize` and the
  colour steps up with the stack — the base colour, then `stackNameColorMedium` from
  `stackSizeMediumThreshold` and `stackNameColorLarge` from `stackSizeLargeThreshold` — so a huge
  stack is recognisable at a glance. Mobs named with a name tag keep their own colour, and colour
  changes apply to stacks that already exist. These live in a new **Stack display** settings category
  (`/mobstacker help display`).
### Changed
- **The config GUI scrolls.** A settings page used to draw its rows at fixed positions, so a long
  category — or simply a large GUI scale — could push the last settings off the bottom of the screen
  where they could not be reached. Each page now shows as many rows as fit and the mouse wheel moves
  through the rest, with a line telling you which rows you are looking at.
### Fixed
- **Sweeping Edge and `damageOverflow` are independent settings again.** They answer different
  questions — overflow decides whether the leftover of a *killing blow* carries onto the mobs below,
  while Sweeping Edge decides how much damage a sweep deals at all — but the code had them tangled:
  with `damageOverflow` off, **every** sweeping mode bailed out and the hit fell back to the plain
  weapon damage, so `sweepingEdgeOverflow` and everything under it silently did nothing. Sweeping
  Edge now always adds its damage, and how far into the stack that reaches is up to the stack: a
  pooled health bar spends all of it, damage overflow carries it down, and with neither it fells the
  mob in front of you. Per-mob sweeping goes on wounding the members with overflow off too — the
  wounds and the overflow are worked out separately now, and the kill count, loot and XP follow
  whichever of them actually killed something.
- **`stackHealth` forces `killWholeStackOnDeath` wherever it is on.** Pooling a stack's health into
  one bar only makes sense if the whole stack dies with it, so `stackHealth` forces
  `killWholeStackOnDeath` on. That was done by rewriting the stored value every time the config was
  saved, which cost two things: your own `killWholeStackOnDeath` choice was overwritten the first
  time `stackHealth` went on and never came back when it went off, and it only ever covered the
  *global* config — so a region that enabled `stackHealth` for itself silently kept the global
  `killWholeStackOnDeath`, and pooled health did nothing in that region. The rule is now applied
  where the settings are read, region included, and stores nothing, so switching `stackHealth` off
  hands your own value back. The GUI greys the forced setting out, shows the value the game acts on
  and says in red why it cannot be changed, instead of accepting a click and quietly reverting it.
- **A region row follows a change made by another setting.** The region screen only repainted a row
  when that row's own override changed, so a value that moved because a *different* row moved was
  left stale — switching `stackHealth` in a region greyed `killWholeStackOnDeath` out correctly but
  went on showing `OFF` until the screen was reopened, and dropping its override painted the global
  value over a setting the region still forces on. Every row now repaints from what is really in
  force there. Tooltips also wrap to the window instead of running off its right edge.
- **A region no longer claims a change it does not make.** A region stores only the settings it
  changes, so a stored value that says exactly what the global config already says is not an
  override. Setting a region value *to* the global one already dropped it, but changing the *global*
  value to match the region's did not: the row stayed gold and the region stayed pinned to a value it
  was no longer changing. Such an override is now dropped on the way to disk, whichever of the two
  moved, and a row is coloured by the difference itself rather than by the presence of a stored value.
- **A setting that depends on another one behaves the same everywhere.** The `sweepingEdge*` tuning
  options only work while `sweepingEdgeOverflow` is on, but that was checked against the *global*
  config only: inside a region that had enabled `sweepingEdgeOverflow` for itself, they were still
  refused, and the config GUI let you flip the switch anyway and then quietly dropped the change. The
  dependency is now resolved wherever the change is made — a region uses its own value for it, just
  as the game does at the mob — and the GUI greys a setting out (with a tooltip saying why) until the
  setting it needs is on. A setting whose dependency is off also **reads as off**, all the way down a
  chain of them, so a switch can never sit on `ON` while having no effect: turning
  `sweepingEdgeOverflow` off shows `sweepingEdgePerMob` and `sweepingEdgeSingleHit` as off too. What
  you had set is kept, not erased, and comes straight back when you turn the master setting on again.
  The GUI also repaints a row from the config after every edit, so a value the config refuses can no
  longer sit on a widget as if it had been saved.
- **Mobs that never move now stack.** Merging was only attempted when a mob crossed a block boundary,
  so mobs that simply stay put — several spawn eggs used on the same block, mobs with no AI, a penned
  or stuck group — stood side by side and never stacked until something nudged them. Every mob now
  also re-checks on a timer (see `stackScanInterval`), staggered across mobs and skipped for stacks
  that are already full, so it costs less than the movement checks it complements.
- **Kill holograms no longer get stuck in the world.** The floating `-N` text above a killed stack is
  an armor stand that lives for about a second, but it was being written into the world save like any
  other entity: if the server stopped — or the chunk unloaded — during that second, it came back on
  the next load with nothing left to remove it and stayed floating there forever. Holograms are now
  kept strictly in memory and are never saved, and are cleared when the server stops. The versions
  that leaked them did not mark their armor stands at all, so the ones already stuck in a world had
  to be deleted by hand; an untagged stand is now recognised by its shape instead — an invisible,
  silent, invulnerable marker with no gravity, no base plate and no equipment, named nothing but
  `-<number>` — and removed as soon as its chunk loads, with a line in the server log saying where.

## [1.5.3] - 2026-07-03
### Added
- **New `PLAYERS` stack mode.** Mobs stack only when they are near a player — within
  `playerStackRadius` blocks (default **12**) of anyone — and nowhere else. Great for keeping farms
  and busy areas tidy without stacking mobs out in the untouched wilderness. DENY regions still
  override it, so you can carve out no-stack pockets even inside the player bubble. Set it with
  `/mobstacker set stackMode players` and tune the range with `/mobstacker set playerStackRadius <n>`.
### Changed
- **Stacking now ships OFF by default.** A freshly installed mod no longer stacks anything until an
  operator opts in, so nothing changes on your server until you choose to enable it. The first time a
  world loads without a config, the server log prints a short notice explaining that stacking is OFF
  and how to turn it on. (Existing configs keep whatever mode they already had.)
- **Adding an ALLOW region auto-enables REGIONS mode** when stacking is currently OFF, so a
  freshly-defined region takes effect immediately instead of silently doing nothing. An explicit
  `everywhere`/`players` mode is never overridden.

## [1.5.2] - 2026-07-03
### Added
- **"Nothing will stack" heads-up.** `/mobstacker` and `/mobstacker region list` now print a yellow
  note when `stackMode` is `REGIONS` but no `ALLOW` region is defined — the one config in which mobs
  stack nowhere at all — and point to the two ways out (add an ALLOW region, or switch to
  `everywhere`). A frequent source of "stacking isn't working" confusion.
### Fixed
- **Commands now persist to disk.** Changes made with `/mobstacker set`, `toggle`, `reset`,
  `reset all`, `ignore …`, and `region add|remove` are now written to the world's
  `serverconfig/mobstacker.json` immediately, so they survive a server restart. Previously these
  commands only changed the config **in memory** and were lost on restart unless something else saved
  the file — the config GUI already saved, so this brings commands in line with it. (`stacksize`,
  which sets a live mob's count rather than a config value, is unaffected.)

## [1.5.1] - 2026-07-02
### Added
- **Config GUI on servers (phase 2).** The config screen can now edit a **remote server's** config,
  not just singleplayer. When you open it on a server that has the mod, it shows the server's live
  settings (fetched over a config-sync channel) and — if you are an **operator** — pushes your
  changes back to the server, which validates them exactly like the `/mobstacker` commands and
  **saves them to the world's config file**. Non-operators see the config read-only. The networking
  uses **optional channels**, so a vanilla client (or any client without the mod) is never sent
  anything and never disconnected — stacking stays fully server-side.

## [1.5.0] - 2026-07-02
*Never released on its own — these changes first reached players as part of 1.5.1.*
### Added
- **In-game config GUI (phase 1).** A client-side screen to change settings without commands,
  driven by the same settings registry: on/off and cycle buttons for booleans and `stackMode`, edit
  boxes (with live validation) for numbers and item ids, browsed one category at a time. Open it via
  a new **key binding** (“Open Config GUI”, unbound by default — set it in Options → Controls) or the
  client command **`/mobstackerconfig`**. For now it edits the config in **singleplayer / on the LAN
  host**; on a remote server it is informational only (config-sync networking will come in a later
  phase), so commands remain the way to configure a dedicated server.
### Changed
- **Fabric API is now a required dependency**, used by the config GUI (key binding, client command,
  and upcoming networking). The mod is otherwise still self-contained — no other mods are needed, and
  stacking remains server-side (a client only needs the mod to use the GUI).

## [1.4.0] - 2026-07-01
### Added
- `/mobstacker selftest` — a **developer** command (opt-in only: registered when the game/server is
  launched with `-Dmobstacker.selftest=true`, so it never appears in the normal build) that
  automatically round-trips every setting (reset/set/toggle/validate, bounds and enum/item checks,
  plus the stackHealth dependency) and reports pass/fail. It runs against a throwaway sandbox config,
  so the live per-world config is never touched — a quick way to sanity-check the whole config
  surface at once.
### Changed
- **Command overhaul.** The `/mobstacker` tree is now flat and consistent, matching how vanilla
  commands feel. Every scalar setting is changed through generic, tab-completed subcommands
  instead of a per-setting branch:
  - `/mobstacker set <setting> <value>` — change any setting (value validated for its type).
  - `/mobstacker get <setting>` — show a setting's value, default and description.
  - `/mobstacker toggle <setting>` — flip a boolean.
  - `/mobstacker reset <setting>` / `reset all` — restore defaults.
  - `/mobstacker help [category]` — browse settings by category (stacking, combat, feedback,
    breeding, drops, separator, mobcaps) with values and descriptions.
  Tab-completion suggests every setting name and then the valid values for the chosen one.
- Clearer feedback: changes show `old -> new` in green, no-ops are yellow (not red), and errors
  are red. The two "stack size" concepts are now clearly separated — `set maxStackSize` (the global
  limit) vs `stacksize <target> <n>` (force a targeted mob's live count).
- Ignore lists are now consistent: `/mobstacker ignore <entity|mod> <add|remove|list>`.
### Removed
- The old `stackerConfig …`, `mobCapConfig …`, `setStackSize …`, `unignore …` and nested
  `separator …` command paths. All their functionality moved to the new `set/get/toggle/reset`
  commands above. **The config file format is unchanged** — only the command syntax changed.
### Internal
- Settings are now declared once in a data-driven `ConfigOption` registry (`MobStackerSettings`)
  that drives the commands (and will drive the upcoming config GUI), removing ~450 lines of
  duplicated per-setting command code.

## [1.3.0] - 2026-07-01
### Added
- **Drop compaction** (`compactDrops`, default `true`): a stacked mob's death drops are now merged
  into as few full item stacks as possible instead of dozens of scattered item entities, cutting
  item-entity lag on large farms. It never creates or destroys loot — the same drops are re-emitted
  packed into fewer entities, keeping different items and different enchantments/NBT apart. Works
  with every kill path (normal, `killWholeStackOnDeath`, and damage overflow). New
  `/mobstacker stackerConfig compactDrops <true|false>` command + settings-overview line.
- **Experience compaction** (`compactExperience`, default `true`): a stacked mob's whole death
  experience (including every extra mob killed via overflow / `killWholeStackOnDeath`) is now
  combined into a **single orb** instead of a swarm of tiny ones. Same total XP, far fewer orb
  entities. Independent toggle: `/mobstacker stackerConfig compactExperience <true|false>`.

## [1.2.3] - 2026-07-01
### Fixed
- A stack's respawned remainder (after a kill) and separated mobs kept their **age/baby state**
  now: killing a member of a baby-stack no longer turns the survivors into adults (and
  `finalizeSpawn` can no longer roll a random baby into an adult stack).

## [1.2.2] - 2026-07-01
### Added
- `breedOnePerClick` config flag (default `false`): feed **one member per click** (click once
  per animal) instead of feeding as many members as the food in hand allows in a single click.
  Applies to both breeding and baby-stack growth feeding.

## [1.2.1] - 2026-07-01
### Fixed
- Joining a mob to a stack no longer **resets the breeding cooldown** (the whole stack could be
  re-bred). The surviving stack's own data is preserved on merge instead of being overwritten by
  the discarded mob.
- `stackHealth`: merging no longer **collapses the accumulated max health** to twice a single
  mob's — it now accumulates correctly across merges.
- Survivors respawned after a partial kill **keep the breeding cooldown** instead of resetting it.
### Changed
- Loose baby-stacks now **consolidate freely** instead of fragmenting by a strict age band; a
  merged baby-stack keeps the **youngest age** so no member grows up early.

## [1.2.0] - 2026-07-01
### Added
- **Stack breeding**: feed a stacked adult animal its food to breed its members in pairs into a
  single **baby-stack** (fair one-food-per-mob cost, ~5-minute cooldown on the bred members,
  partial feeding remembered so nothing is wasted). Feeding a baby-stack speeds its growth,
  scaled to the stack size.
- **Baby stacking**: loose babies now stack too — farm-animal babies matched by age, and
  hostile/other babies such as baby zombies (which never grow up) simply stack together.
- Config flags (all default `true`): `enableStackBreeding`, `enableAnimalBabyStacking`,
  `enableHostileBabyStacking`, with matching `/mobstacker stackerConfig` commands.

## [1.1.1] - 2026-07-01
### Changed
- Rebranded the fork as **"MobStacker: Restacked"** (display name only — `modId` stays
  `mobstacker` for world/config/NBT compatibility). Authors: michalekjarmark & davidex; original
  author frikinjay credited. Reworked the README and repository presentation.

## [1.1.0] - 2026-07-01
### Changed
- The mod is now **fully standalone** — the **Almanac** library dependency was removed and its
  helpers reimplemented in-house (Gson config I/O, command registration, localized names,
  equipment drop). Only the Fabric Loader and Minecraft are required.
- **Let Me Despawn** downgraded from a hard dependency to an optional companion (`suggests`).
### Added
- `/mobstacker reload` to re-read the config from disk after a manual edit.

## [1.0.16] - 2026-07-01 — Fork foundation
The initial run of this fork's Fabric 1.20.1 line (versions ~1.0.15–1.0.20), backported from the
original 1.21 mod and extended with new features:
### Added
- **Region-based stacking**: global `StackMode` (`REGIONS` / `EVERYWHERE` / `OFF`) plus allow/deny
  cuboid regions per dimension (deny always wins). Commands: `/mobstacker region add|remove|list`.
- **Damage overflow**: a lethal hit's leftover damage carries onto the mobs below, dropping
  loot/XP/score for every mob killed and leaving the next survivor wounded. Flag
  `enableDamageOverflow`.
- **Sweeping Edge support**: folds vanilla sweep damage back into the hit so it clears stacks.
  Flag `sweepingEdgeOverflow`.
- **Stack-kill feedback**: an action-bar line, a scaling particle pop, and a floating `-N`
  hologram, each with its own toggle (`stackKillActionBar` / `stackKillParticles` /
  `stackKillHologram`).
- **Per-world config**: settings live in `<world>/serverconfig/mobstacker.json` instead of a
  single global file.
- **Equipment-aware stacking**: mobs holding/wearing items stay unstacked by default
  (`stackEquippedMobs`, variant B).
### Fixed
- Death-animation loop when a mob merged into a dying stack.

---

Older history (upstream, before this fork's 1.20.1 line) lives in the original
[MobStacker](https://github.com/frikinjay/mob-stacker) repository.
