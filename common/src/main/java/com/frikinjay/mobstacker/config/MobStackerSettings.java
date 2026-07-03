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
