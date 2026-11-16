# Known caveats, by Minecraft version

What broke (or would have) when this mod was moved from 1.20.1 to newer versions, why, and where the
mod deals with it. Written for whoever adds the next node (person or agent) so they know where to
look before they start searching. How to add a node is in [`README.md`](README.md); this file is only
the traps.

**Versions in brackets** are where the change is believed to have happened. Built so far: **1.20.1,
1.20.2, 1.20.4, 1.20.6, 1.21.1, 26.1.2, 26.2 and 26.3**, so everything up to 1.21.1 is confirmed and anything
between 1.21.2 and 26.1 is *unconfirmed* until a node in between compiles and runs. The code's version
conditions sit at those believed versions too; a new node moves them if `-PallErrors` or a test says
otherwise (they are marked "unconfirmed" in the code). The nodes for 1.20.2-1.20.6 moved four of the
guesses made from 1.20.1 and 1.21.1 alone: see "Between 1.20.1 and 1.21.1" below. The 26.2 node
moved six guesses from 26.1 to **26.3**, the version that went from GLFW to SDL, and the 26.1.2 node
eight more to 26.2: see "26.1 to 26.3".

The worst ones are first: **no crash, no log line, it just does not work**.

## Silent: no error, wrong behaviour

| Since | Symptom | Cause | Where the mod handles it |
|---|---|---|---|
| 1.20.5 | A world from an older version loses the **gear of every member under a stack's top mob** (`keepMemberEquipment`). | Items are saved as `{id, count, components}` from 1.20.5 (`{id, Count, tag}` before), and vanilla's data fixers upgrade only what they know: the mod's `StackData` rides along unchanged. | `StackDataUpgrade`: the save carries the data version (`StackData.DataVersion`, missing = 1.20.1's 3465), and a load runs every member's items through vanilla's `ITEM_STACK` fixer (`EntityDataMixin`). Anything new the stack data saves that vanilla may reshape (items, ids) needs a step there. |
| 1.20.5 | A member taken out of a **leashed** stack comes out leashed as well. | The leash is saved as `leash` from 1.20.5 (`Leash` before), and a copy leaves out only the keys it knows. | `TOP_ONLY_KEYS` (the condition sat at 1.21 until the 1.20.6 node). |
| NeoForge 20.2 | A Fabric client's packets to a NeoForge 20.2 server arrive **garbled**, and the empty config request **fails to decode**. | NeoForge 20.2 reads a payload it does not know as its `SimplePayload`, which takes a VarInt off the front as a packet index (and writes one when sending). | `neoforge.mixin.Clientbound/ServerboundCustomPayloadPacketMixin` read the mod's channels as bare bytes (`LegacyPayload`), and the mod sends them as `LegacyPayload`; see `neoforge.legacy.LegacyChannels`. NeoForge passes only its own `SimplePayload` on to event channels, so `neoforge.mixin.LegacyServerPayloadMixin` and `client.LegacyClientPayloadMixin` hand ours over themselves. Checked with a player: a NeoForge 20.2 server with a NeoForge client (labels, config, region boxes), and a Fabric client's config request arriving whole. |
| 1.20.2-1.20.4 | **Singleplayer and LAN** (integrated server): no stack labels and no config sync, with no error. | The integrated server hands its client the packet's payload object as it is, never written and read back. Fabric API before 1.20.5 takes only its own payload (`ServerPlayNetworking.createS2CPacket`), NeoForge 20.2 only its `SimplePayload`; the mod's `RawPayload` went to neither. NeoForge 20.4 and everything from 1.20.5 match the payload by id or type, so they took it. | Every packet the server builds for a channel comes from `MobStackerNetworking.packet`, which asks the loader's `Transport.packet`: Fabric below 1.20.5 makes Fabric API's packet, NeoForge 20.2 a `LegacyPayload` one. |
| NeoForge 20.4 | A **Fabric** client with the mod sees **no stack labels** on a NeoForge 20.4 server. | NeoForge 20.4 filters a mod's payloads out of a bundle unless the channel went through NeoForge's own negotiation (`NetworkRegistry.filterGameBundlePackets`, logged at TRACE only); a Fabric client names its channels with `minecraft:register`, which only the packets sent on their own are checked against. The label rides in the pairing bundle. | `StackLabels.labelOutsideBundle` (set by `MobStackerNeoForgeLegacy` on 20.4): the label goes on its own at the end of the tick. Not before the bundle: `ClientLevel.addEntity` removes whatever had the id first, and the label with it. |
| 1.20.5, 1.21.2 | A horse coming out of a stack from an older world gets **default** speed/jump/health instead of its own. | Member rolls are keyed by attribute id: `horse.jump_strength` became `generic.jump_strength` (1.20.5), and the `generic.` prefix went (1.21.2). | `MobVariants.upgradeRolls` (`ROLL_KEY_ALIASES`), from `StackDataUpgrade`. |
| 1.21.6 | Text drawn by a screen is **invisible**; a text box goes **blank** after `setTextColor` (typing still works). | A text colour is ARGB, and `0xFFFFFF` (what 1.20.1 code passes) has alpha 0. | Every colour through `client/Gfx` (`text`, `centered`, `textColor(box, rgb)`), which makes a colour without alpha opaque. Never call `drawString`/`setTextColor` directly. |
| 26.3 (SDL) | **Right click does nothing** on a widget that accepts it; a left click may count as the right one. | Mouse buttons are numbered as SDL does: left **1**, middle 2, right **3** (GLFW: 0, 2, 1). | Use `InputConstants.MOUSE_BUTTON_LEFT/RIGHT` (the right value on every version, inlined at compile time), never literals (`client/StepButton`). |
| 26.3 (SDL) | **Typing does nothing** until a text box is clicked ("type anywhere to search"). | Characters arrive only while something started text input (`Minecraft.textInputManager()`, one owner at a time); an `EditBox` does it when focused and stops when it loses focus. | A screen that takes typing with no box focused (`CompatScreen.typesAnywhere()`) claims it every frame and lets go in `removed()`, and answers `isInputCaptured()` true: otherwise the game's global keys (the friends list on **O**) take a letter before the screen sees it. |
| 26.3 | Key codes compared against GLFW constants never match. | GLFW is gone (SDL; 26.2 is still GLFW). | `InputConstants.KEY_*` (they exist on 1.20.1 too, with GLFW's values). |
| 1.21.5 | NBT reads quietly return nothing; `set.contains(tag.getString(k))` is always false. | `CompoundTag` getters return `Optional`. | Every read through `compat/Nbt`, which keeps 1.20.1's answers (0, `""`, empty when missing). |
| 1.21.5 | Shearing a stack gives the **top sheep's wool only**. | Wool comes from the `shearing/sheep` loot table, which requires `sheared: false`; the mod shears the same (top) sheep again for each member. | `SheepShearMixin.mobstacker$shearMembers` sets the sheep woolly before each shear. Any "repeat vanilla's harvest per member" must check the loot table's conditions. |
| 1.21.5 | Every copy of a stack member gets the top's **saddle** (or body armour). | Saddles and body armour are equipment slots, saved with the rest of the equipment. | `MobStacker.memberOf` clears SADDLE and BODY; `TOP_ONLY_KEYS` has the new key names. Re-check `TOP_ONLY_KEYS` against `src/main/vanilla-keys/<mc>.txt` on every port. |
| 1.21.5 | Saved key names changed (turtle `HomePosX` -> `home_pos` int array, `has_egg`, `sleeping_pos`, `last_hurt_by_*`). | Data rewrite to snake_case. | `TOP_ONLY_KEYS`, `ConfigSelfTest` (version branches). |
| 1.21.6 | A command clicked in chat opens a **"Run command" confirmation** every time. | The client asks before running a clicked command that needs a permission. | `Compat.runCommand` sends a *custom click action* instead (`mobstacker:command`), handled by `ServerCommonPacketListenerImplMixin` -> `MobStackerCommands.runClicked`: runs as the player who clicked, with their permissions, `/mobstacker` commands only. No client mod needed. |
| 1.21.2 | Sweeping Edge sweeps in other situations than on 1.20.1. | Vanilla's sweep condition changed (horizontal known movement, `#swords`). | The mod follows that version's vanilla: `Compat.stillEnoughToSweep`, `Compat.isSword`. |
| 26.x | `ChatFormatting.getColor()` is gone / colour of a format is unknown. | `ChatFormatting` carries no colour. | `Compat.rgb(ChatFormatting)`. |
| 26.1 | A per-frame world label **jumps** instead of sliding. | Easy to place by `entity.getEyePosition()` (moves once a tick) when rewriting rendering. | Use the frame's camera: `minecraft.gameRenderer.mainCamera().position()` (`MobStackerRegionOverlay.emitGizmos`). |
| 26.1 | Screens show a blurred world, no dark backdrop. | Vanilla's own background. | Not a bug: `CompatScreen.drawBackground` calls vanilla's. |
| 26.1 | **Water and clouds cover a region box** (X-ray off; found in round 26.x-1, unseen on 26.3 Fabric because that instance ran Improved Transparency). | The game draws its see-through gizmos before the translucent terrain (26.1: in the main pass; 26.2: `PreparedFrame.executeTranslucent`'s `gizmos` phase; 26.3: its `translucentGizmos` phase), clouds and weather come after, and a gizmo writes no depth. | Drawn late instead, per version (`client.LevelRendererGizmosMixin`, `client.FeatureFrameGizmosMixin`, `client/LateGizmos`): 26.1 in the late debug pass, 26.2 at the start of the always-on-top pass (**which the game adds only when there are always-on-top gizmos**: added for these too, and it clears the depth only for its own), 26.3 at the end of `executeClassicTransparency`. 26.3's Improved Transparency (order-independent) is left alone. In the classic mode the water hides the part of a box below its surface from above (1.20.1 showed it). |
| NeoForge 26.1 (unconfirmed; 21.1 did not) | Shears on a **mooshroom** stack turn the whole stack into cows and give mushrooms for one; with `stackedHarvest` off nothing steps out. | NeoForge 26.x moves only the sheep's, snow golem's and bogged's shears into `IShearable`; the mooshroom and the copper golem keep vanilla's in `mobInteract`. The mod assumed every shears interaction on NeoForge was outside `mobInteract`. | `MobStacker.shearingOutsideMobInteract` is a predicate per mob (`MobStackerNeoForge`); `MushroomCowShearMixin` harvests the members in `mobInteract` on Fabric and on 26.1+. Check the list again at the 1.21.2-1.21.11 NeoForge nodes. |
| NeoForge 20.2-21.1 | A sheared **mooshroom** stack becomes **one cow** (the stack is lost), by hand or dispenser. | The cow is made by `create` + `addFreshEntity` (`shear`, or Forge's `shearInternal` on 20.2); only Fabric and Forge had the mixin that carries the stack. | `MushroomCowShearMixin` (`neoforge && <26.1`); from 26.1 it is `convertTo`, which `MobMixin` handles. Scenario: `tools/test/scenarios/shear_dispenser_mooshroom.txt`. |

## Refuses to load or crashes

| Since | Symptom | Cause | Where |
|---|---|---|---|
| 26.1 | Loom fails; no `mappings`/`modImplementation`/`remapJar`/`neoForge` accessor. | Minecraft is not obfuscated from 26.1. | `stonecutter.gradle.kts` applies `dev.architectury.loom-no-remap` to non-`1.x` nodes; `build.gradle.kts` `unobfuscated`, configurations by string. |
| 26.1 | `validateAccessWidener` fails / widener not applied. | The widener's namespace is `official`, not `named`. | `build.gradle.kts` writes an `official` copy for Loom and filters the jar's. Lines whose target is gone go in `widenerBefore` (`LivingEntity deathScore` from 1.21.2). |
| 26.1 | NeoForge 26.x: no access transformer in the jar (fields the widener opens are private at run time). | `remapJar` converts the widener to an AT only while obfuscated, and 26.x has no remapping. | `build.gradle.kts` writes `META-INF/accesstransformer.cfg` from the node's widener lines (`accessTransformerOf`; Mojang's names are the run-time names there) and leaves the widener out of the jar. |
| 1.21.2 (unconfirmed; 1.21.11 and 26.2 have it) | The server refuses to start: "Critical injection failure" in `ServerChunkCacheMixin`, "Scanned 0 target(s)". | `ServerChunkCache.tickChunks` has an overload (`tickChunks(ProfilerFiller, long)`), and **Mixin takes the first method of a bare name**: the call is in the other one. | `Compat.TICK_CHUNKS`, the full descriptor. `checkMixins` selects as Mixin does since (it used to look at every overload). Give a target with overloads its descriptor. |
| 1.21.2 | Mixin apply error on a `@Redirect` that `checkMixins` passed. | A redirect that captures the target method's arguments (`LivingEntity.actuallyHurt` gained a `ServerLevel`). | Version-specific handler in `LivingEntityMixin`. |
| ≤26.3 | Mixin apply error on an `@Invoker` that `checkMixins` passed. | The method moved to a superclass (`Animal.usePlayerItem` -> `Mob`). | `AnimalInvoker` targets `Mob` on 26.x. |
| 1.21.6 | Entity save/load methods missing. | `ValueOutput`/`ValueInput` replace `CompoundTag` there. | `Compat.save`/`load` (`TagValueOutput`/`TagValueInput`, `ProblemReporter.DISCARDING`); `EntityDataMixin`. `tools/VanillaKeys.java` reads both shapes. |
| 26.1 | Conversion mixins (pig/villager lightning, tadpole, zombie) find no target. | Conversions go through `Mob.convertTo(EntityType, ConversionParams, ...)`. | `MobMixin`'s `convertTo` carries the stack; the old ones are in `mixinsBefore`. |
| 26.3 | Spawn cap mixin finds no target. | `NaturalSpawner.createState` reads the level's entities itself (26.2 still has them handed over by `ServerChunkCache.tickChunks`). | `NaturalSpawnerMixin` replaces `ServerChunkCacheMixin`. |
| 26.1 | Every drawing class fails to compile. | New renderer (`com.mojang.renderpearl`), GUI drawn through `GuiGraphicsExtractor`/`extractRenderState`, entities through render states and `SubmitNodeCollector`. | Region boxes are **gizmos** (`emitGizmos`, `client.LevelRendererGizmosMixin`), stack labels a render-state name tag line (`EntityRendererMixin`, `client.EntityRenderStateMixin`), screens bridged by `client/CompatScreen` (drawing *and* input events). The old classes compile below 26.1 only. |
| any | A mixin config with a dropped entry is invalid JSON. | Entries are dropped per node by `mixinsBefore`/`mixinsFrom`; a dropped last entry leaves a trailing comma. | Version-limited entries go at the **top** of their list in `mobstacker.mixins.json`. |
| 1.20.1 node | `x instanceof Player p` does not compile where `x` is already a `Player`. | The 1.20.1 node compiles with Java 17 (21 allows it). | Write it the Java 17 way in shared code. |

## Between 1.20.1 and 1.21.1

What the nodes for 1.20.2, 1.20.4 and 1.20.6 found. 1.20.3/1.20.4 changed nothing the mod touches.

| Since | What | Where |
|---|---|---|
| 1.20.2 | `ClientboundCustomPayloadPacket(channel, buf)` is gone: the packet carries a `CustomPacketPayload` that writes itself (typed payloads with codecs only from 1.20.5). Fabric API's own send calls still take a channel and a buffer until 1.20.5. | `network/RawPayload` (three shapes), `MobStackerNetworking.packet` / `Transport.packet` |
| 1.20.2 | Potion effect keys: `ActiveEffects` -> `active_effects` and so on. | `src/main/vanilla-keys/1.20.2.txt`; none is top-only |
| 1.20.5 | `LevelRenderer.renderLevel` has no pose stack (the camera is on the model-view matrix), but still takes the tick as a float; `DeltaTracker` is 1.21. | `client.LevelRendererMixin` (three handlers) |
| 1.20.5 | The dispenser wears its shears with `hurtAndBreak(int, RandomSource, ServerPlayer, Runnable)`: neither 1.20.1's `hurt` nor 1.21's. | `Compat.DISPENSER_SHEARS_WEAR` |
| 1.20.5 | `AttributeModifier.Operation` names (`ADD_VALUE`, `ADD_MULTIPLIED_TOTAL`), the modifier still has a name and a UUID. | `ConfigSelfTest` |
| **1.21, not 1.20.5** | Saved attributes: `Attributes`/`Modifiers`/`Name` -> `attributes`/`modifiers`/`id`, a modifier known by a resource id (`minecraft:leader_zombie_bonus`). | `Compat.ATTRIBUTES_KEY` and the three beside it |
| **1.21, not 1.20.5** | `Saddleable.equipSaddle(ItemStack, SoundSource)`; 1.20.5 has 1.20.1's `equipSaddle(SoundSource)`. | `Compat.saddle` |

Confirmed where they were: 1.20.2 (the shared packet listener, the mouse wheel, `EditBox`, the debug
overlay); 1.20.5 (items as components, `generic.jump_strength`, the body armour slot, `Parrot.TAME_FOOD`
gone, `EntityDataAccessor` a record, `finalizeSpawn` without the tag, `hurtAndBreak` with a slot, text
JSON with registries, `TURTLE_SCUTE`); 1.21 (`tags/entity_type`, `dropAllDeathLoot` with the level,
`ResourceLocation`'s private constructor).

## NeoForge before 20.5 (Minecraft 1.20.2-1.20.4)

Still half Forge, so it has an entrypoint of its own: the `neoforge.legacy` package, which
`build.gradle.kts` (`legacyNeoForge`) compiles instead of `MobStackerNeoForge` and `neoforge/client`.
`NeoForgeShearing` and the loader's mixins are shared by both.

| | NeoForge 20.2 | NeoForge 20.4 | NeoForge 20.5+ |
|---|---|---|---|
| Mod file | `META-INF/mods.toml`, `mandatory = true` | `META-INF/mods.toml`, `type = "required"` (FML 2.0.17 **refuses to load** a mod that says `mandatory`) | `META-INF/neoforge.mods.toml` |
| Mixin configs | `[[mixins]]` in the toml from FML 1.0.8 (every stable 20.2); the betas before read only the manifest | `[[mixins]]` | `[[mixins]]` |
| Networking | Forge's event channels (`NetworkRegistry.newEventChannel`) | `RegisterPayloadHandlerEvent`, `IPayloadRegistrar.play(id, reader, handlers)`, `PacketDistributor.PLAYER.with(player).send`, `isConnected(id)` | `RegisterPayloadHandlersEvent`, `PayloadRegistrar`, stream codecs |
| Client events | `TickEvent.ClientTickEvent` with a phase, `RegisterGuiOverlaysEvent` | the same | `ClientTickEvent.Post`, `RegisterGuiLayersEvent` |
| `IShearable.onSheared` | `(player, item, level, pos, fortune)`; the mobs override it | the same shape; the default calls `Shearable.shear` | `(player, item, level, pos)` |
| Dispenser's shears | vanilla's `Shearable.shear` | vanilla's | vanilla's on 20.6, `IShearable.onSheared` from 21.0 (`ShearsDispenseItemBehaviorMixin`) |
| `ServerEntity.sendPairingData` | vanilla's `Consumer` | `PacketAndPayloadAcceptor` | `PacketAndPayloadAcceptor` (until 26.1) |

NeoForge 20.3 (1.20.3) and 20.5 (1.20.5) never left beta, so the NeoForge jars are for 1.20.2, 1.20.4
and 1.20.6 only, and each asks for the first stable build of its line (20.2.86, 20.4.167, 20.6.115),
which is what it was run on besides the newest.

## 26.1 to 26.3

What the 26.1.2 and 26.2 nodes found. **26.1** (with its hotfixes 26.1.1 and 26.1.2) already has the
new renderer, gizmos, render states, `GuiGraphicsExtractor`, the input events, `convertTo` and Fabric
API's renames; much of the rest came with 26.2:

| Since | What | Where |
|---|---|---|
| 26.2 | `EntityType.COW` -> `EntityTypes.COW` | `stonecutter.gradle.kts` |
| 26.2 | `minecraft.setScreen(s)`, `minecraft.screen` -> `minecraft.gui.setScreen(s)`, `minecraft.gui.screen()` | `stonecutter.gradle.kts` |
| 26.2 | `animal.Bucketable` -> `entity.Bucketable` (1.21.11 and 26.1.2 have the old one), `monster.Slime` -> `monster.cubemob.Slime` | `stonecutter.gradle.kts` |
| 26.2 | `gameRenderer.getMainCamera()` -> `mainCamera()` | `Gfx.cameraPosition` |
| 26.2 | `SubmitNodeCollector.submitNameTag` loses the camera distance (`state.distanceToCameraSq`) | `Gfx.submitNameTag` |
| 26.2 | the dyes become one collection, `Items.DYE.pick(color)`; 26.1 has neither that nor `DyeItem.byColor` (the colour is a component) | `Compat.dye` (by id on 26.1) |
| 26.2 | gizmos are finalized from `LevelRenderer.submitFeatures` (26.1: from a lambda of the main pass) | `client.LevelRendererGizmosMixin` hooks the start of `finalizeGizmoCollection` itself, the same on every 26.x |
| 26.2 | saved keys: `age`, `age_locked`, `from_bucket`, `pickup_timer`, `sitting_damage_received` new, `HurtByTimestamp` not in the list (none new is top-only; `HurtByTimestamp` stays in `TOP_ONLY_KEYS` for 26.1) | `src/main/vanilla-keys/26.1.2.txt` |

And what the 26.2 node found. 26.2 still runs on **GLFW** (`InputConstants.KEY_A` is 65, the left mouse
button 0); **SDL came with 26.3**, and with it the changes below, which the code had at 26.1.

| Since | What | Where |
|---|---|---|
| 26.3 | `KeyEvent(key, keycode, modifiers)` (26.2: `scancode`), `InputConstants.Type.KEYBOARD` (26.2: `KEYSYM`), `Screen.isInputCaptured()`, `TextInputManager.startTextInput(owner)` (26.2: no owner, and every typed character reaches the screen) | `CompatScreen` (`keyCodeOf`, `wantTyping`, `isInputCaptured`), `MobStackerClient.key` |
| 26.3 | `EnderMan` -> `Enderman` | `stonecutter.gradle.kts` |
| 26.3 | `Inventory.placeItemBackInInventory(stack, Prediction)` | `Compat.giveOrDrop` |
| 26.3 | `Entity.setInvulnerable` -> `setPermanentlyInvulnerable` (`invulnerable_time` saved) | `Compat.setInvulnerable` |
| 26.3 | `NaturalSpawner.createState` reads the level's entities | `NaturalSpawnerMixin` from 26.3, `ServerChunkCacheMixin` below it |
| 26.3 | Saved keys: `DrownedConversionTime`, `StrayConversionTime`, `InWaterTime`, `AssignProfessionWhenSpawned` are not in 26.3's list, `can_break`, `color`, `invulnerable_time` are new; none is top-only | `src/main/vanilla-keys/26.2.txt` |

What the code still has at 26.1 compiles on 26.1.2 and its mixins apply there; whether a change came
in 26.1 itself or between 1.21.11 and 26.1 is for a 1.21.11 node to tell.

## NeoForge between 1.21.1 and 26.3

Found porting the NeoForge module (davidex's, for 1.21.1) to 26.3; where each changed in between is
unknown (conditions at 26.1). NeoForge 26.2 (FML 11) has every one of them already: its node needed
no change of its own, and registers key categories as 26.3 does.

| 1.21.1 | 26.3 | Where |
|---|---|---|
| `FMLEnvironment.dist` | `FMLEnvironment.getDist()` (FML 12) | `MobStackerNeoForge` |
| `PacketDistributor.sendToServer` | `client.network.ClientPacketDistributor.sendToServer` | `MobStackerNeoForgeClient` |
| `ServerEntity.sendPairingData(player, PacketAndPayloadAcceptor)` | vanilla's `Consumer<Packet<...>>` (the acceptor is gone) | `ServerEntityMixin` (`neoforge && <26.1`) |
| `ServerPlayer.getServer()` | gone: `player.level().getServer()` (works on every version) | `MobStackerNeoForge` |
| `MinecraftServer.handleCustomClickAction(id, payload)` (vanilla) | NeoForge adds the player and the profile to the call | `Compat.HANDLE_CUSTOM_CLICK_ACTION` |
| dispenser `tryShearLivingEntity` | `tryShearEntity` (as vanilla) | `Compat.DISPENSER_SHEAR_ENTITY` |
| mods are built inside `Minecraft`'s constructor (`Minecraft.getInstance()` is set) | mods are built from `Main.main` **before** `Minecraft` exists: `Minecraft.getInstance()` is **null** in the mod's constructor - the client crashes at launch ("Cannot read field gameDirectory"), a server never shows it | Nothing called from a mod constructor asks the game for anything: `MobStackerClient.init(transport, gameDirectory)`, NeoForge passes `FMLPaths.GAMEDIR.get()` |

Key categories: vanilla's `KeyMapping.Category.register` is enough on NeoForge too (its
`ClientHooks.onRegisterKeyMappings` only appends the categories registered through the event to the
same list); register a category one way, not both, or it is listed twice.

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
| 1.21.6 (unconfirmed) | `getCurrentVersion().getDataVersion().getVersion()` | `dataVersion().version()` (`StackDataUpgrade.currentVersion`) |
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
| 26.2 | `EntityType.COW` (constants) | `EntityTypes.COW` |
| 26.1 | `GuiGraphics`, `drawString`, `drawCenteredString` | `GuiGraphicsExtractor`, `text`, `centeredText` |
| 26.2 | `Minecraft.setScreen`, `.screen` | `minecraft.gui.setScreen`, `.gui.screen()` |
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

## Fabric and NeoForge on one server (not fixed)

A client of the other loader joins and plays; the question is whether the mod's channels work between
them, or the client is treated as one without the mod (labels in the mob's name, no config screen, no
region boxes). Checked 2026-11-16 with a dedicated server and a client of the same Minecraft, the
player an operator:

| Server → client | 1.20.2 | 1.20.4 | 1.20.6 | 1.21.1 |
|---|---|---|---|---|
| Fabric → NeoForge | as without the mod | as without the mod | as without the mod | as without the mod |
| NeoForge → Fabric | as without the mod | as without the mod | **works** | **works** |
| the same loader | works | works | works | works |

- **Fabric server, NeoForge client:** the NeoForge client never names its channels to a server that is
  not NeoForge (Fabric's `ServerPlayNetworking.getSendable` stays empty for good), so the server never
  sends it anything. The client does see the server's channels: its config request arrives.
- **NeoForge 20.2/20.4 server, Fabric client:** 20.4 never tells the client its channels in a way
  Fabric reads (only `neoforge:register`), so the client never asks; 20.2 gets the client's request but
  does not see the client's channels.
- A fix would have the NeoForge side send `minecraft:register` with the mod's channels itself, in
  each NeoForge line's own way; not tried.

- From **1.21.9 there are no always-loaded spawn chunks**: a scripted test that summons near spawn with
  no player finds nothing. `forceload add` the area first (`tools/test/scenarios/basic.txt` does).
- **`NoAI` mobs never stack** (the mod leaves a frozen mob alone on purpose): keep a test mob still with
  `NoGravity` and a force-loaded chunk instead, and at night (`time set midnight`, `gamerule
  doDaylightCycle false`) so undead do not burn and wear their helmets.
- **Moving a world to another version** (member gear, rolls): save it with one node's server
  (`run_server.py <old node> --dir migrate`), copy `versions/<old>/run/migrate/world` to
  `versions/<new>/run/migrate/`, and start the new node with `--dir migrate`.
- The 1.20.1 round scenarios assume a **flat** world (`run_server.py --flat`), and their NBT is
  1.20.x's (`HandItems`, `Count`); 1.20.5 and 1.21.5 changed parts of it.
- 26.3 rejects `gamerule doMobSpawning` (game rules were renamed somewhere after 1.21.1); the line
  fails on its own and the scenario goes on.
- `tools/test/mcclient.py` speaks the 1.20.1 protocol only: anything on a newer client needs a person.
- The server scripts never build the mod's client side. **Start a new node's client once** before a
  jar goes to a person (`./gradlew :<node>:runClient`, up to the title screen): the NeoForge 26.3 jar
  passed every server test and crashed the client at launch. With **`-PstrictMixins`** a client
  mixin that finds nothing fails there too (without it, it does nothing, silently), and with
  `--args="--quickPlaySingleplayer <world>"` it goes straight into `run/saves/<world>` (a copy of the
  test server's world will do). A new run dir's first start **waits on the narrator screen**: put
  `onboardAccessibility:false` in `versions/<node>/run/options.txt` first (and `soundCategory_master:0.0`
  for a quiet one; the game rewrites the file when it closes).
- **Seeing what a client draws without a person**: build a test world on the node's server (a scenario
  can `setworldspawn x y z <yaw> <pitch>` to aim the camera; creative via `defaultgamemode creative`),
  copy it to `run/saves/<world>`, write `run/config/mobstacker-overlay.json` to show its regions
  (`{"worlds":{"world:<world>":{"showAll":true}}}`), start the strict client with quickPlay and
  capture **the game window only** with Win32 `PrintWindow(hwnd, hdc, 2)` (PW_RENDERFULLCONTENT).
  Never `CopyFromScreen`: Windows refuses to bring the window forward and it copies whatever else is
  on the person's screen.
- **A dedicated server with a client of another node** (other loader, same Minecraft): start the server
  with `run_server.py <node> --keep` and a scenario that builds the scene, then
  `./gradlew :<node>:runClient --args="--quickPlayMultiplayer localhost:25599"`. Started from a script,
  give Gradle `stdin=DEVNULL`: with the parent's input it sat for 15 minutes without a line. The overlay
  key of a server is `server:<address>` (`server:localhost:25599`), and a player sees other people's
  regions only as an operator (`op @a` over RCON; the Fabric dev client is `PlayerNNN`, not `Dev`).
  The labels prove nothing on their own: a game the server thinks has no mod gets them in the mob's
  name, and they look the same. The region box (from the config snapshot) is what shows the channels work.
- Two scenarios that share a pen leave mobs for each other: `shear_dispenser.txt` uses 300 300,
  `shear_dispenser_mooshroom.txt` 320 320 and empties it afterwards.
- A NeoForge node's **patched** jar (what the loader changed in vanilla's classes, and its own API) is
  the `neoforge-<version>-minecraft-merged-*` folder beside the vanilla ones; `checkMixins` writes the
  whole classpath to `versions/<node>/build/mixin-check-classpath.txt`.
- **`Reset active project` leaves the `neoforge` package as it was**: the committed sources are the
  `1.20.1-fabric` node's, which leaves that package out, so after `Set active project to <a NeoForge
  node>` a reset brings back everything else and not those files. Check `git diff
  src/main/java/com/frikinjay/mobstacker/neoforge/` before a commit, and restore with `git checkout`
  what you did not mean to change (by hand what you did: committed code there is in the 1.20.1 form,
  with `<1.20.5` branches live and `ResourceLocation`).
- Write code inside a commented branch in **1.20.1's words** (`MobSpawnType`, `ResourceLocation`, in
  mixin descriptors too): the replacements turn them into the newer ones for that node, and a reset
  turns a newer word written there back. A reset that changes a committed file means one of the two.
  (`.getHolderOrThrow(key)` -> `.getOrThrow(key)` from 1.21.2 is only the call with an argument, so
  `DataResult.getOrThrow()` stays as written.)
- Reading vanilla: `javap -p -c -cp <jar> <class>` on the node's jar in
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/<mc>*/` (JDK 25's `javap` reads
  every version); data files (loot tables, tags) are in the same jar.
