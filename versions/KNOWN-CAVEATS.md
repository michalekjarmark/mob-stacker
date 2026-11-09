# Known caveats, by Minecraft version

What broke (or would have) when this mod was moved from 1.20.1 to newer versions, why, and where the
mod deals with it. Written for whoever adds the next node (person or agent) so they know where to
look before they start searching. How to add a node is in [`README.md`](README.md); this file is only
the traps.

**Versions in brackets** are where the change is believed to have happened. Only **1.20.1, 1.21.1 and
26.3** are built so far, so anything between 1.21.2 and 26.1 is *unconfirmed* until a node in between
compiles and runs. The code's version conditions sit at those believed versions too; a new node moves
them if `-PallErrors` or a test says otherwise (they are marked "unconfirmed" in the code).

The worst ones are first: **no crash, no log line, it just does not work**.

## Silent: no error, wrong behaviour

| Since | Symptom | Cause | Where the mod handles it |
|---|---|---|---|
| 1.21.6 | Text drawn by a screen is **invisible**; a text box goes **blank** after `setTextColor` (typing still works). | A text colour is ARGB, and `0xFFFFFF` (what 1.20.1 code passes) has alpha 0. | Every colour through `client/Gfx` (`text`, `centered`, `textColor(box, rgb)`), which makes a colour without alpha opaque. Never call `drawString`/`setTextColor` directly. |
| 26.1 (SDL) | **Right click does nothing** on a widget that accepts it; a left click may count as the right one. | Mouse buttons are numbered as SDL does: left **1**, middle 2, right **3** (GLFW: 0, 2, 1). | Use `InputConstants.MOUSE_BUTTON_LEFT/RIGHT` (the right value on every version, inlined at compile time), never literals (`client/StepButton`). |
| 26.1 (SDL) | **Typing does nothing** until a text box is clicked ("type anywhere to search"). | Characters arrive only while something started text input (`Minecraft.textInputManager()`, one owner at a time); an `EditBox` does it when focused and stops when it loses focus. | A screen that takes typing with no box focused (`CompatScreen.typesAnywhere()`) claims it every frame and lets go in `removed()`, and answers `isInputCaptured()` true: otherwise the game's global keys (the friends list on **O**) take a letter before the screen sees it. |
| 26.1 | Key codes compared against GLFW constants never match. | GLFW is gone (SDL). | `InputConstants.KEY_*` (they exist on 1.20.1 too, with GLFW's values). |
| 1.21.5 | NBT reads quietly return nothing; `set.contains(tag.getString(k))` is always false. | `CompoundTag` getters return `Optional`. | Every read through `compat/Nbt`, which keeps 1.20.1's answers (0, `""`, empty when missing). |
| 1.21.5 | Shearing a stack gives the **top sheep's wool only**. | Wool comes from the `shearing/sheep` loot table, which requires `sheared: false`; the mod shears the same (top) sheep again for each member. | `SheepShearMixin.mobstacker$shearMembers` sets the sheep woolly before each shear. Any "repeat vanilla's harvest per member" must check the loot table's conditions. |
| 1.21.5 | Every copy of a stack member gets the top's **saddle** (or body armour). | Saddles and body armour are equipment slots, saved with the rest of the equipment. | `MobStacker.memberOf` clears SADDLE and BODY; `TOP_ONLY_KEYS` has the new key names. Re-check `TOP_ONLY_KEYS` against `src/main/vanilla-keys/<mc>.txt` on every port. |
| 1.21.5 | Saved key names changed (turtle `HomePosX` -> `home_pos` int array, `has_egg`, `sleeping_pos`, `last_hurt_by_*`). | Data rewrite to snake_case. | `TOP_ONLY_KEYS`, `ConfigSelfTest` (version branches). |
| 1.21.6 | A command clicked in chat opens a **"Run command" confirmation** every time. | The client asks before running a clicked command that needs a permission. | `Compat.runCommand` sends a *custom click action* instead (`mobstacker:command`), handled by `ServerCommonPacketListenerImplMixin` -> `MobStackerCommands.runClicked`: runs as the player who clicked, with their permissions, `/mobstacker` commands only. No client mod needed. |
| 1.21.2 | Sweeping Edge sweeps in other situations than on 1.20.1. | Vanilla's sweep condition changed (horizontal known movement, `#swords`). | The mod follows that version's vanilla: `Compat.stillEnoughToSweep`, `Compat.isSword`. |
| 26.x | `ChatFormatting.getColor()` is gone / colour of a format is unknown. | `ChatFormatting` carries no colour. | `Compat.rgb(ChatFormatting)`. |
| 26.1 | A per-frame world label **jumps** instead of sliding. | Easy to place by `entity.getEyePosition()` (moves once a tick) when rewriting rendering. | Use the frame's camera: `minecraft.gameRenderer.mainCamera().position()` (`MobStackerRegionOverlay.emitGizmos`). |
| 26.1 | Screens show a blurred world, no dark backdrop. | Vanilla's own background. | Not a bug: `CompatScreen.drawBackground` calls vanilla's. |

## Refuses to load or crashes

| Since | Symptom | Cause | Where |
|---|---|---|---|
| 26.1 | Loom fails; no `mappings`/`modImplementation`/`remapJar`/`neoForge` accessor. | Minecraft is not obfuscated from 26.1. | `stonecutter.gradle.kts` applies `dev.architectury.loom-no-remap` to non-`1.x` nodes; `build.gradle.kts` `unobfuscated`, configurations by string. |
| 26.1 | `validateAccessWidener` fails / widener not applied. | The widener's namespace is `official`, not `named`. | `build.gradle.kts` writes an `official` copy for Loom and filters the jar's. Lines whose target is gone go in `widenerBefore` (`LivingEntity deathScore` from 1.21.2). |
| 26.1 | **NeoForge 26.x has no access transformer yet.** | `remapJar` converts the widener to an AT only while obfuscated. | Unsolved. |
| 1.21.2 | Mixin apply error on a `@Redirect` that `checkMixins` passed. | A redirect that captures the target method's arguments (`LivingEntity.actuallyHurt` gained a `ServerLevel`). | Version-specific handler in `LivingEntityMixin`. |
| ≤26.3 | Mixin apply error on an `@Invoker` that `checkMixins` passed. | The method moved to a superclass (`Animal.usePlayerItem` -> `Mob`). | `AnimalInvoker` targets `Mob` on 26.x. |
| 1.21.6 | Entity save/load methods missing. | `ValueOutput`/`ValueInput` replace `CompoundTag` there. | `Compat.save`/`load` (`TagValueOutput`/`TagValueInput`, `ProblemReporter.DISCARDING`); `EntityDataMixin`. `tools/VanillaKeys.java` reads both shapes. |
| 26.1 | Conversion mixins (pig/villager lightning, tadpole, zombie) find no target. | Conversions go through `Mob.convertTo(EntityType, ConversionParams, ...)`. | `MobMixin`'s `convertTo` carries the stack; the old ones are in `mixinsBefore`. |
| 26.1 | Spawn cap mixin finds no target. | `NaturalSpawner.createState` reads the level's entities itself. | `NaturalSpawnerMixin` replaces `ServerChunkCacheMixin`. |
| 26.1 | Every drawing class fails to compile. | New renderer (`com.mojang.renderpearl`), GUI drawn through `GuiGraphicsExtractor`/`extractRenderState`, entities through render states and `SubmitNodeCollector`. | Region boxes are **gizmos** (`emitGizmos`, `client.LevelRendererGizmosMixin`), stack labels a render-state name tag line (`EntityRendererMixin`, `client.EntityRenderStateMixin`), screens bridged by `client/CompatScreen` (drawing *and* input events). The old classes compile below 26.1 only. |
| any | A mixin config with a dropped entry is invalid JSON. | Entries are dropped per node by `mixinsBefore`/`mixinsFrom`; a dropped last entry leaves a trailing comma. | Version-limited entries go at the **top** of their list in `mobstacker.mixins.json`. |
| 1.20.1 node | `x instanceof Player p` does not compile where `x` is already a `Player`. | The 1.20.1 node compiles with Java 17 (21 allows it). | Write it the Java 17 way in shared code. |

**`checkMixins` is not the last word**: it found neither of the two mixin errors above. Run every new
node's jar on a real server with every injector required (`prodServer -PstrictMixins`, or
`tools/test/run_server.py`), which is how both were found.

