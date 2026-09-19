package com.frikinjay.mobstacker.config;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single registry of every scalar setting the mod exposes. Both the command layer
 * ({@code /mobstacker set|get|toggle|reset}, the overview and {@code help}) and — later — the
 * config GUI iterate over this list, so a setting is declared exactly once here and shows up
 * everywhere automatically.
 * <p>
 * Getter/setter lambdas read {@link MobStacker#config} lazily (per call), which is important
 * because the active config instance is swapped per world (see {@code MobStacker.loadWorldConfig}).
 * Regions and the ignore lists are collection-shaped and keep their own dedicated commands.
 */
public final class MobStackerSettings {
    private static final List<ConfigOption> OPTIONS = new ArrayList<>();
    // Settings that only ever make sense globally. stackMode and playerStackRadius decide where the
    // region system applies at all, so letting a region override them would be circular, and the mob
    // caps are world-level spawn limits rather than a property of a place. Everything else can be
    // given a different value inside a region.
    private static final java.util.Set<String> GLOBAL_ONLY = java.util.Set.of(
            "stackMode", "playerStackRadius",
            "monsterMobCap", "creatureMobCap", "ambientMobCap", "axolotlsMobCap",
            "undergroundWaterCreatureMobCap", "waterCreatureMobCap", "waterAmbientMobCap");
    private static final Map<String, ConfigOption> BY_ID = new LinkedHashMap<>();

    static {
        // --- Stacking ---
        register(ConfigOption.ofEnum("stackMode", Category.STACKING,
                "Where stacking is allowed: OFF, REGIONS (only inside ALLOW regions), PLAYERS (only near a player), or EVERYWHERE.",
                StackMode.class, () -> MobStacker.config.getStackMode(), v -> MobStacker.config.setStackMode(v), StackMode.OFF));
        register(ConfigOption.ofInt("maxStackSize", Category.STACKING,
                "The largest a stack is allowed to grow to. A mob type given its own ceiling ignores this.",
                1, 100000, () -> MobStacker.config.getMaxMobStackSize(), v -> MobStacker.config.setMaxMobStackSize(v), 16));
        register(ConfigOption.ofBool("stackOnSpawn", Category.STACKING,
                "Merge a mob into a nearby stack on its first tick, instead of waiting for it to move "
                        + "or for the next scan. Covers spawners, breeding and spawn eggs.",
                () -> MobStacker.config.getStackOnSpawn(), v -> MobStacker.config.setStackOnSpawn(v), true));
        register(ConfigOption.ofEnum("mobListMode", Category.STACKING,
                "Which mob list decides: BLACKLIST (everything stacks except the ignored lists) or "
                        + "WHITELIST (nothing stacks except the allowed lists).",
                MobListMode.class, () -> MobStacker.config.getMobListMode(),
                v -> MobStacker.config.setMobListMode(v), MobListMode.BLACKLIST));
        register(ConfigOption.ofDouble("stackRadius", Category.STACKING,
                "How far apart (in blocks) mobs can be and still merge into the same stack.",
                0.1, 42000.0, () -> MobStacker.config.getStackRadius(), v -> MobStacker.config.setStackRadius(v), 6.0));
        register(ConfigOption.ofDouble("playerStackRadius", Category.STACKING,
                "In PLAYERS stack mode, mobs within this many blocks of any player are allowed to stack.",
                1.0, 42000.0, () -> MobStacker.config.getPlayerStackRadius(), v -> MobStacker.config.setPlayerStackRadius(v), 12.0));
        register(ConfigOption.ofInt("stackScanInterval", Category.STACKING,
                "How often (in ticks) a mob re-checks for a nearby stack to join, so mobs that never move still merge. 0 only merges when a mob crosses a block boundary.",
                0, 1200, () -> MobStacker.config.getStackScanInterval(), v -> MobStacker.config.setStackScanInterval(v), 20));
        register(ConfigOption.ofBool("stackEquippedMobs", Category.STACKING,
                "Allow mobs that hold or wear items to stack. Off keeps an armed or armored mob out of stacks entirely.",
                () -> MobStacker.config.getStackEquippedMobs(), v -> MobStacker.config.setStackEquippedMobs(v), false));
        register(ConfigOption.ofBool("keepMemberEquipment", Category.STACKING,
                "Remember what every mob in a stack wears and holds, so its own gear drops when it is killed instead of being lost on the merge.",
                () -> MobStacker.config.getKeepMemberEquipment(), v -> MobStacker.config.setKeepMemberEquipment(v), true)
                .requires("stackEquippedMobs"));
        register(ConfigOption.ofBool("stackNamedMobs", Category.STACKING,
                "Let mobs renamed with a name tag stack with other mobs of the same name. Off protects a named mob from being absorbed, but a stack that is renamed keeps stacking either way.",
                () -> MobStacker.config.getStackNamedMobs(), v -> MobStacker.config.setStackNamedMobs(v), false));
        register(ConfigOption.ofBool("killWholeStackOnDeath", Category.STACKING,
                "Killing the top mob kills the entire stack at once (disables damage overflow).",
                () -> MobStacker.config.getKillWholeStackOnDeath(), v -> MobStacker.config.setKillWholeStackOnDeath(v), false)
                .lockedOnBy("stackHealth"));
        register(ConfigOption.ofBool("stackHealth", Category.STACKING,
                "A stack's health scales with its size. Forces killWholeStackOnDeath on for as long as it is on.",
                () -> MobStacker.config.getStackHealth(), v -> MobStacker.config.setStackHealth(v), false)
                .withAppliedNote(() -> MobStacker.config.getStackHealth()
                        ? "killWholeStackOnDeath is forced on while this is on" : null));

        // --- Combat ---
        register(ConfigOption.ofBool("damageOverflow", Category.COMBAT,
                "Leftover lethal damage carries down onto the mobs below in the stack.",
                () -> MobStacker.config.getDamageOverflow(), v -> MobStacker.config.setDamageOverflow(v), true));
        register(ConfigOption.ofBool("sweepingEdgeOverflow", Category.COMBAT,
                "Fold vanilla Sweeping Edge damage back into the hit so it clears a stack.",
                () -> MobStacker.config.getSweepingEdgeOverflow(), v -> MobStacker.config.setSweepingEdgeOverflow(v), true));
        register(ConfigOption.ofBool("sweepingEdgePerMob", Category.COMBAT,
                "Sweep every mob in the stack for 1 + damage x (level / (level + 1)) each, exactly like a vanilla sweep through a crowd, instead of adding one flat bonus to the hit.",
                () -> MobStacker.config.getSweepingEdgePerMob(), v -> MobStacker.config.setSweepingEdgePerMob(v), false)
                .requires("sweepingEdgeOverflow"));
        register(ConfigOption.ofBool("sweepingEdgeSingleHit", Category.COMBAT,
                "Put every other mob's sweep into the one hit and let damage overflow carry it down the stack, killing several mobs outright, instead of wounding each of them separately.",
                () -> MobStacker.config.getSweepingEdgeSingleHit(), v -> MobStacker.config.setSweepingEdgeSingleHit(v), false)
                .requires("sweepingEdgePerMob")
                // With one death for the whole stack there are no separate members left to wound, so
                // the sweep goes into the single hit whatever this says. stackHealth is covered too,
                // since it forces killWholeStackOnDeath on.
                .redundantWhen("killWholeStackOnDeath"));
        register(ConfigOption.ofBool("sweepingEdgeVanillaConditions", Category.COMBAT,
                "Only sweep when vanilla would: fully charged swing, no critical hit, not sprinting, on the ground, sword in hand.",
                () -> MobStacker.config.getSweepingEdgeVanillaConditions(), v -> MobStacker.config.setSweepingEdgeVanillaConditions(v), false)
                .requires("sweepingEdgeOverflow"));
        register(ConfigOption.ofInt("sweepingEdgeMaxKills", Category.COMBAT,
                "Cap how many mobs one sweep may kill in a single swing (0 = no cap). Only used by sweepingEdgePerMob.",
                0, 100000, () -> MobStacker.config.getSweepingEdgeMaxKills(), v -> MobStacker.config.setSweepingEdgeMaxKills(v), 0)
                .requires("sweepingEdgeOverflow"));

        // --- Kill feedback ---
        register(ConfigOption.ofBool("stackKillActionBar", Category.FEEDBACK,
                "Show an action-bar line (\"Killed Nx Name - M left\") when killing from a stack.",
                () -> MobStacker.config.getStackKillActionBar(), v -> MobStacker.config.setStackKillActionBar(v), true));
        register(ConfigOption.ofBool("stackKillParticles", Category.FEEDBACK,
                "Pop a particle burst scaled to how many mobs a hit killed.",
                () -> MobStacker.config.getStackKillParticles(), v -> MobStacker.config.setStackKillParticles(v), true));
        register(ConfigOption.ofBool("stackKillHologram", Category.FEEDBACK,
                "Float a short-lived \"-N\" hologram above the mob on a stacked kill.",
                () -> MobStacker.config.getStackKillHologram(), v -> MobStacker.config.setStackKillHologram(v), true));
        register(ConfigOption.ofEnum("killHologramColor", Category.FEEDBACK,
                "Colour of the floating \"-N\" kill hologram.",
                StackColor.class, () -> MobStacker.config.getKillHologramColor(),
                v -> MobStacker.config.setKillHologramColor(v), StackColor.RED));

        // --- Stack display ---
        register(ConfigOption.ofEnum("stackNameColor", Category.DISPLAY,
                "Colour of the \"Cow x16\" name shown above a stack. A mob named with a name tag keeps its own colour.",
                StackColor.class, () -> MobStacker.config.getStackNameColor(),
                v -> MobStacker.config.setStackNameColor(v), StackColor.WHITE));
        register(ConfigOption.ofBool("stackNameColorBySize", Category.DISPLAY,
                "Colour the stack name by how big the stack is, so large stacks stand out at a glance.",
                () -> MobStacker.config.getStackNameColorBySize(),
                v -> MobStacker.config.setStackNameColorBySize(v), false));
        register(ConfigOption.ofEnum("stackNameColorMedium", Category.DISPLAY,
                "Name colour once a stack reaches stackSizeMediumThreshold (needs stackNameColorBySize).",
                StackColor.class, () -> MobStacker.config.getStackNameColorMedium(),
                v -> MobStacker.config.setStackNameColorMedium(v), StackColor.YELLOW));
        register(ConfigOption.ofEnum("stackNameColorLarge", Category.DISPLAY,
                "Name colour once a stack reaches stackSizeLargeThreshold (needs stackNameColorBySize).",
                StackColor.class, () -> MobStacker.config.getStackNameColorLarge(),
                v -> MobStacker.config.setStackNameColorLarge(v), StackColor.RED));
        register(ConfigOption.ofInt("stackSizeMediumThreshold", Category.DISPLAY,
                "Stack size at which the name switches to stackNameColorMedium.",
                2, 100000, () -> MobStacker.config.getStackSizeMediumThreshold(),
                v -> MobStacker.config.setStackSizeMediumThreshold(v), 16));
        register(ConfigOption.ofInt("stackSizeLargeThreshold", Category.DISPLAY,
                "Stack size at which the name switches to stackNameColorLarge.",
                2, 100000, () -> MobStacker.config.getStackSizeLargeThreshold(),
                v -> MobStacker.config.setStackSizeLargeThreshold(v), 64));

        // --- Breeding ---
        register(ConfigOption.ofBool("enableStackBreeding", Category.BREEDING,
                "Feeding a stacked animal breeds its members without unstacking them.",
                () -> MobStacker.config.getEnableStackBreeding(), v -> MobStacker.config.setEnableStackBreeding(v), true));
        register(ConfigOption.ofBool("breedOnePerClick", Category.BREEDING,
                "One click feeds a single member (on) instead of as many as the food in hand (off).",
                () -> MobStacker.config.getBreedOnePerClick(), v -> MobStacker.config.setBreedOnePerClick(v), false));
        register(ConfigOption.ofBool("stackedHarvest", Category.BREEDING,
                "Shearing or milking a stack gives one mob's worth per member, and costs one bucket and one point of shear durability per member. Off makes a stack give what a single mob would.",
                () -> MobStacker.config.getStackedHarvest(), v -> MobStacker.config.setStackedHarvest(v), true));
        register(ConfigOption.ofBool("enableAnimalBabyStacking", Category.BREEDING,
                "Let baby farm animals stack together.",
                () -> MobStacker.config.getEnableAnimalBabyStacking(), v -> MobStacker.config.setEnableAnimalBabyStacking(v), true));
        register(ConfigOption.ofBool("enableHostileBabyStacking", Category.BREEDING,
                "Let baby hostile mobs (e.g. baby zombies) stack together.",
                () -> MobStacker.config.getEnableHostileBabyStacking(), v -> MobStacker.config.setEnableHostileBabyStacking(v), true));

        // --- Drops & XP ---
        register(ConfigOption.ofBool("compactDrops", Category.DROPS,
                "Merge a stacked mob's death drops into as few full item stacks as possible.",
                () -> MobStacker.config.getCompactDrops(), v -> MobStacker.config.setCompactDrops(v), true));
        register(ConfigOption.ofBool("compactExperience", Category.DROPS,
                "Combine a stacked mob's death experience into a single orb.",
                () -> MobStacker.config.getCompactExperience(), v -> MobStacker.config.setCompactExperience(v), true));

        // --- Separator ---
        register(ConfigOption.ofBool("enableSeparator", Category.SEPARATOR,
                "Enable right-clicking a stack with the separator item to split one mob off.",
                () -> MobStacker.config.getEnableSeparator(), v -> MobStacker.config.setEnableSeparator(v), false));
        register(ConfigOption.ofBool("consumeSeparator", Category.SEPARATOR,
                "Consume one separator item each time a stack is separated.",
                () -> MobStacker.config.getConsumeSeparator(), v -> MobStacker.config.setConsumeSeparator(v), true));
        register(ConfigOption.ofString("separatorItem", Category.SEPARATOR,
                "The item used to separate a mob from a stack.",
                true, () -> MobStacker.config.getSeparatorItem(), v -> MobStacker.config.setSeparatorItem(v), "minecraft:diamond")
                .withValidator(value -> {
                    ResourceLocation id = ResourceLocation.tryParse((String) value);
                    return (id != null && BuiltInRegistries.ITEM.containsKey(id)) ? null : "Unknown item: " + value;
                }));

        // --- Mob caps (vanilla spawn caps per category) ---
        register(ConfigOption.ofInt("monsterMobCap", Category.MOBCAPS, "Vanilla spawn cap for the monster category.",
                0, 128, () -> MobStacker.config.getMonsterMobCap(), v -> MobStacker.config.setMonsterMobCap(v), 22));
        register(ConfigOption.ofInt("creatureMobCap", Category.MOBCAPS, "Vanilla spawn cap for the creature (animal) category.",
                0, 128, () -> MobStacker.config.getCreatureMobCap(), v -> MobStacker.config.setCreatureMobCap(v), 5));
        register(ConfigOption.ofInt("ambientMobCap", Category.MOBCAPS, "Vanilla spawn cap for the ambient (bat) category.",
                0, 128, () -> MobStacker.config.getAmbientMobCap(), v -> MobStacker.config.setAmbientMobCap(v), 7));
        register(ConfigOption.ofInt("axolotlsMobCap", Category.MOBCAPS, "Vanilla spawn cap for axolotls.",
                0, 128, () -> MobStacker.config.getAxolotlsMobCap(), v -> MobStacker.config.setAxolotlsMobCap(v), 2));
        register(ConfigOption.ofInt("undergroundWaterCreatureMobCap", Category.MOBCAPS, "Vanilla spawn cap for underground water creatures (glow squid).",
                0, 128, () -> MobStacker.config.getUndergroundWaterCreatureMobCap(), v -> MobStacker.config.setUndergroundWaterCreatureMobCap(v), 2));
        register(ConfigOption.ofInt("waterCreatureMobCap", Category.MOBCAPS, "Vanilla spawn cap for water creatures (squid, dolphin).",
                0, 128, () -> MobStacker.config.getWaterCreatureMobCap(), v -> MobStacker.config.setWaterCreatureMobCap(v), 2));
        register(ConfigOption.ofInt("waterAmbientMobCap", Category.MOBCAPS, "Vanilla spawn cap for water ambient mobs (fish).",
                0, 128, () -> MobStacker.config.getWaterAmbientMobCap(), v -> MobStacker.config.setWaterAmbientMobCap(v), 8));
    }

    private MobStackerSettings() {
    }

    /**
     * Why {@code option} cannot be changed right now, or null when it can. Settings that declare
     * {@link ConfigOption#requires(String)} — the Sweeping Edge tuning options — do nothing until the
     * setting they need is on, so they say so instead of silently having no effect.
     * <p>
     * The dependency is resolved wherever the change is being made: {@code lookup} is asked for the
     * value in force there (a region's own value, or the config a remote client is showing) and a
     * null answer falls back to the global config. That is what lets a region enable
     * {@code sweepingEdgeOverflow} for itself and then use the options that build on it, exactly as
     * the game resolves them at the mob.
     *
     * @param lookup     the value of a setting id in the scope being edited, or null for the global config
     * @param regionName the region being edited, only used to word the message
     */
    public static String dependencyProblem(ConfigOption option,
                                           java.util.function.Function<String, String> lookup,
                                           String regionName) {
        // Both "not yet" and "no longer needed" answer the same question — why this setting would
        // have no effect — so they come back through one entry point and every caller gets both
        // without having to remember there are three shapes.
        if (isRedundant(option, lookup)) {
            String where = regionName == null ? "here" : "in region '" + regionName + "'";
            return "'" + option.id() + "' changes nothing " + where + " while " + option.redundantWhen()
                    + " is on, which already does the same thing. Turn " + option.redundantWhen()
                    + " off to use it.";
        }
        String requiredId = option.requires();
        if (requiredId == null) {
            return null;
        }
        ConfigOption required = byId(requiredId);
        if (required == null) {
            return null;
        }
        if (dependencyMet(option, lookup)) {
            return null;
        }
        String where = regionName == null
                ? "Enable it first."
                : "Enable it in region '" + regionName + "' (or globally) first.";
        return "'" + option.id() + "' only applies while " + requiredId + " is on. " + where;
    }

    /** Whether the setting that would make this one pointless is on in the scope {@code lookup} answers for. */
    private static boolean isRedundant(ConfigOption option,
                                       java.util.function.Function<String, String> lookup) {
        String otherId = option.redundantWhen();
        if (otherId == null) {
            return false;
        }
        ConfigOption other = byId(otherId);
        if (other == null) {
            return false;
        }
        String value = lookup == null ? null : lookup.apply(otherId);
        if (value == null || value.isEmpty()) {
            // currentValue, not storedValue: killWholeStackOnDeath is itself forced on by stackHealth,
            // and a setting made redundant by a forced one is just as redundant.
            value = other.currentValue();
        }
        return Boolean.parseBoolean(value);
    }

    /** Whether the setting this one needs is on in the scope {@code lookup} answers for. */
    private static boolean dependencyMet(ConfigOption option,
                                         java.util.function.Function<String, String> lookup) {
        String requiredId = option.requires();
        if (requiredId == null) {
            return true;
        }
        ConfigOption required = byId(requiredId);
        if (required == null) {
            return true;
        }
        String value = lookup == null ? null : lookup.apply(requiredId);
        if (value == null || value.isEmpty()) {
            // Resolved, not stored: a setting whose own dependency is off is off, so a chain of them
            // (sweepingEdgeOverflow -> sweepingEdgePerMob -> sweepingEdgeSingleHit) collapses as one.
            value = required.currentValue();
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * The value {@code option} really has in the scope {@code lookup} answers for, or null when
     * nothing overrides what is stored. Two things can override it, and they are opposites:
     * <ul>
     *   <li>a setting that pins this one on ({@link ConfigOption#lockedOnBy(String)}) — it reads as
     *       {@code true};</li>
     *   <li>a setting this one needs that is off ({@link ConfigOption#requires(String)}) — it reads
     *       as its default, because it does nothing at all until that setting comes back on;</li>
     *   <li>a setting that already does this one's job ({@link ConfigOption#redundantWhen(String)}) —
     *       it reads as its default too, for the same reason read the other way round.</li>
     * </ul>
     * Either way the stored value is left untouched and returns the moment the scope changes back,
     * so switching a master setting off and on again costs the player nothing.
     */
    public static String effectiveValue(ConfigOption option,
                                        java.util.function.Function<String, String> lookup) {
        String locked = lockedValue(option, lookup);
        if (locked != null) {
            return locked;
        }
        if (isRedundant(option, lookup)) {
            return option.defaultValue();
        }
        return dependencyMet(option, lookup) ? null : option.defaultValue();
    }

    /**
     * The value {@code option} is currently pinned to by another setting, or null when nothing pins
     * it. A setting declared with {@link ConfigOption#lockedOnBy(String)} reads as {@code "true"}
     * for as long as the setting that pins it is on, everywhere that setting is on: globally when
     * {@code lookup} is null, and inside one region when it answers for that region. The value
     * stored underneath is untouched and comes back the moment the lock lifts.
     *
     * @param lookup the value of a setting id in the scope being read, or null for the global config
     */
    public static String lockedValue(ConfigOption option,
                                     java.util.function.Function<String, String> lookup) {
        String lockId = option.lockedOnBy();
        if (lockId == null) {
            return null;
        }
        ConfigOption lock = byId(lockId);
        if (lock == null) {
            return null;
        }
        String value = lookup == null ? null : lookup.apply(lockId);
        if (value == null || value.isEmpty()) {
            value = lock.currentValue();
        }
        return Boolean.parseBoolean(value) ? "true" : null;
    }

    /**
     * Why {@code option} cannot be changed at all right now — another setting pins it — or null when
     * nothing does. Resolved in the scope being edited exactly like {@link #dependencyProblem}, so a
     * region that turns {@code stackHealth} on locks {@code killWholeStackOnDeath} in that region
     * only.
     */
    public static String lockProblem(ConfigOption option,
                                     java.util.function.Function<String, String> lookup,
                                     String regionName) {
        if (lockedValue(option, lookup) == null) {
            return null;
        }
        String where = regionName == null ? "here" : "in region '" + regionName + "'";
        return "'" + option.id() + "' is forced on " + where + " while " + option.lockedOnBy()
                + " is on. Turn " + option.lockedOnBy() + " off first.";
    }

    /**
     * Why {@code region} may not be given {@code canonical} for this setting, or null when it may.
     * Bundles both kinds of dependency — a hard lock and a "does nothing yet" requirement — and
     * resolves each against the region's own settings, so the answer is the one that applies where
     * that region is. Every path that writes a region override goes through this: the commands, the
     * config-sync channel and the singleplayer GUI.
     */
    public static String regionEditProblem(ConfigOption option, StackRegion region, String canonical) {
        String lock = lockProblem(option, region::getSetting, region.getName());
        if (lock != null) {
            return lock;
        }
        // Going back to the default is always allowed: that is how a setting is switched off again.
        if (canonical.equalsIgnoreCase(option.defaultValue())) {
            return null;
        }
        return dependencyProblem(option, region::getSetting, region.getName());
    }

    /** Whether a region may carry its own value for this setting. */
    public static boolean isRegionOverridable(String id) {
        ConfigOption option = byId(id);
        return option != null && !GLOBAL_ONLY.contains(option.id());
    }

    /** Every setting a region may override, in registry order. */
    public static List<ConfigOption> regionOverridable() {
        List<ConfigOption> out = new ArrayList<>();
        for (ConfigOption option : OPTIONS) {
            if (!GLOBAL_ONLY.contains(option.id())) {
                out.add(option);
            }
        }
        return out;
    }

    private static void register(ConfigOption option) {
        OPTIONS.add(option);
        BY_ID.put(option.id(), option);
    }

    /** All options, in declaration (category) order. */
    public static List<ConfigOption> all() {
        return OPTIONS;
    }

    /** All registered setting ids, in declaration order. */
    public static List<String> ids() {
        return new ArrayList<>(BY_ID.keySet());
    }

    /** Looks up an option by its id (case-insensitive), or null if there is none. */
    public static ConfigOption byId(String id) {
        ConfigOption exact = BY_ID.get(id);
        if (exact != null) {
            return exact;
        }
        for (ConfigOption option : OPTIONS) {
            if (option.id().equalsIgnoreCase(id)) {
                return option;
            }
        }
        return null;
    }

    /** Options belonging to a category, in declaration order. */
    public static List<ConfigOption> byCategory(Category category) {
        List<ConfigOption> out = new ArrayList<>();
        for (ConfigOption option : OPTIONS) {
            if (option.category() == category) {
                out.add(option);
            }
        }
        return out;
    }
}
