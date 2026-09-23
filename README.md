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

**MobStacker** merges nearby mobs of the same kind into one entity, so a farm with four hundred cows
costs the server four cows' worth of ticking. Loot, experience, health and behaviour are all
preserved — the mobs are still there, they just travel together.

> 🍴 **About this fork:** *MobStacker: Restacked* is an **actively-developed, independent fork** of
> [MobStacker](https://github.com/frikinjay/mob-stacker) by **frikinjay** (the original author),
> maintained by **michalekjarmark & davidex** under **LGPL v3**. We develop it independently of the
> original author, who no longer maintains the mod, keeping a **Fabric 1.20.1** line for our server.

> 📦 **Dependencies:** only **[Fabric API](https://modrinth.com/mod/fabric-api)** (used by the config
> GUI). Stacking itself is server-side — a client only needs the mod to open the GUI.
> *Let Me Despawn* is an optional companion, never a requirement.

> 📝 **Changelog:** see [CHANGELOG.md](CHANGELOG.md).

## ✨ What this fork adds

- 🗺️ **Where-to-stack modes** — `OFF` (default), `EVERYWHERE`, `REGIONS` (allow/deny cuboids) or
  `PLAYERS` (only near players). Stacking ships **off** until you opt in.
- 🧩 **Settings per region** — almost every setting can differ inside a region, so a cow farm and a
  grinder in the same world behave differently. Regions are created, redrawn and deleted from the
  GUI or the commands, and can be **drawn in the world** in a colour of their own.
- 💥 **Damage overflow** — one big hit kills several mobs in a stack and drops loot/XP for each.
- ⚔️ **Sweeping Edge support** — vanilla sweep damage applied to a stack, optionally mob by mob.
- 🛡️ **Per-mob equipment** — every stacked mob keeps its own armour and weapons, and drops them when
  it is the one that dies, Looting and drop chances included.
- 🏷️ **Name tags on stacks** — rename a stack and it stays a stack; rename a single mob and it stays
  out of stacks, the way players expect. Vanilla's easter-egg names still work.
- 🐴 **Pets and mounts are left alone** — nothing you have tamed, saddled, loaded or leashed is ever
  stacked, and touching a wild herd hands you one animal out of it.
- 🎯 **Stack-kill feedback** — action bar, a scaling particle pop and a floating `-N` hologram.
- 🐣 **Stack breeding & baby stacks** — breed a stacked animal into a single baby-stack; loose babies
  stack too.
- 📦 **Drop & XP compaction** — a stack's death drops become a few full item stacks and one orb
  instead of dozens of entities.
- 🎨 **Stack colours** — colour the `Cow x16` label and the kill hologram, optionally by stack size.
- 🖥️ **In-game config GUI** — works in singleplayer, on a LAN host and on a remote server (operators
  edit, everyone else reads).
- 🧭 **Streamlined commands** — a flat `/mobstacker set|get|toggle|reset <setting>` tree with
  tab-completion.
- 🗂️ **Per-world config** — settings live in the world save folder instead of leaking between worlds.
- 🪶 **Only Fabric API required** — the old *Almanac* dependency is built in.

> 🔗 **Good companions:** [Let Me Despawn](https://www.curseforge.com/minecraft/mc-mods/let-me-despawn)
> for vanilla-style despawning, and
> [Spawncap Control Utility](https://www.curseforge.com/minecraft/mc-mods/spawncapcontrolutility) or
> [In Control!](https://www.curseforge.com/minecraft/mc-mods/in-control) for finer spawn control.

## Configuration

Config lives per world in `<world>/serverconfig/mobstacker.json`. Every setting below can be changed
with `/mobstacker set <setting> <value>` or in the config GUI, and — except where noted — can be
given its own value inside a region.

### Stacking

| Setting | Description | Default |
|---|---|---|
| `stackMode` | Where new stacks may form: `OFF`, `REGIONS`, `PLAYERS`, `EVERYWHERE`. *Global only* | `OFF` |
| `playerStackRadius` | In `PLAYERS` mode, how close to a player mobs must be. *Global only* | `12.0` |
| `maxStackSize` | Largest a stack may grow to | `16` |
| `stackRadius` | How far apart mobs can be and still merge | `6.0` |
| `stackScanInterval` | How often (ticks) a mob re-checks for a stack to join, so mobs that never move still merge. `0` = only when crossing a block boundary | `20` |
| `stackOnSpawn` | Merge a mob into a nearby stack on its first tick, so spawners, breeding and spawn eggs stack at once instead of after a scan | `true` |
| `stackEquippedMobs` | Let mobs holding or wearing items stack at all | `false` |
| `keepMemberEquipment` | Remember each member's gear so it drops when *that* mob dies. Needs `stackEquippedMobs` | `true` |
| `stackNamedMobs` | Let name-tagged mobs stack with mobs of the same name | `false` |
| `killWholeStackOnDeath` | Killing the top mob kills the whole stack | `false` |
| `stackHealth` | The stack shares one health bar, scaled to its size. Forces `killWholeStackOnDeath` on | `false` |
| `mobListMode` | `BLACKLIST` (everything stacks except the ignored lists) or `WHITELIST` (nothing stacks except the allowed lists) | `BLACKLIST` |
| `ignoredEntities` | Entity ids that never stack, in `BLACKLIST` mode | `["minecraft:ender_dragon", "minecraft:vex"]` |
| `ignoredMods` | Mod ids whose entities never stack, in `BLACKLIST` mode | `["corpse"]` |
| `allowedEntities` | The only entity ids that stack, in `WHITELIST` mode | `[]` |
| `allowedMods` | Mod ids whose entities stack, in `WHITELIST` mode | `[]` |
| `maxStackSizes` | A ceiling for one mob type, e.g. `{"minecraft:cow": 64}`. Anything unlisted follows `maxStackSize` | `{}` |
| `regions` | Allow/deny cuboids — see [Regions](#regions--modes) | `[]` |

### Combat

| Setting | Description | Default |
|---|---|---|
| `enableDamageOverflow` | Leftover damage from a lethal hit carries onto the mobs below | `true` |
| `sweepingEdgeOverflow` | Sweeping Edge adds its damage to a stack instead of doing nothing | `true` |
| `sweepingEdgePerMob` | Sweep **every** mob for its own `1 + damage × (level / (level + 1))`, like a vanilla sweep through a crowd. Needs `sweepingEdgeOverflow` | `false` |
| `sweepingEdgeSingleHit` | Put the whole sweep into the one hit and let overflow carry it down. Needs `sweepingEdgePerMob` | `false` |
| `sweepingEdgeVanillaConditions` | Only sweep when vanilla would: full swing, no crit, not sprinting, on the ground, sword in hand | `false` |
| `sweepingEdgeMaxKills` | Cap on mobs one sweep may kill per swing (`0` = none) | `0` |

### Feedback & display

| Setting | Description | Default |
|---|---|---|
| `stackKillActionBar` | Tell the killer how many died and how many remain | `true` |
| `stackKillParticles` | Particle pop at the mob, scaling with the number killed | `true` |
| `stackKillHologram` | Short-lived floating `-N` above the mob | `true` |
| `killHologramColor` | Colour of that `-N` | `RED` |
| `stackNameColor` | Colour of the `Cow x16` label (a name-tagged mob keeps its own colour) | `WHITE` |
| `stackNameColorBySize` | Step the colour up with the stack size | `false` |
| `stackNameColorMedium` / `stackNameColorLarge` | Colours above each threshold | `YELLOW` / `RED` |
| `stackSizeMediumThreshold` / `stackSizeLargeThreshold` | Where those colours take over | `16` / `64` |

### Breeding, drops and the separator

| Setting | Description | Default |
|---|---|---|
| `enableStackBreeding` | Feeding a stacked animal breeds its members in pairs | `true` |
| `stackedHarvest` | Shearing and milking a stack give one mob's worth per member; off shears one animal at a time | `true` |
| `breedOnePerClick` | Feed one member per click instead of as many as the food allows | `false` |
| `enableAnimalBabyStacking` | Let loose farm-animal babies stack, matched by age | `true` |
| `enableHostileBabyStacking` | Let loose hostile babies (baby zombies …) stack | `true` |
| `compactDrops` | Merge a stack's death drops into as few full item stacks as possible | `true` |
| `compactExperience` | Merge a stack's death experience into one orb | `true` |
| `enableSeparator` | Allow splitting a stack with an item | `false` |
| `consumeSeparator` | Consume that item on use | `true` |
| `separatorItem` | Which item splits a stack (the GUI completes item ids as you type) | `minecraft:diamond` |
| `separationCooldown` | Seconds a mob taken out of a stack for you (taming, riding, a bucket, shears, the separator) or poured from a bucket stays out of stacks; `0` lets it rejoin on the next scan | `0` |

The vanilla per-category spawn caps are settings too (category `mobcaps`, global only):
`/mobstacker set monsterMobCap <0-128>`, and `/mobstacker help mobcaps` lists them all.

## Commands

All of these need operator permission (level 2).

```bash
/mobstacker                          # grouped overview of every current value
/mobstacker help [category]          # command list, or one category's settings
/mobstacker get <setting>            # value, default and description
/mobstacker set <setting> <value>    # validated for that setting's type
/mobstacker toggle <setting>
/mobstacker reset <setting> | all
/mobstacker reload                   # re-read the JSON after editing it by hand

/mobstacker list deny|allow entity|mod add|remove|list <id>
/mobstacker maxstack <entity> <n|max|default>   # a ceiling for one mob type
/mobstacker maxstack list
/mobstacker stacksize <target> <n>   # force a LIVE mob's count (not the global limit)
```

`/mobstacker ignore …` still works as the old name for `list deny …`.

Anywhere a whole number is asked for, **`max`** means as high as that setting goes —
`/mobstacker set maxStackSize max` is 2147483647 without having to remember it. It is stored as the
number it means, so `get` always answers with something unambiguous. **`default`** works the same
way for every setting: `/mobstacker set maxStackSize default` is `reset maxStackSize`, and in the
ceilings tab's size box it removes that mob's ceiling, as `maxstack <entity> default` does.

### Which mobs may stack

Two questions, answered separately. **`mobListMode`** picks which lists are read: `BLACKLIST`
(the default, and how the mod has always worked) reads `ignoredEntities` / `ignoredMods` and stacks
everything else; `WHITELIST` reads `allowedEntities` / `allowedMods` and stacks nothing else. The two
halves keep their own lists, so flipping the mode to have a look never rewrites a list you built.

Every one of these can also be set **per region** — `/mobstacker region mobs <name> …`, or the
**Mob lists…** button on the region screen. A region that sets nothing follows the global list. A
region that sets one means that list and no other, which is the same rule every per-region setting
has followed since 1.6.0; entity lists and mod lists are inherited independently.

Because inheriting and overriding are different states, a region takes a list over explicitly:

```
/mobstacker region mobs sheep_pen allow entity override   # its own copy, seeded with the global one
/mobstacker region mobs sheep_pen allow entity add minecraft:sheep
/mobstacker region mobs sheep_pen allow entity inherit    # back to following the global list
```

Adding straight to an inherited list is refused rather than silently turned into a one-entry
override — the ten entries you were looking at would have gone. Handing a region's list back to the
global one asks once before it goes, for the same reason.

In the **Mob lists…** screen the entry box completes ids as you type, the way the command line does:
**Tab**, **Enter** or a click takes the highlighted one, the arrow keys walk the list and **Esc**
closes it without closing the screen. Enter leaves an id that is already whole alone, so
`minecraft:pig` is not turned into the `minecraft:piglin` listed under it. Entity tabs suggest entity ids, mod tabs suggest the namespaces that
actually have mobs in them, and ids already on the list are left out.

A `minecraft:` id that names no mob is a typo and is refused. A **modded** id is accepted whether
the mod is installed or not — a list has to survive its mod being away for a week — but it is shown
`(not loaded)` and says so when you add it, so an entry that currently means nothing never passes
for one that is working.

Tab-completion suggests every setting name and then the valid values for the one you picked.

> 🧪 `/mobstacker selftest` round-trips every setting against a sandbox config. It only registers
> when the game is launched with `-Dmobstacker.selftest=true`, so regular users never see it.

> 💡 An entity tagged `{StackData: {CanStack:0b}}` never stacks.

### Config GUI

Bind *"Open Config GUI"* (category *MobStacker: Restacked*) in **Options → Controls**, or run
`/mobstackerconfig`. The screen is driven by the same registry as the commands: booleans flip,
`stackMode` cycles, numbers and item ids are typed and validated, one category at a time. On a
**remote server with the mod** it shows the server's live config and saves operators' edits;
non-operators see it read-only. In **singleplayer** (and on a LAN host) it edits your own world's
config whether cheats are on or not: it is the mod's settings screen, and the file it writes sits in
your own save folder anyway. The `/mobstacker` commands follow vanilla's rule instead and need cheats
(operator level 2). The networking uses optional channels, so vanilla clients are never sent
anything. Needs **Fabric API** on the client.

## Regions & modes

The mode decides *where* new stacks may form. It never affects mobs that are already stacked.

| Mode | Behaviour |
|---|---|
| `off` *(default)* | Nothing stacks. Pick another mode to enable stacking. |
| `regions` | Only inside an `allow` region. |
| `players` | Only within `playerStackRadius` of a player. |
| `everywhere` | Everywhere. |

A `deny` region always wins, in every mode. Regions are axis-aligned cuboids tied to the dimension
you run the command in; corners take absolute or `~` coordinates, and custom shapes are approximated
with several cuboids.

```bash
/mobstacker set stackMode <off|regions|players|everywhere>

/mobstacker region add <name> <allow|deny> <x1 y1 z1> <x2 y2 z2>
/mobstacker region add cowfarm allow ~-15 ~-3 ~-15 ~15 ~5 ~15   # around where you stand
/mobstacker region bounds <name> <x1 y1 z1> <x2 y2 z2>          # move or resize, keeping its settings
/mobstacker region type <name> <allow|deny>
/mobstacker region color <name> <colour|auto>                   # the colour its box is drawn in
/mobstacker region mobs <name> <deny|allow> <entity|mod> <add|remove|list|override|inherit>
/mobstacker region maxstack <name> <entity> <n|max|default>      # a ceiling just for this region
/mobstacker region rename <name> <newname>                      # keeps its area, settings and colour
/mobstacker list <deny|allow> <entity|mod> <add|remove|list>    # which mobs may stack at all
/mobstacker maxstack <entity> <n|max|default> | maxstack list   # a ceiling for one mob type
/mobstacker region remove <name>
/mobstacker region list
/mobstacker region show <name>                                  # bounds, priority and its overrides
```

> **Getting started:** stacking is `off` on a fresh install. Adding an `allow` region while it is
> still off **switches it to `regions`** so the region works right away.

Regions can also be **drawn rather than typed**: open the area editor, press **Pick in world…**, and
right-click two blocks. While you are picking, the box from the first corner to the block under your
crosshair is drawn live, so the reach is visible before anything is saved. Sneak and click to cancel.
Nothing is saved until you press Save — the picker fills in the same six numbers you could have typed.
A region remembers its corners the way you gave them: the first block you click is corner 1, the
second is corner 2, and that is what the editor and `region show` give back.

### Settings per region

A region can carry its own value for almost every setting; anything it does not mention follows the
global config, so you only ever state the differences.

```bash
/mobstacker region set cowfarm maxStackSize 64
/mobstacker region unset cowfarm maxStackSize      # back to the global value
/mobstacker region priority <name> <n>             # higher wins an overlap; ties go to the smaller region
```

In the GUI the same lives behind **Regions…**. A row's label is **gold** when the region sets that
value itself and **grey** when it follows the world, and the `↺` beside it drops the override.
A value equal to the global one is never stored as an override, so gold always means "different in
here". Hovering says the same in words, and in red when a setting cannot be edited here. **New
region…** and **Edit area…** open an editor for a region's kind, dimension and both corners, with a
**Here** button that fills a corner from where you are standing; the same screen deletes a region,
behind a second click. Redrawing a region keeps its settings and its priority.

Only `stackMode`, `playerStackRadius` and the mob caps stay global — the first two decide where the
region system applies at all, and the caps are world-level spawn limits rather than a property of a
place.

## Combat

By default a stack fights like a single mob: one hit removes the top mob and a fresh one takes its
place. **Damage overflow** changes that, so a hard-hitting blow is not wasted.

- Leftover damage from a lethal hit carries onto the mobs below, and every mob it kills drops loot,
  experience and kill score.
- A mob the overflow does not finish is left **wounded**, not healed.
- It acts on the final post-armor damage, so it works for **any** source — melee, Harming potions,
  magic, lava — with resistance and protection already applied.

Sharpness, Smite, Bane of Arthropods and Fire Aspect feed overflow for free (a bigger hit kills more
mobs), and Looting is applied per mob killed.

**Sweeping Edge** would normally do nothing here, because it damages mobs *around* the target and a
stack is one entity. Instead the vanilla sweep (`1 + attack damage × level / (level + 1)`) is folded
back into the hit:

| Sweeping Edge | Sweep damage |
|---|---|
| I | `1.0 + 0.50 × attack damage` |
| II | `1.0 + 0.67 × attack damage` |
| III | `1.0 + 0.75 × attack damage` |

`sweepingEdgePerMob` goes further: the mob you struck takes the full hit and **every other mob takes
its own sweep**, wounding them, with the wounds adding up from swing to swing — so a stack falls
apart over a couple of swings, exactly as a loose herd would. Since the attack damage in that formula
is the damage *after* enchantments, and a stack is one mob type, Smite scales it against zombies and
Bane of Arthropods against spiders automatically. Where there are no separate mobs to wound
(`stackHealth`, `killWholeStackOnDeath`) every member's sweep goes into the single hit instead, which
costs the stack the same total health. `sweepingEdgeSingleHit` asks for that concentrated hit even
when the members are separate, so one swing kills several mobs outright instead of wounding them all;
it needs `damageOverflow` to reach past the mob you struck.

> ⚠️ Because the whole stack stands in one spot, a sweep strong enough to kill a healthy mob of that
> type kills **all** of them in one swing — what vanilla would do to the same mobs side by side, but
> a big jump in power. `sweepingEdgeMaxKills` caps it, and `sweepingEdgeVanillaConditions` restricts
> the bonus to swings vanilla would actually sweep with.

> 💡 **`damageOverflow` and the `sweepingEdge*` family are independent.** Overflow decides whether a
> **killing blow's** leftover carries down; sweeping decides how much a sweep deals at all. Per-mob
> sweeping still wounds the members with overflow off — turn off `sweepingEdgeOverflow` if you want
> one kill per hit. `killWholeStackOnDeath` takes priority over overflow, and `stackHealth` forces it
> on, because a pooled health bar only makes sense if the whole stack goes down with it.
>
> A setting that needs another one (`sweepingEdgeSingleHit` → `sweepingEdgePerMob` →
> `sweepingEdgeOverflow`) is greyed out in the GUI, refuses to be switched on and **reads as off**
> until then, so a switch never sits on `ON` while doing nothing. Nothing is erased — switch the
> master back on and your settings are there again. Both kinds of dependency are judged by a
> **region's own** values inside a region.

## Equipment

Mobs that hold or wear something stay out of stacks by default (`stackEquippedMobs`), because their
gear is per-mob and a stack shows one mob. Turn it on and, with `keepMemberEquipment` (on by
default), the stack **remembers what every member wears and holds** — item by item, with the drop
chance of each piece.

When a member is killed, its own gear is put back on the entity just before its death loot is rolled,
so vanilla does the rest: the drop chances apply, **Looting** raises them, and dropped armour is
damaged the way vanilla damages it. That holds under every death mode, and because the items are
stored exactly as the game stores them, **other mods' items and any enchantment work without the mod
knowing anything about them**. The remainder of a killed stack, and a mob pulled out with the
separator item, take the next member's gear with them.

## Pets & mounts

**Anything a player has put something into stays out of stacks**, with no setting to change that:
tamed or owned mobs, saddled ones, horse armour, a donkey's or llama's chest, a leashed mob, anything
riding or being ridden, a trusting ocelot and a fox that knows you. A horse's saddle and chest live in an inventory of its own rather
than in its equipment slots, so a merge used to wipe them along with the taming — hence the hard rule.

Wild herds still stack, which is where the performance is anyway, and foals are born untamed, so a
breeding pen keeps stacking everything it produces.

**Right-clicking a stacked mount hands you one animal out of the stack**, and everything you then do
— taming, a saddle, armour, feeding, climbing on — applies to that one. It walks back into the herd
afterwards unless what you did was to keep it. Horses, donkeys, mules, llamas, camels and skeleton
and zombie horses.

**Every horse in a stack keeps its own speed, jump and health.** The horse that comes out is the
next one in line, with its own numbers, and when the top horse dies the next one takes over with
its own. A good horse merged into a herd is not lost, and a herd merged under a good horse does not
become a herd of good horses.

**Taming works the same way**: offer a bone to a stack of wolves, cod to cats or seeds to parrots and
one animal steps out to be tamed rather than the whole pack at once. Only while you are holding the
right item — right-clicking a pack with an empty hand does nothing, exactly as in vanilla.

## Seeing a region

Give a region a colour and switch its box on, and its bounds are drawn in the world — no more walking
to a corner to read coordinates off F3.

| | |
|---|---|
| Colour a region | `/mobstacker region color <name> <colour\|auto>`, or the button beside its priority in the region screen |
| Show or hide one | the **Box** button in the region screen |
| Show or hide everything | the **All** button, or a key binding (unbound by default, set it in Controls) |
| How it looks | the **Style** button — `wireframe`, `filled` or `both` *(the default)* |
| Through walls | the **X-ray** button, or a key binding (unbound by default) — off by default, and only where the server allows it (below) |

`auto` means no colour was chosen, and the box is drawn **green** for an allow region and **red** for
a deny one. The colour belongs to the region, so everyone sees the same one; *whether* a box is drawn
is each player's own business, kept client-side and never sent anywhere.

Boxes need the mod on the client — without it there is simply nothing to see. They are hidden by
terrain, like everything else in the world, until you switch **X-ray** on: then they are drawn
through walls and terrain, so a region can be seen from anywhere around it — the box you are
picking with **Pick in world…** too. Nothing in the world covers them then — not clouds, water or
mobs either; only your hand and the HUD. Like the style, X-ray is one choice for every world.

> 🔒 **X-ray is the server's to allow.** A box seen through a mountain shows a little of what is behind
> it, so it only works on a server started with the Java argument `-Dmobstacker.xray=true`. In
> singleplayer (and when you host a LAN game) your game *is* the server, so add the argument in your
> launcher's Java arguments. Elsewhere the button is greyed out and tells you why; a client's own flag
> does not count on somebody else's server.

> 🔎 **Boxes not showing in a modpack?** Start the game with the Java argument
> `-Dmobstacker.overlayDebug=true`: every few seconds the log (`logs/latest.log`) gets a line starting
> `[MobStacker overlay debug]` saying what the boxes found when they were drawn. That line is what a
> bug report about it needs.

> 🎨 **Shader packs** (Iris) draw the boxes their own way, and some of them barely at all — against the
> sky in particular: with a pack loaded, the pack decides how every line and translucent face in the
> world is lit and blended, the boxes included. In testing, Complementary Reimagined showed them nearly
> as intended and Sildur's Vibrant hardly at all.

## Names & name tags

A stack is labelled `Cow x16`, drawn in `stackNameColor`. Turn on `stackNameColorBySize` and the
colour steps up with the stack — the base colour, then `stackNameColorMedium` and
`stackNameColorLarge` at thresholds you set — so a 64-stack is recognisable across the farm. Colour
changes reach existing stacks within a second (see `stackScanInterval`).

**Name tags say what you mean.** Rename a stack and the name becomes its label — `Bella x16`, in the
colour you gave it — and **the stack goes on accepting mobs**. Rename a *single* mob and it stays out
of stacks, which is how players protect a pet; `stackNamedMobs` opts those in too, with mobs of the
same name only. The count is only ever appended to the name you typed, so a cow named `Cow x5` keeps
that name, and a stack keeps its name through kills and through conversions (zombie → drowned).
A mob named some other way (`/data`, a dispenser) is treated as protected.

Vanilla's name easter eggs survive the label: `Dinnerbone` and `Grumm` turn a stack upside down,
`jeb_` makes a stack of sheep cycle the dye colours, `Toast` gives rabbits the memorial skin. Those
are rendering, so they need the mod on the client.

## Breeding & baby stacks

- **Feed a stacked adult** and it breeds its members in pairs, at a fair **one food item per member**
  (16 cows fed 16 wheat give 8 babies). Partial feeds are remembered, and bred members go on the
  usual ~5-minute cooldown while the rest can still be bred. Animals vanilla would not let breed —
  an untamed wolf, for one — are not bred here either.
- **Babies arrive as one baby-stack** (a young `Cow x8`), grow up as a unit and then merge into the
  adult stack. Feeding a baby-stack speeds its growth, scaled to its size.
- **Shearing and milking scale with the stack** (`stackedHarvest`) — a stack of 16 sheep gives 16
  sheep's worth of wool for 16 points of shear durability, and 16 cows fill as many buckets as you
  brought. Turn it off and a stack gives what a single mob would: shears then take one animal out of
  the stack and shear that one, so the rest keep their wool for the next click. A dispenser with
  shears does exactly what a player's shears do, either way.
- **Loose babies stack too** — farm animals matched by age, and non-ageable babies such as baby
  zombies simply together (`enableAnimalBabyStacking` / `enableHostileBabyStacking`).
- `breedOnePerClick` feeds one member per click instead of as many as the food in hand allows.

> 💡 Tameable mobs with their own right-click behaviour (wolf, cat, horse) still fall back to vanilla
> breeding.

## Drop & experience compaction

Killing a big stack normally litters the ground with an item entity per drop per mob and a swarm of
tiny orbs. With `compactDrops` and `compactExperience` (both on) a stack's death drops are re-emitted
**merged into as few full item stacks as possible** and its experience into a **single orb**.

Nothing is created or destroyed — the same loot and the same total XP, in fewer entities. Items that
differ in enchantments or NBT are kept apart, only *stacked* mobs are affected, and it works with
every kill path.

## Notes

- 🗂️ Config is per world, in `<world>/serverconfig/mobstacker.json`. Upgrading from a pre-1.4 version?
  Copy your old `config/mobstacker.json` there.
- 👑 Boss entities keep their custom names and health bars.
- 🔌 An API is available for custom merging conditions, death handlers and entity data modifiers.
- 🐑🐷🧟 Works with animals, monsters and NPCs alike.
- 🎨 Mobs only stack with mobs that look and behave the same: wool colour and shearing for sheep,
  markings for horses, genes for pandas, a charged creeper, a screaming goat, and so on. Jobless
  villagers stack; employed ones do not.

## Credits & License

- **Original author:** [frikinjay](https://github.com/frikinjay) — creator of MobStacker.
- **This fork (*MobStacker: Restacked*):** maintained by **michalekjarmark & davidex**, developed
  independently of the original author.
- **License:** [LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.html). This fork honors the original
  license and keeps the original author's attribution.

---

*Report issues on the [fork's issue tracker](https://github.com/michalekjarmark/mob-stacker/issues).*