## Renames that only break compilation

Mostly handled by Stonecutter replacements in `stonecutter.gradle.kts` (both ways, the whole file
including mixin descriptor strings) or by a helper in `compat/Compat.java`.

| Since | Old | New |
|---|---|---|
| 1.21 | `new ResourceLocation(ns, path)` | `ResourceLocation.fromNamespaceAndPath` (`Compat.id`) |
| 1.21 | data folders `tags/entity_types` | `tags/entity_type` (renamed at build time) |
| 1.21.2 | `MobSpawnType`, `EntityType.create(level)` | `EntitySpawnReason`, `create(level, reason)` |
| 1.21.2 | `spawnAtLocation(stack)`, `hurt(...)` | `spawnAtLocation(ServerLevel, stack)`, `hurtServer(ServerLevel, ...)` |
| 1.21.2 | `registryOrThrow`, `getHolderOrThrow`, `Registry.get(id)` | `lookupOrThrow`, `getOrThrow`, `getValue(id)` |
| 1.21.2 | `MobEffects.MOVEMENT_SLOWDOWN` | `SLOWNESS` |
| 1.21.5 | `moveTo(...)` | `snapTo(...)` |
| 1.21.5 | `new ClickEvent(Action.X, v)`, `new HoverEvent(...)` | records: `ClickEvent.RunCommand`, `SuggestCommand`, `HoverEvent.ShowText` |
| 1.21.5 | `getOwnerUUID()`, `Saddleable`, `SwordItem`, `Inventory.items/offhand` | `getOwnerReference()`, `Mob.isSaddled()`, `#swords`, `getNonEquipmentItems()` |
| 1.21.5 | `Shearable.shear(source)` | `shear(ServerLevel, SoundSource, ItemStack)` |
| 1.21.5 | `VillagerData` getters | record: `profession()` holder, `withProfession(...)` |
| 1.21.9 | `GameProfile` getters, profile cache | record `id()`/`name()`, `NameAndId` |
| 1.21.9 | `keyPressed(int,int,int)`, `charTyped(char,int)`, `mouseClicked(double,double,int)` | `KeyEvent`, `CharacterEvent`, `MouseButtonEvent` (`CompatScreen.onKey/onChar/onClick`) |
| 1.21.11 | `ResourceLocation`, `ResourceKey.location()` | `Identifier`, `identifier()` |
| 1.21.11 | entity packages (`animal.Cow`, `monster.Zombie`, `npc.Villager`, `animal.horse`) | `animal.cow`, `monster.zombie`, `npc.villager`, `animal.equine`, ... |
| 1.21.11 | `hasPermissions(int)` | `permissions()` (`PermissionSet`) |
| 26.1 | `EntityType.COW` (constants) | `EntityTypes.COW` |
| 26.1 | `GuiGraphics`, `drawString`, `drawCenteredString` | `GuiGraphicsExtractor`, `text`, `centeredText` |
| 26.1 | `Minecraft.setScreen`, `.screen` | `minecraft.gui.setScreen`, `.gui.screen()` |
| 26.1 | `displayClientMessage(c, true)` | `sendOverlayMessage(c)` |
| 26.1 | Fabric API `ClientCommandManager`, `KeyBindingHelper`, `HudRenderCallback`, `PayloadTypeRegistry.playC2S/playS2C` | `ClientCommands`, `KeyMappingHelper`, `HudElementRegistry`, `serverboundPlay/clientboundPlay` |
| 26.1 | `KeyMapping` category string | `KeyMapping.Category.register(id)` |

