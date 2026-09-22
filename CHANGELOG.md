# Changelog

All notable changes to **MobStacker: Restacked** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
via the `mod_version` in `gradle.properties`. This is an independently-developed fork of
[MobStacker](https://github.com/frikinjay/mob-stacker) by frikinjay, under LGPL v3.

## [1.9.0] - unreleased
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
  **completes ids as you type**, the way the command line does: Tab or a click takes the highlighted
  one, the arrows walk the list, Esc closes it. Handing a region's list back to the global one asks
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

### Changed
- `/mobstacker ignore <entity|mod> …` is now `/mobstacker list deny <entity|mod> …`, with
  `list allow` for the whitelist half and `region mobs <name> …` for a region's own. **The old
  `ignore` spelling still works** and reaches exactly the same code.
- The settings registry is now **45 settings, 36 of them overridable per region**.
- **A list will not take a vanilla mob that does not exist.** `minecraft:cwo` is a typo and nothing
  will ever arrive to make it mean something, so it is refused outright. A *modded* id is still
  accepted whether the mod is installed or not — a list has to survive its mod being away for a
  week — but it is marked `(not loaded)` in the editor and says so in chat, so an entry that means
  nothing never looks like one that is working. The per-type stack ceilings are judged the same way.
- **The box colour button on the region screen is labelled**, and says what "auto" means on hover.

### Fixed

> **Where these come from.** 1.7.0 and 1.8.0 both shipped without a test pass, so most of what is
> fixed below is not a 1.9.0 regression — it is the first time anybody has played those two versions
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
  next click peeled off a fresh one and nothing was ever tamed. It now stays out of stacks for a
  while, and every further interaction with it renews that. A horse with any taming progress at all
  — vanilla calls it temper — stays out for good, the way a tamed one does.
- **Bucketing a stacked fish takes one fish** *(as old as fish stacking at all)*. It used to take
  the whole shoal: everything but the name went into the bucket, and one fish came back out of it.
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
- **The X/Y/Z header in the area editor no longer sits on the dimension button** *(1.7.0)*.
- **A region's wireframe is visible through another region's fill** *(1.8.0)*, and through its own
  near face: the edges are drawn before the faces, which then do not hide them. Drawing boxes
  through *walls* is a separate job and still to come.
- **`keepMemberEquipment` no longer reads `ON` while greyed out** *(the greying is 1.6.0; 1.7.0's
  setting is the first one it got wrong, being the first whose default is on)*. A setting that is
  doing nothing reads as off, whichever way its own default points — and can always be switched
  off, which for a setting whose default was on was being refused.
- **`/mobstacker set killWholeStackOnDeath false` says it is forced on** *(1.6.0)* while
  `stackHealth` holds it there, instead of answering "it is already false" because `false` is what
  sits in the file. This was the one failure `/mobstacker selftest` had been reporting.
- The self-test now covers everything above that can be checked without a world: settings held
  inert by another, what a list will accept, and creating a region versus redrawing one.

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
