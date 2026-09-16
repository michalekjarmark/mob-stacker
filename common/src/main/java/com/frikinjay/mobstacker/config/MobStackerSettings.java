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
                "The largest a stack is allowed to grow to.",
                1, 100000, () -> MobStacker.config.getMaxMobStackSize(), v -> MobStacker.config.setMaxMobStackSize(v), 16));
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
                "Allow mobs that hold or wear items to stack (variant B: off keeps them separate).",
                () -> MobStacker.config.getStackEquippedMobs(), v -> MobStacker.config.setStackEquippedMobs(v), false));
        register(ConfigOption.ofBool("killWholeStackOnDeath", Category.STACKING,
                "Killing the top mob kills the entire stack at once (disables damage overflow).",
                () -> MobStacker.config.getKillWholeStackOnDeath(), v -> MobStacker.config.setKillWholeStackOnDeath(v), false)
                .withValidator(value -> (!(Boolean) value && MobStacker.config.getStackHealth())
                        ? "Cannot disable killWholeStackOnDeath while stackHealth is on. Turn stackHealth off first."
                        : null));
        register(ConfigOption.ofBool("stackHealth", Category.STACKING,
                "A stack's health scales with its size (forces killWholeStackOnDeath on).",
                () -> MobStacker.config.getStackHealth(), v -> MobStacker.config.setStackHealth(v), false)
                .withAppliedNote(() -> MobStacker.config.getStackHealth() && MobStacker.config.getKillWholeStackOnDeath()
                        ? "killWholeStackOnDeath was also enabled" : null));

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
                .withValidator(requiresSweepingEdgeOverflow("sweepingEdgePerMob")));
        register(ConfigOption.ofBool("sweepingEdgeVanillaConditions", Category.COMBAT,
                "Only sweep when vanilla would: fully charged swing, no critical hit, not sprinting, on the ground, sword in hand.",
                () -> MobStacker.config.getSweepingEdgeVanillaConditions(), v -> MobStacker.config.setSweepingEdgeVanillaConditions(v), false)
                .withValidator(requiresSweepingEdgeOverflow("sweepingEdgeVanillaConditions")));
        register(ConfigOption.ofInt("sweepingEdgeMaxKills", Category.COMBAT,
                "Cap how many mobs one sweep may kill in a single swing (0 = no cap). Only used by sweepingEdgePerMob.",
                0, 100000, () -> MobStacker.config.getSweepingEdgeMaxKills(), v -> MobStacker.config.setSweepingEdgeMaxKills(v), 0)
                .withValidator(requiresSweepingEdgeOverflow("sweepingEdgeMaxKills")));

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
     * The Sweeping Edge tuning options only do anything while sweepingEdgeOverflow is on, so they
     * refuse to be changed away from their default until it is enabled — the same "explain the
     * dependency instead of silently doing nothing" rule used by stackHealth / killWholeStackOnDeath.
     */
    private static java.util.function.Function<Object, String> requiresSweepingEdgeOverflow(String id) {
        return value -> {
            boolean wantsDefault = Boolean.FALSE.equals(value) || Integer.valueOf(0).equals(value);
            if (wantsDefault || MobStacker.config.getSweepingEdgeOverflow()) {
                return null;
            }
            return "'" + id + "' only applies while sweepingEdgeOverflow is on. Enable it first.";
        };
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
