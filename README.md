# MobStacker: Restacked

<center>
<img src="https://media.forgecdn.net/attachments/988/553/mobstacker-1.png" alt="A skeleton stacked to almost the maximum integer limit">
</center>

<p align="center">
<img src="https://img.shields.io/badge/Minecraft-1.20.1-brightgreen" alt="Minecraft 1.20.1">
<img src="https://img.shields.io/badge/Loader-Fabric-1976d2" alt="Fabric">
<img src="https://img.shields.io/badge/Java-17-orange" alt="Java 17">
<img src="https://img.shields.io/github/license/michalekjarmark/mob-stacker" alt="License: LGPL v3">
<img src="https://img.shields.io/badge/requires-Fabric%20API-1976d2" alt="Requires Fabric API">
<img src="https://img.shields.io/github/last-commit/michalekjarmark/mob-stacker" alt="Last commit">
</p>
<br>

> 🍴 **About this fork:** *MobStacker: Restacked* is an **actively-developed, independent fork**
> of [MobStacker](https://github.com/frikinjay/mob-stacker) by **frikinjay** (the original
> author), maintained by **michalekjarmark & davidex** and distributed under **LGPL v3**. We
> develop it independently of the original author, who no longer maintains the mod — adding new
> features, fixing bugs, and keeping a **Fabric 1.20.1** line running for our server.

