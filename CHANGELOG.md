# Changelog

All notable changes to **MobStacker: Restacked** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
via the `mod_version` in `gradle.properties`. This is an independently-developed fork of
[MobStacker](https://github.com/frikinjay/mob-stacker) by frikinjay, under LGPL v3.

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
