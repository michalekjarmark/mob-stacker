# MobStacker: Restacked

<center>
<img src="https://media.forgecdn.net/attachments/988/553/mobstacker-1.png" alt="A skeleton stacked to almost the maximum integer limit">
</center>

<p align="center">
<img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen" alt="Minecraft 1.20.1">
<img src="https://img.shields.io/badge/Loader-Fabric-1976d2" alt="Fabric">
<img src="https://img.shields.io/badge/Java-17-orange" alt="Java 17">
<img src="https://img.shields.io/github/license/michalekjarmark/mob-stacker" alt="License: LGPL v3">
<img src="https://img.shields.io/badge/dependencies-none-success" alt="No required dependencies">
<img src="https://img.shields.io/github/last-commit/michalekjarmark/mob-stacker" alt="Last commit">
</p>
<br>

> 🍴 **About this fork:** *MobStacker: Restacked* is an **actively-developed, independent fork**
> of [MobStacker](https://github.com/frikinjay/mob-stacker) by **frikinjay** (the original
> author), maintained by **michalekjarmark & davidex** and distributed under **LGPL v3**. We
> develop it independently of the original author, who no longer maintains the mod — adding new
> features, fixing bugs, and keeping a **Fabric 1.20.1** line running for our server.

> ✅ **Standalone:** this fork has **no required library dependencies** — it needs only the
> Fabric Loader and Minecraft. (Earlier versions required the *Almanac* library; that
> functionality is now built in.) *Let Me Despawn* is an optional companion, not a requirement.

**MobStacker** is a performance Minecraft mod to optimize entity handling, addressing a common cause of performance issues in vanilla and modded environments. By intelligently "stacking" similar mobs in close proximity, MobStacker significantly reduces server load and enhances client-side performance without compromising gameplay mechanics.

> 💡 **Note**: MobStacker preserves all loot and mob properties within stacked entities. Named mobs (via name tags) are exempt from stacking to maintain uniqueness.

## ✨ What this fork adds

Everything below is on top of the original MobStacker — see the linked sections for detail:

- 🗺️ **Region-based stacking** — limit stacking to chosen world regions (allow/deny cuboids + a global mode).
- 💥 **Damage overflow** — one big hit kills several mobs in a stack and drops loot/XP for each.
- ⚔️ **Sweeping Edge support** — folds vanilla sweep damage back into the hit so it clears stacks.
- 🎯 **Stack-kill feedback** — action bar, a scaling particle pop, and a floating `-N` hologram (each toggleable).
- 🗂️ **Per-world config** — settings live in the world save folder, so they no longer leak between worlds.
- 🧰 **Equipment-aware stacking** — mobs holding/wearing items stay unstacked by default (`stackEquippedMobs`).
- 🪶 **Zero dependencies** — Almanac's functionality is built in; nothing but Fabric Loader + Minecraft required.
- 🛠️ **Quality of life** — `/mobstacker` settings overview and `/mobstacker reload` for on-the-fly config reloads.

## Key Benefits

1. **🚀 Server Performance Boost**: Dramatically reduces entity processing overhead.
2. **📈 Enhanced Client FPS**: Lowers strain on client-side rendering.
3. **🌐 Optimized Network Traffic**: Minimizes entity data transmission.
4. **🎮 Cleaner Gameplay**: Reduces visual clutter in mob-dense areas.
5. **💾 Memory Usage Reduction**: Lowers overall memory footprint.

## Performance Improvement

While actual performance gains vary based on server specifications, player count, and mod configurations, MobStacker can provide substantial improvements, especially in environments with:

- High mob density
- Numerous entity-adding mods
- Large, active player bases