> 📦 **Dependencies:** the only required library is **[Fabric API](https://modrinth.com/mod/fabric-api)**,
> used by the in-game config GUI — no other mods. (Earlier versions required the *Almanac* library;
> that functionality is now built in.) *Let Me Despawn* is an optional companion, not a requirement.
> Stacking itself is server-side; a client only needs the mod to use the GUI.

> 📝 **Changelog:** see [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

**MobStacker** is a performance Minecraft mod to optimize entity handling, addressing a common cause of performance issues in vanilla and modded environments. By intelligently "stacking" similar mobs in close proximity, MobStacker significantly reduces server load and enhances client-side performance without compromising gameplay mechanics.

> 💡 **Note**: MobStacker preserves all loot and mob properties within stacked entities. Named mobs (via name tags) are exempt from stacking to maintain uniqueness.

## ✨ What this fork adds

Everything below is on top of the original MobStacker — see the linked sections for detail:

- 🗺️ **Where-to-stack modes** — `OFF` (default), `EVERYWHERE`, `REGIONS` (allow/deny cuboids), or `PLAYERS` (only near players, within a configurable radius). Stacking ships **off** until you opt in.
- 💥 **Damage overflow** — one big hit kills several mobs in a stack and drops loot/XP for each.
- ⚔️ **Sweeping Edge support** — folds vanilla sweep damage back into the hit so it clears stacks.
- 🎯 **Stack-kill feedback** — action bar, a scaling particle pop, and a floating `-N` hologram (each toggleable).
- 🐣 **Stack breeding & baby stacks** — feed a stacked animal to breed its members in pairs into a single baby-stack, and let loose babies (farm animals *and* hostile mobs like baby zombies) stack too.
- 📦 **Drop & XP compaction** — a stacked mob's death drops are merged into a few full item stacks (and its experience into a single orb) instead of dozens of scattered entities, cutting entity lag on big farms.
- 🗂️ **Per-world config** — settings live in the world save folder, so they no longer leak between worlds.
- 🧰 **Equipment-aware stacking** — mobs holding/wearing items stay unstacked by default (`stackEquippedMobs`).
- 🖥️ **In-game config GUI** — a key binding and the `/mobstackerconfig` command open a screen to change settings without typing commands (singleplayer, LAN host, and remote servers with the mod for operators). Commands still work everywhere.
- 🧭 **Streamlined commands** — a flat, vanilla-like tree: `/mobstacker set|get|toggle|reset <setting>` with tab-completion, plus `/mobstacker help`.
- 🪶 **Only Fabric API required** — Almanac's functionality is built in; the sole library dependency is Fabric API (for the GUI). No other mods.

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
| 🐣 Stack Breeding | Feed a stacked animal to breed its members in pairs into one baby-stack (fair one-food-per-mob cost) |
| 🍼 Baby Stacking | Loose babies stack too — farm animals (with age matching) and hostile mobs like baby zombies |

## Configuration

| Option | Description | Default |
|--------|-------------|---------|
| `killWholeStackOnDeath` | Determines if entire stack dies when one mob is killed | `false` |
| `stackHealth` | Combines health of stacked mobs when enabled. Forces `killWholeStackOnDeath` on for as long as it is on | `false` |
| `enableDamageOverflow` | Carries leftover damage from a lethal hit onto the next mobs in the stack | `true` |
| `sweepingEdgeOverflow` | Lets the Sweeping Edge enchantment add bonus damage to stacks | `true` |
| `sweepingEdgePerMob` | Sweep **every** mob in the stack for its own `1 + damage x (level / (level + 1))`, exactly like a vanilla sweep through a crowd, instead of adding one flat bonus to the hit | `false` |
| `sweepingEdgeSingleHit` | Put the whole sweep into the one hit — every other mob's sweep added to it — and let `damageOverflow` carry it down the stack, instead of wounding each mob separately. Needs `sweepingEdgePerMob` | `false` |
| `sweepingEdgeVanillaConditions` | Only sweep when vanilla would: fully charged swing, no critical hit, not sprinting, on the ground, sword in hand | `false` |
| `sweepingEdgeMaxKills` | Caps how many mobs one sweep may kill per swing (`0` = no cap). Only used by `sweepingEdgePerMob` | `0` |
| `stackEquippedMobs` | If `true`, mobs holding/wearing items may stack (their gear is dropped on merge); if `false`, equipped mobs stay unstacked | `false` |
| `stackKillActionBar` | Show an action-bar line (above the hotbar) telling the killer how many mobs a hit killed and how many remain | `true` |
| `stackKillParticles` | Play a particle "pop" at the mob when a hit clears one or more mobs off a stack (scales with the number killed) | `true` |
| `stackKillHologram` | Show a short-lived floating `-N` hologram above the mob indicating how many that hit killed | `true` |
| `killHologramColor` | Colour of that floating `-N` hologram | `RED` |
| `stackNameColor` | Colour of the `Cow x16` name above a stack (a mob named with a name tag keeps its own colour) | `WHITE` |
| `stackNameColorBySize` | Colour the stack name by how big the stack is, so large stacks stand out | `false` |
| `stackNameColorMedium` | Name colour from `stackSizeMediumThreshold` upwards | `YELLOW` |
| `stackNameColorLarge` | Name colour from `stackSizeLargeThreshold` upwards | `RED` |
| `stackSizeMediumThreshold` | Stack size at which the name switches to `stackNameColorMedium` | `16` |
| `stackSizeLargeThreshold` | Stack size at which the name switches to `stackNameColorLarge` | `64` |
| `enableStackBreeding` | Feeding a stacked animal breeds its members in pairs into a baby-stack (feeding a baby-stack speeds its growth) | `true` |
| `breedOnePerClick` | If `true`, each click feeds a single member (click once per animal); if `false`, one click feeds as many members as the food in hand allows | `false` |
| `enableAnimalBabyStacking` | Allow loose farm-animal babies (cows, sheep, …) to stack, matched by age | `true` |
| `enableHostileBabyStacking` | Allow loose hostile/other babies (e.g. baby zombies) to stack | `true` |
| `compactDrops` | Merge a stacked mob's death drops into as few full item stacks as possible (fewer item entities = less farm lag) | `true` |
| `compactExperience` | Merge a stacked mob's death experience into a single orb instead of many small ones | `true` |
| `maxMobStackSize` | Maximum number of mobs in a single stack | `16` |
| `stackRadius` | Radius within which mobs attempt to stack | `6.0` |
| `stackScanInterval` | How often (in ticks) a mob re-checks for a nearby stack to join, so mobs that never move still merge. `0` only merges when a mob crosses a block boundary | `20` |
| `enableSeparator` | Toggles use of separator item for stack splitting | `false` |
| `consumeSeparator` | Determines if separator item is consumed on use | `true` |
| `separatorItem` | Specifies the item used as a separator | `"minecraft:diamond"` |
| `ignoredEntities` | List of entities excluded from stacking | `["minecraft:ender_dragon", "minecraft:vex"]` |
| `ignoredMods` | List of mod IDs whose entities are excluded from stacking | `["corpse"]` |
| `stackMode` | Where new stacks may form: `OFF`, `REGIONS`, `PLAYERS` or `EVERYWHERE` | `OFF` |
| `playerStackRadius` | In `PLAYERS` mode, mobs within this many blocks of any player may stack | `12.0` |
| `regions` | Allow/deny cuboids that gate stacking (see Region & Mode Management) | `[]` |

## Commands

All commands require operator permissions (level 2) and are prefixed with `/mobstacker`.

### Configuration Commands

Every scalar setting is changed through one small, consistent set of subcommands with full
tab-completion — no more hunting through nested menus. Type `/mobstacker` for a grouped
overview of the current values, `/mobstacker help` for the command list, or
`/mobstacker help <category>` to see a category's settings with their values, defaults and
descriptions.

```bash
# Grouped overview of every current setting
/mobstacker

# Command list + setting categories (stacking, combat, feedback, display, breeding, drops, separator, mobcaps)
/mobstacker help [category]

# Inspect one setting (current value, default, description)
/mobstacker get <setting>

# Change any setting — the value is validated for that setting's type
/mobstacker set <setting> <value>

# Flip a boolean setting
/mobstacker toggle <setting>

# Restore one setting — or every setting — to its default
/mobstacker reset <setting>
/mobstacker reset all
```

> 🧪 There is also a developer command, `/mobstacker selftest`, that automatically round-trips
> every setting against a sandbox config (your world config is untouched). It is **not** available
> in the normal build — it only registers when the game/server is launched with the JVM flag
> `-Dmobstacker.selftest=true`, so it never shows up for regular users.

`<setting>` is any of the config keys from the table above (e.g. `killWholeStackOnDeath`,
`damageOverflow`, `maxStackSize`, `stackRadius`, `stackMode`, `separatorItem`, the `stackKill*`
feedback toggles, the breeding toggles, `compactDrops`/`compactExperience`, and the `*MobCap`
values). Tab-completion suggests every setting name, and then the valid values for the one you
picked (e.g. `true`/`false`, the `stackMode` options, or item ids for `separatorItem`).

```bash
# Examples
/mobstacker set maxStackSize 32
/mobstacker toggle damageOverflow
/mobstacker set stackMode everywhere
/mobstacker set separatorItem minecraft:diamond
/mobstacker get compactExperience
```

### Config GUI

Prefer clicking to typing? Open the in-game config screen:

- **Key binding:** bind *“Open Config GUI”* (category *MobStacker: Restacked*) in **Options → Controls**
  (unbound by default), then press it in-game.
- **Command:** `/mobstackerconfig` (client-side).

The screen is driven by the same settings as the commands — flip booleans, cycle `stackMode`, and
type numbers / item ids, browsing one category at a time. It works in **singleplayer / on the LAN
host**, and on a **remote server that has the mod**: there it shows the server's live config and, if
you are an **operator**, saves your edits to the server (non-operators see it read-only). On a server
without the mod it is informational only, so keep using the commands there. The networking uses
optional channels, so vanilla clients are unaffected. Requires **Fabric API** on the client.

### Entity and Mod Management

```bash
# Never stack a specific entity type
/mobstacker ignore entity add <entity_id>
/mobstacker ignore entity remove <entity_id>
/mobstacker ignore entity list

# Never stack any entity from a given mod
/mobstacker ignore mod add <mod_id>
/mobstacker ignore mod remove <mod_id>
/mobstacker ignore mod list

# Force the LIVE stack count of a targeted mob (admin/debug — needs an entity selector).
# This is different from `set maxStackSize`, which is the global upper limit stacks may grow to.
/mobstacker stacksize <target> <size>

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
| `off` *(default)* | No new stacks ever form. Stacking ships off — pick another mode to enable it. |
| `regions` | Stacking only forms inside an `allow` region, and never inside a `deny` region. |
| `players` | Stacking only forms near a player (within `playerStackRadius` blocks), and never inside a `deny` region. |
| `everywhere` | Stacking forms everywhere, except inside a `deny` region. |

```bash
# Set the global stacking mode
/mobstacker set stackMode <off|regions|players|everywhere>
# In players mode, tune how far from a player mobs may stack (blocks)
/mobstacker set playerStackRadius 12
```

> **Getting started:** because stacking is `off` by default, a fresh install stacks nothing until
> you enable a mode. Adding an `allow` region while the mode is still `off` **auto-switches** it to
> `regions` so the region works right away.

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

# Everything about one region: bounds, priority and the settings it overrides
/mobstacker region show <name>
```

#### Settings per region

A region can carry its own value for almost every setting, so one farm can behave
differently from another without touching the rest of the world. Anything a region does
not mention simply follows the global config, so you only ever state the differences.

```bash
# Give a region its own value for a setting
/mobstacker region set <name> <setting> <value>
/mobstacker region set cowfarm maxStackSize 64
/mobstacker region set cowfarm stackNameColor GREEN

# Drop an override, so the setting follows the global config again
/mobstacker region unset cowfarm maxStackSize

# Decide which region wins where two overlap (higher first; ties go to the smaller one)
/mobstacker region priority <name> <number>
```

In the config GUI the same thing lives behind the **Regions…** button: pick a region,
walk the categories, and every row shows its value with a **gold** label when the region
sets it itself and a **grey** one when it follows the global config — the small `↺`
button beside a row drops the override again. A region only ever stores what it actually
changes, so a value equal to the global one is not an override — whether you picked it in
the region or later changed the global config to match — and gold therefore always means
"different in here". Hovering a row says the same thing in words, along with the global
value, and in red when a setting cannot be edited (the one it depends on is off here, or
another one forces it on). The region's overlap priority is editable right there too.

The only settings that stay global are `stackMode` and `playerStackRadius` — they decide
where the region system applies at all — and the seven `mobcaps`, which are world-level
spawn limits rather than a property of a place. `deny` still beats everything, in every
mode, whatever a region's own settings say.

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

### Vanilla-style sweeping (`sweepingEdgePerMob`)

Turn this on and the sweep stops being a single bonus on the hit: the mob you actually
struck takes the full hit, and **every other mob in the stack takes its own sweep hit**
— `1 + attack damage x (level / (level + 1))`, the vanilla formula — just as a real
sweep would have hit them if they were standing loose. Because the attack damage in that
formula is the damage *after* Sharpness, Smite and Bane of Arthropods, and every mob in a
stack is the same type, the right enchantment bonus is applied automatically: Smite
scales the sweep against a stack of zombies, Bane of Arthropods against spiders, and
Sharpness against everything.

The sweep leaves the other mobs wounded rather than untouched, and those wounds add up
from swing to swing — so a stack of cows takes a couple of swings to fall apart, exactly
as a herd standing loose would under the same sweeps.

Some stacks have no separate mobs to wound: `stackHealth` pools their health into one bar,
and `killWholeStackOnDeath` makes them die together. There, every other member's sweep is
added to the single hit instead. The total health it costs the stack is the same, so a
pooled stack falls in the same number of swings as an unpooled one.

`sweepingEdgeSingleHit` asks for that concentrated hit even when the members *are*
separate. Ten mobs with Sweeping Edge III means the top mob takes the weapon's damage plus
nine sweeps' worth in one blow, which `damageOverflow` then carries down the stack — so a
swing kills several mobs outright instead of leaving all of them wounded. It takes
`damageOverflow` to reach past the mob you struck; without it the swing just kills that one
mob, which is the point of putting everything in one place. `sweepingEdgeMaxKills` caps how
far a single swing reaches either way.

One consequence is worth knowing before enabling it: since the whole stack stands in one
spot, a sweep strong enough to kill a single healthy mob of that type kills **all** of
them in one swing. That is exactly what vanilla would do to the same mobs standing side
by side, but it is a big jump in power — use `sweepingEdgeMaxKills` to cap it, and
`sweepingEdgeVanillaConditions` if you want the bonus only on the kind of swing vanilla
actually sweeps with.

> 💡 All of these are genuinely independent toggles. `damageOverflow` and the
> `sweepingEdge*` family answer different questions: overflow decides whether the leftover
> of a **killing blow** carries onto the mobs below, while Sweeping Edge decides how much
> damage a sweep deals at all. Sweeping Edge always adds its damage, and how far that
> reaches is up to the stack — a pooled health bar spends all of it, overflow carries it
> down, and with neither it fells the mob in front of you. Per-mob sweeping still wounds
> the members with overflow off, so turning overflow off does **not** mean one kill per
> hit unless you also turn the sweeping off. Disable `sweepingEdgeOverflow` alone for
> that.
> `killWholeStackOnDeath` takes priority: with it enabled, any kill already wipes the
> whole stack, so overflow does not apply — and `stackHealth` forces it on, because a
> pooled health bar only makes sense if the whole stack goes down with it. The four
> `sweepingEdge*` tuning options above only apply while `sweepingEdgeOverflow` is on —
> they refuse to be turned on before then, the config GUI greys them out until it is, and
> they **read as off** meanwhile, so a switch never sits on `ON` while doing nothing. The
> same goes down the chain: `sweepingEdgeSingleHit` follows `sweepingEdgePerMob`, which
> follows `sweepingEdgeOverflow`. Nothing is erased — turn the master setting back on and
> everything you had set is there again.
> Both kinds of dependency are judged by the region's own value inside a region, so a
> region may enable `sweepingEdgeOverflow` for itself and use everything built on it while
> the rest of the world does without, and a region that turns `stackHealth` off is not
> bound by the world's forced `killWholeStackOnDeath`.

When a hit kills mobs from a stack the mod can show feedback three ways, each with its own
independent toggle:

- **Action bar** (`stackKillActionBar`, default on): a line above the killer's hotbar
  showing how many were killed by that hit and how many remain (e.g. `Killed 3× Cow • 9 left`).
- **Particle pop** (`stackKillParticles`, default on): a burst of particles at the mob,
  growing in amount and height with the number killed. It spawns no extra entities.
### Telling stacks apart

The name above a stack is drawn in `stackNameColor` — any of the sixteen Minecraft colours — so
stacks can be made to stand out from ordinary mobs, and (once you put them in different regions)
from each other. Turn on `stackNameColorBySize` and the colour steps up with the stack: the base
colour below `stackSizeMediumThreshold`, `stackNameColorMedium` from there, and
`stackNameColorLarge` from `stackSizeLargeThreshold` upwards — a 64-stack is then recognisable
across the farm without counting. A mob you named with a name tag keeps the colour you gave it.
Colour changes reach stacks that already exist within a second (see `stackScanInterval`).

- **Floating hologram** (`stackKillHologram`, default on): a short-lived `-N` text that
  drifts up above the mob showing how many that hit killed. This is the only feedback
  channel that spawns an entity — an invisible marker armor stand removed after ~1 second.
  It is never written to the world save, so a server restart or a chunk unload in the
  middle of its short life can no longer leave one floating behind.

### Breeding & Baby Stacking

Stacked animals can be bred without unstacking them, and babies stack too.

- **Feed a stacked adult** its breeding food and it breeds its members in pairs. The cost is
  fair — **one food item per member** (a stack of 16 cows fed 16 wheat produces 8 babies), and
  every partial feed is remembered so nothing is wasted. Bred members go on the usual ~5-minute
  breeding cooldown, while the rest of the stack can still be bred right away.
- **Feeding speed** is configurable via `breedOnePerClick`: by default one click feeds as many
  members as the food in your hand allows; enable it to feed **one member per click** instead
  (click once per animal, just like feeding them individually).
- **Babies arrive as a single baby-stack** (e.g. a young `Cow x8`) instead of a swarm of loose
  babies — better for performance and consistent with the rest of the mod. Repeated breeding
  tops up the existing, slightly older baby-stack nearby before spawning a new one.
- **The baby-stack grows up as one unit** (it shares a single age) and then merges into the adult
  stack automatically once grown.
- **Feeding a baby-stack** speeds up its growth, scaled fairly to the stack size (one food item
  per baby rather than one for the whole stack).
- **Loose babies stack too.** Farm-animal babies only stack with others of a **similar age** (so
  they don't grow up early); non-ageable babies such as baby zombies (which never grow up in
  vanilla) simply stack together. Each category has its own toggle
  (`enableAnimalBabyStacking` / `enableHostileBabyStacking`).

> 💡 Breeding targets the common farm animals (cow, pig, sheep, chicken, mooshroom, rabbit,
> goat, …). Tameable mobs with their own special right-click behaviour (wolf, cat, horse) may
> fall back to vanilla breeding for now.

### Drop & Experience Compaction

Killing a big stack normally spawns a separate item entity for **every** drop of **every** mob
(a 40-cow stack can litter the ground with dozens of leather and beef entities) and a swarm of
tiny experience orbs, which is a real source of lag on large farms. With `compactDrops` on
(default), a stacked mob's death drops are captured and re-emitted **merged into as few full item
stacks as possible**, dropped together at the mob's position. With `compactExperience` on
(default), all of that death's experience is combined into a **single orb**.

- Only **stacked** mobs are affected — a normal single mob keeps the vanilla behaviour.
- It **never creates or destroys anything**: the exact same loot and total XP is dropped, just
  packed into fewer entities. Different items, and items with different enchantments/NBT, are kept
  apart correctly.
- Works with every kill path (normal kills, `killWholeStackOnDeath`, and damage overflow), and the
  two toggles are independent.

```bash
/mobstacker toggle compactDrops
/mobstacker toggle compactExperience
```

### Mob Cap Management

The vanilla per-category spawn caps are ordinary settings too (category `mobcaps`), changed
with the same `set` command:

```bash
# Set a category's spawn cap (0–128)
/mobstacker set monsterMobCap <value>

# List every cap with its current value and default
/mobstacker help mobcaps
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