A trap of the tooling itself: a **mass search-and-replace** that turns an old constructor into a call
to a `Compat` helper also rewrites the helper's own old-version branch into a call to itself
(`Compat.runCommand` called `Compat.runCommand` on 1.20.1: a stack overflow on the first clickable
panel, caught before any release). After such a replace, look at the helpers' `else` branches.

## Known gaps on 26.x (not fixed)

- With `separateStackLabel` off (the count written into the name), stacks named `Dinnerbone`/`Grumm`,
  `Toast` and `jeb_` lose their easter egg: 26.x reads those names off render states.
- Kill holograms left in a world by 1.5.x are no longer cleaned up: armour stands do not tick on their own.

## Testing traps

- From **1.21.9 there are no always-loaded spawn chunks**: a scripted test that summons near spawn with
  no player finds nothing. `forceload add` the area first (`tools/test/scenarios/basic.txt` does).
- The 1.20.1 round scenarios assume a **flat** world (`run_server.py --flat`), and their NBT is
  1.20.x's (`HandItems`, `Count`); 1.20.5 and 1.21.5 changed parts of it.
- 26.3 rejects `gamerule doMobSpawning` (game rules were renamed somewhere after 1.21.1); the line
  fails on its own and the scenario goes on.
- `tools/test/mcclient.py` speaks the 1.20.1 protocol only: anything on a newer client needs a person.
- Reading vanilla: `javap -p -c -cp <jar> <class>` on the node's jar in
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/<mc>*/` (JDK 25's `javap` reads
  every version); data files (loot tables, tags) are in the same jar.
