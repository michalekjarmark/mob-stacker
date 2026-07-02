# Changelog

All notable changes to **MobStacker: Restacked** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
via the `mod_version` in `gradle.properties`. This is an independently-developed fork of
[MobStacker](https://github.com/frikinjay/mob-stacker) by frikinjay, under LGPL v3.

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