> 🔗 **Recommended Companion Mods**:
> - [Let Me Despawn](https://www.curseforge.com/minecraft/mc-mods/let-me-despawn) (optional — recommended for vanilla-style despawn handling)
> - [Spawncap Control Utility](https://www.curseforge.com/minecraft/mc-mods/spawncapcontrolutility) or [In Control!](https://www.curseforge.com/minecraft/mc-mods/in-control) (Enhanced mob control)

## Features

| Feature | Description |
|---------|-------------|
| 📦 Smart Stacking | Automatically combines identical mob types within a configurable radius |
| 🔢 Customizable Stacks | Set preferred maximum stack sizes |
| ❤️ Flexible Health Management | Configurable options for stack health and death mechanics |
| 🚫 Selective Stacking | Ability to exclude specific entities or entire mods |
| 🔪 Stack Splitting | Implemented separator item functionality for dividing stacks |
| 🗺️ Region-Based Stacking | Limit stacking to chosen world regions (allow/deny cuboids) |
| 💥 Damage Overflow | A single big hit kills multiple mobs in a stack and drops loot for each |
| ⚔️ Sweeping Edge Support | Sweeping Edge adds bonus damage against stacks instead of doing nothing |

## Configuration

| Option | Description | Default |
|--------|-------------|---------|
| `killWholeStackOnDeath` | Determines if entire stack dies when one mob is killed | `false` |
| `stackHealth` | Combines health of stacked mobs when enabled | `false` |
| `enableDamageOverflow` | Carries leftover damage from a lethal hit onto the next mobs in the stack | `true` |
| `sweepingEdgeOverflow` | Lets the Sweeping Edge enchantment add bonus damage to stacks | `true` |
| `stackEquippedMobs` | If `true`, mobs holding/wearing items may stack (their gear is dropped on merge); if `false`, equipped mobs stay unstacked | `false` |
| `stackKillActionBar` | Show an action-bar line (above the hotbar) telling the killer how many mobs a hit killed and how many remain | `true` |
| `stackKillParticles` | Play a particle "pop" at the mob when a hit clears one or more mobs off a stack (scales with the number killed) | `true` |
| `stackKillHologram` | Show a short-lived floating `-N` hologram above the mob indicating how many that hit killed | `true` |
| `maxMobStackSize` | Maximum number of mobs in a single stack | `16` |
| `stackRadius` | Radius within which mobs attempt to stack | `6.0` |
| `enableSeparator` | Toggles use of separator item for stack splitting | `false` |
| `consumeSeparator` | Determines if separator item is consumed on use | `true` |
| `separatorItem` | Specifies the item used as a separator | `"minecraft:diamond"` |
| `ignoredEntities` | List of entities excluded from stacking | `["minecraft:ender_dragon", "minecraft:vex"]` |
| `ignoredMods` | List of mod IDs whose entities are excluded from stacking | `["corpse"]` |
| `stackMode` | Where new stacks may form: `REGIONS`, `EVERYWHERE` or `OFF` | `REGIONS` |
| `regions` | Allow/deny cuboids that gate stacking (see Region & Mode Management) | `[]` |

## Commands

All commands require operator permissions (level 2) and are prefixed with `/mobstacker`.

### Configuration Commands

```bash
# Toggle whole stack death
/mobstacker stackerConfig killWholeStackOnDeath [true|false]

# Toggle health stacking
/mobstacker stackerConfig stackHealth [true|false]

# Toggle damage overflow (one hit can kill several mobs in a stack)
/mobstacker stackerConfig damageOverflow [true|false]

# Toggle Sweeping Edge bonus damage against stacks
/mobstacker stackerConfig sweepingEdgeOverflow [true|false]

# Toggle whether mobs holding/wearing items are allowed to stack
/mobstacker stackerConfig stackEquippedMobs [true|false]

# Toggle the action-bar kill feedback (above the hotbar)
/mobstacker stackerConfig stackKillActionBar [true|false]

# Toggle the particle "pop" shown at the mob on a stack kill
/mobstacker stackerConfig stackKillParticles [true|false]

# Toggle the floating "-N" hologram shown above the mob on a stack kill
/mobstacker stackerConfig stackKillHologram [true|false]

# Set maximum stack size
/mobstacker stackerConfig maxStackSize [value]

# Set stack radius
/mobstacker stackerConfig stackRadius [value]

# Toggle separator functionality
/mobstacker stackerConfig separator enableSeparator [true|false]

# Toggle separator consumption
/mobstacker stackerConfig separator consumeSeparator [true|false]

# Set separator item
/mobstacker stackerConfig separator separatorItem [item_id]
```

### Entity and Mod Management

```bash
# Ignore specific entity
/mobstacker ignore entity [entity_id]

# Ignore all entities from a mod
/mobstacker ignore mod [mod_id]

# Remove entity from ignore list
/mobstacker unignore entity [entity_id]

# Remove mod from ignore list
/mobstacker unignore mod [mod_id]

# Set stack size for specific entity
/mobstacker setStackSize [entity] [size]

# Reload the config from disk (after editing the JSON file by hand)
/mobstacker reload
```
**NOTE**: An entity can be given the tag `{StackData: {CanStack:0b}}` to prevent it from stacking.

### Region & Mode Management

The global stacking mode controls *where* new stacks are allowed to form. It does
not affect mobs that are already stacked (they keep splitting/separating correctly
everywhere).

| Mode | Behaviour |
|------|-----------|
| `regions` *(default)* | Stacking only forms inside an `allow` region, and never inside a `deny` region. |
| `everywhere` | Stacking forms everywhere, except inside a `deny` region. |
| `off` | No new stacks ever form. |

```bash
# Set the global stacking mode
/mobstacker stackerConfig stackMode [regions|everywhere|off]
```

Regions are axis-aligned cuboids tied to the dimension you run the command in.
Corners accept absolute coordinates or `~` relative coordinates (relative to you).
`deny` regions always win over `allow` regions. Approximate custom shapes by adding
several cuboids.

```bash
# Add an ALLOW region (stacking enabled here)
/mobstacker region add <name> allow <x1> <y1> <z1> <x2> <y2> <z2>

# Add a DENY region (stacking forbidden here, overrides everything)
/mobstacker region add <name> deny <x1> <y1> <z1> <x2> <y2> <z2>

# Example: a cow-farm region around where you stand
/mobstacker region add cowfarm allow ~-15 ~-3 ~-15 ~15 ~5 ~15

# Remove a region
/mobstacker region remove <name>

# List all regions and the current mode
/mobstacker region list
```

> 💡 With the default `regions` mode and no regions defined, **no mobs stack at all**.
> Add at least one `allow` region (e.g. around a laggy farm) to enable stacking there
> while the rest of the world behaves like vanilla.

### Combat: Damage Overflow & Enchantments

By default a stack behaves like a single mob in combat: one hit removes the top mob,
and a fresh, full-health mob takes its place. **Damage overflow** changes that so a
hard-hitting blow is not wasted on the stack.

- When a hit deals more damage than the top mob's remaining health, the leftover
  damage is carried onto the mobs below it.
- A hit that would kill **N** mobs drops loot, experience and kill-score for **all N**
  mobs — not just one.
- If the overflow does not fully kill the next mob, that mob is left wounded (it does
  **not** get healed back to full).
- This works for **any** damage source — melee, Instant Damage / Harming potions,
  magic, lava, etc. — because it acts on the final post-armor damage. Minecraft's own
  resistance, armor and protection calculations are applied first, so they are
  respected automatically.

Because the related enchantments already increase a hit's damage in vanilla,
**Sharpness, Smite, Bane of Arthropods and Fire Aspect already feed overflow for
free** (a bigger hit simply kills more mobs). **Looting** is applied per mob killed.

**Sweeping Edge** is special: it normally damages mobs *around* the target, but a
stack is a single entity, so vanilla Sweeping Edge would do nothing here. Instead the
mod folds the vanilla sweep damage (`1.0 + level / (level + 1) × attack damage`) back
into the hit, so Sweeping Edge meaningfully clears stacks:

| Sweeping Edge | Bonus damage to the stack |
|---------------|---------------------------|
| I | `1.0 + 0.50 × attack damage` |
| II | `1.0 + 0.67 × attack damage` |
| III | `1.0 + 0.75 × attack damage` |

> 💡 Both behaviours are independent toggles — disable `damageOverflow` to return to
> one-kill-per-hit, or keep overflow but disable `sweepingEdgeOverflow` alone.
> `killWholeStackOnDeath` takes priority: with it enabled, any kill already wipes the
> whole stack, so overflow does not apply.

When a hit kills mobs from a stack the mod can show feedback three ways, each with its own
independent toggle:

- **Action bar** (`stackKillActionBar`, default on): a line above the killer's hotbar
  showing how many were killed by that hit and how many remain (e.g. `Killed 3× Cow • 9 left`).
- **Particle pop** (`stackKillParticles`, default on): a burst of particles at the mob,
  growing in amount and height with the number killed. It spawns no extra entities.
- **Floating hologram** (`stackKillHologram`, default on): a short-lived `-N` text that
  drifts up above the mob showing how many that hit killed. This is the only feedback
  channel that spawns an entity — an invisible marker armor stand removed after ~1 second.

### Mob Cap Management

```bash
# Set mob cap for mob categories
/mobstacker mobCapConfig [options]
```

## Additional Notes

- 🗂️ Config is stored **per world/server** in `<world>/serverconfig/mobstacker.json`,
  loaded when the server starts — so settings no longer leak between worlds. (Upgrading
  from an older version? Copy your old `config/mobstacker.json` there to keep your setup.)
- 🔄 Automatic stacking occurs when compatible mobs move to a new block.
- 👑 Boss entities receive special handling to preserve custom names and health bars.
- 🔌 API available for custom merging conditions, death handlers, and entity data modifiers.
- 📊 Stacked mobs display stack size in their name (e.g., "Zombie x5").
- 🐑🐷🧟 Compatible with various entity types: animals, monsters, and NPCs.

## Credits & License

- **Original author:** [frikinjay](https://github.com/frikinjay) — creator of MobStacker.
- **This fork (*MobStacker: Restacked*):** maintained by **michalekjarmark & davidex**,
  developed independently of the original author.
- **License:** [LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.html). This fork honors the
  original license and keeps the original author's attribution.

---

*Report issues on the [fork's issue tracker](https://github.com/michalekjarmark/mob-stacker/issues).*
