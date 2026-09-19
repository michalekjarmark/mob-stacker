package com.frikinjay.mobstacker.config;

import com.frikinjay.mobstacker.MobStacker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MobStackerConfig implements MobLists.Holder {
    private static final int MAX_CAP_VALUE = 128;
    private static final double MAX_RADIUS = 42000.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean killWholeStackOnDeath = false;
    private boolean stackHealth = false;
    private boolean enableDamageOverflow = true;
    private boolean sweepingEdgeOverflow = true;
    private boolean sweepingEdgePerMob = false;
    private boolean sweepingEdgeSingleHit = false;
    private boolean sweepingEdgeVanillaConditions = false;
    private int sweepingEdgeMaxKills = 0;
    private boolean stackEquippedMobs = false;
    private boolean keepMemberEquipment = true;
    private boolean stackNamedMobs = false;
    private boolean stackKillActionBar = true;
    private boolean stackKillParticles = true;
    private boolean stackKillHologram = true;
    private StackColor killHologramColor = StackColor.RED;
    private StackColor stackNameColor = StackColor.WHITE;
    private boolean stackNameColorBySize = false;
    private StackColor stackNameColorMedium = StackColor.YELLOW;
    private StackColor stackNameColorLarge = StackColor.RED;
    private int stackSizeMediumThreshold = 16;
    private int stackSizeLargeThreshold = 64;
    private boolean enableStackBreeding = true;
    private boolean stackedHarvest = true;
    private boolean breedOnePerClick = false;
    private boolean enableAnimalBabyStacking = true;
    private boolean enableHostileBabyStacking = true;
    private boolean compactDrops = true;
    private boolean compactExperience = true;
    private int maxMobStackSize = 16;
    private double stackRadius = 6.0;
    private double playerStackRadius = 12.0;
    private int stackScanInterval = 20;
    private boolean stackOnSpawn = true;
    private boolean enableSeparator = false;
    private boolean consumeSeparator = true;
    private String separatorItem = "minecraft:diamond";

    private List<String> ignoredEntities = new ArrayList<>(Arrays.asList(
            "minecraft:ender_dragon",
            "minecraft:vex"
    ));
    private List<String> ignoredMods = new ArrayList<>(Arrays.asList(
            "corpse"
    ));
    // The other half, consulted only while mobListMode is WHITELIST. Empty by default, which is why
    // switching a fresh config to WHITELIST stacks nothing until something is added - deliberate:
    // "only these stack" with nothing listed can only honestly mean nothing.
    private List<String> allowedEntities = new ArrayList<>();
    private List<String> allowedMods = new ArrayList<>();
    private MobListMode mobListMode = MobListMode.BLACKLIST;

    // Per-type stack ceilings: "minecraft:cow" -> 64. Anything not named here uses maxStackSize.
    // A map rather than 7 more scalar settings because the interesting keys are whatever mobs this
    // particular server has, including modded ones nobody can enumerate in advance.
    private Map<String, Integer> maxStackSizes = new LinkedHashMap<>();

    // Off by default: a freshly installed mod stacks nothing until an operator opts in (see the
    // first-run notice logged by MobStacker.loadWorldConfig). Existing configs keep their saved mode.
    private StackMode stackMode = StackMode.OFF;
    private List<StackRegion> regions = new ArrayList<>();

    private final MobCaps mobCaps = new MobCaps();

    public static MobStackerConfig load() {
        File file = MobStacker.configFile;
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                MobStackerConfig loaded = GSON.fromJson(reader, MobStackerConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (Exception e) {
                MobStacker.logger.error("Failed to load config", e);
            }
        }
        return new MobStackerConfig();
    }

    public void save() {
        // stackHealth forces killWholeStackOnDeath on, but that is applied where the settings are
        // read (MobStacker#getKillWholeStackOnDeath) rather than written here. Overwriting the
        // stored value would lose the player's own choice the first time stackHealth was switched
        // on, and would only ever cover the global config - never a region that enables it.
        pruneRedundantRegionOverrides();
        try (FileWriter writer = new FileWriter(MobStacker.configFile)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            MobStacker.logger.error("Failed to save config", e);
        }
    }

    /**
     * Drops region overrides that say exactly what the global config already says.
     * <p>
     * A region stores only the settings it changes, so an override equal to the global value is not
     * one: it made the GUI mark the row as changed, and it pinned the region in place when the
     * global value later moved on to something else. Doing it here, on the way to disk, catches
     * every way the two can come to agree - the region being set to the global value, or the global
     * value being changed to the region's - from the commands and both GUIs alike.
     */
    private void pruneRedundantRegionOverrides() {
        if (MobStacker.config != this || regions == null || regions.isEmpty()) {
            return; // not the live config: its values are not what these regions differ from
        }
        for (StackRegion region : regions) {
            for (String id : new ArrayList<>(region.getSettings().keySet())) {
                ConfigOption option = MobStackerSettings.byId(id);
                if (option != null && option.storedValue().equalsIgnoreCase(region.getSetting(id))) {
                    region.clearSetting(id);
                }
            }
        }
    }

    public String getSeparatorItem() { return separatorItem; }
    public boolean getConsumeSeparator() { return consumeSeparator; }
    public boolean getEnableSeparator() { return enableSeparator; }
    public boolean getKillWholeStackOnDeath() { return killWholeStackOnDeath; }
    public boolean getStackHealth() { return stackHealth; }
    public boolean getDamageOverflow() { return enableDamageOverflow; }
    public boolean getSweepingEdgeOverflow() { return sweepingEdgeOverflow; }
    public boolean getSweepingEdgePerMob() { return sweepingEdgePerMob; }
    public boolean getSweepingEdgeSingleHit() { return sweepingEdgeSingleHit; }
    public boolean getSweepingEdgeVanillaConditions() { return sweepingEdgeVanillaConditions; }
    public int getSweepingEdgeMaxKills() { return sweepingEdgeMaxKills; }
    public boolean getStackEquippedMobs() { return stackEquippedMobs; }
    public boolean getKeepMemberEquipment() { return keepMemberEquipment; }
    public boolean getStackNamedMobs() { return stackNamedMobs; }
    public boolean getStackKillActionBar() { return stackKillActionBar; }
    public boolean getStackKillParticles() { return stackKillParticles; }
    public boolean getStackKillHologram() { return stackKillHologram; }
    public StackColor getKillHologramColor() { return killHologramColor != null ? killHologramColor : StackColor.RED; }
    public StackColor getStackNameColor() { return stackNameColor != null ? stackNameColor : StackColor.WHITE; }
    public boolean getStackNameColorBySize() { return stackNameColorBySize; }
    public StackColor getStackNameColorMedium() { return stackNameColorMedium != null ? stackNameColorMedium : StackColor.YELLOW; }
    public StackColor getStackNameColorLarge() { return stackNameColorLarge != null ? stackNameColorLarge : StackColor.RED; }
    public int getStackSizeMediumThreshold() { return stackSizeMediumThreshold; }
    public int getStackSizeLargeThreshold() { return stackSizeLargeThreshold; }
    public boolean getEnableStackBreeding() { return enableStackBreeding; }
    public boolean getStackedHarvest() { return stackedHarvest; }
    public boolean getBreedOnePerClick() { return breedOnePerClick; }
    public boolean getEnableAnimalBabyStacking() { return enableAnimalBabyStacking; }
    public boolean getEnableHostileBabyStacking() { return enableHostileBabyStacking; }
    public boolean getCompactDrops() { return compactDrops; }
    public boolean getCompactExperience() { return compactExperience; }
    public int getMaxMobStackSize() { return maxMobStackSize; }
    public double getStackRadius() { return stackRadius; }
    public double getPlayerStackRadius() { return playerStackRadius; }
    public int getStackScanInterval() { return stackScanInterval; }

    public void setSeparatorItem(String separatorItem) {
        this.separatorItem = separatorItem;
        save();
    }

    public void setConsumeSeparator(boolean consumeSeparator) {
        this.consumeSeparator = consumeSeparator;
        save();
    }

    public void setEnableSeparator(boolean enableSeparator) {
        this.enableSeparator = enableSeparator;
        save();
    }

    public void setKillWholeStackOnDeath(boolean killWholeStackOnDeath) {
        this.killWholeStackOnDeath = killWholeStackOnDeath;
        save();
    }

    public void setStackHealth(boolean stackHealth) {
        this.stackHealth = stackHealth;
        save();
    }

    public void setDamageOverflow(boolean enableDamageOverflow) {
        this.enableDamageOverflow = enableDamageOverflow;
        save();
    }

    public void setSweepingEdgeOverflow(boolean sweepingEdgeOverflow) {
        this.sweepingEdgeOverflow = sweepingEdgeOverflow;
        save();
    }

    public void setSweepingEdgePerMob(boolean sweepingEdgePerMob) {
        this.sweepingEdgePerMob = sweepingEdgePerMob;
        save();
    }

    public void setSweepingEdgeSingleHit(boolean sweepingEdgeSingleHit) {
        this.sweepingEdgeSingleHit = sweepingEdgeSingleHit;
        save();
    }

    public void setSweepingEdgeVanillaConditions(boolean sweepingEdgeVanillaConditions) {
        this.sweepingEdgeVanillaConditions = sweepingEdgeVanillaConditions;
        save();
    }

    public void setSweepingEdgeMaxKills(int sweepingEdgeMaxKills) {
        this.sweepingEdgeMaxKills = Math.max(0, sweepingEdgeMaxKills);
        save();
    }

    public void setStackEquippedMobs(boolean stackEquippedMobs) {
        this.stackEquippedMobs = stackEquippedMobs;
        save();
    }

    public void setKeepMemberEquipment(boolean keepMemberEquipment) {
        this.keepMemberEquipment = keepMemberEquipment;
        save();
    }

    public void setStackNamedMobs(boolean stackNamedMobs) {
        this.stackNamedMobs = stackNamedMobs;
        save();
    }

    public void setStackKillActionBar(boolean stackKillActionBar) {
        this.stackKillActionBar = stackKillActionBar;
        save();
    }

    public void setStackKillParticles(boolean stackKillParticles) {
        this.stackKillParticles = stackKillParticles;
        save();
    }

    public void setStackKillHologram(boolean stackKillHologram) {
        this.stackKillHologram = stackKillHologram;
        save();
    }

    public void setKillHologramColor(StackColor killHologramColor) {
        this.killHologramColor = killHologramColor;
        save();
    }

    public void setStackNameColor(StackColor stackNameColor) {
        this.stackNameColor = stackNameColor;
        save();
    }

    public void setStackNameColorBySize(boolean stackNameColorBySize) {
        this.stackNameColorBySize = stackNameColorBySize;
        save();
    }

    public void setStackNameColorMedium(StackColor stackNameColorMedium) {
        this.stackNameColorMedium = stackNameColorMedium;
        save();
    }

    public void setStackNameColorLarge(StackColor stackNameColorLarge) {
        this.stackNameColorLarge = stackNameColorLarge;
        save();
    }

    public void setStackSizeMediumThreshold(int stackSizeMediumThreshold) {
        this.stackSizeMediumThreshold = Math.max(2, stackSizeMediumThreshold);
        save();
    }

    public void setStackSizeLargeThreshold(int stackSizeLargeThreshold) {
        this.stackSizeLargeThreshold = Math.max(2, stackSizeLargeThreshold);
        save();
    }

    public void setStackedHarvest(boolean stackedHarvest) {
        this.stackedHarvest = stackedHarvest;
    }

    public void setEnableStackBreeding(boolean enableStackBreeding) {
        this.enableStackBreeding = enableStackBreeding;
        save();
    }

    public void setBreedOnePerClick(boolean breedOnePerClick) {
        this.breedOnePerClick = breedOnePerClick;
        save();
    }

    public void setEnableAnimalBabyStacking(boolean enableAnimalBabyStacking) {
        this.enableAnimalBabyStacking = enableAnimalBabyStacking;
        save();
    }

    public void setEnableHostileBabyStacking(boolean enableHostileBabyStacking) {
        this.enableHostileBabyStacking = enableHostileBabyStacking;
        save();
    }

    public void setCompactDrops(boolean compactDrops) {
        this.compactDrops = compactDrops;
        save();
    }

    public void setCompactExperience(boolean compactExperience) {
        this.compactExperience = compactExperience;
        save();
    }

    public void setMaxMobStackSize(int maxMobStackSize) {
        this.maxMobStackSize = maxMobStackSize;
        save();
    }

    public void setStackRadius(double stackRadius) {
        this.stackRadius = Math.min(stackRadius, MAX_RADIUS);
        save();
    }

    public void setPlayerStackRadius(double playerStackRadius) {
        this.playerStackRadius = Math.min(playerStackRadius, MAX_RADIUS);
        save();
    }

    public void setStackScanInterval(int stackScanInterval) {
        this.stackScanInterval = Math.max(0, stackScanInterval);
        save();
    }

    public List<String> getIgnoredEntities() {
        return getList(MobListKind.DENY_ENTITIES);
    }

    public List<String> getIgnoredMods() {
        return getList(MobListKind.DENY_MODS);
    }

    /**
     * The backing list, created on demand. Global lists always exist - "not set" is a distinction
     * only a region needs, since the global config is what a region falls back <em>to</em>.
     */
    private List<String> backing(MobListKind kind) {
        switch (kind) {
            case DENY_ENTITIES:
                return ignoredEntities == null ? (ignoredEntities = new ArrayList<>()) : ignoredEntities;
            case DENY_MODS:
                return ignoredMods == null ? (ignoredMods = new ArrayList<>()) : ignoredMods;
            case ALLOW_ENTITIES:
                return allowedEntities == null ? (allowedEntities = new ArrayList<>()) : allowedEntities;
            case ALLOW_MODS:
            default:
                return allowedMods == null ? (allowedMods = new ArrayList<>()) : allowedMods;
        }
    }

    @Override
    public List<String> getList(MobListKind kind) {
        return Collections.unmodifiableList(backing(kind));
    }

    @Override
    public boolean hasList(MobListKind kind) {
        return true; // the global config is the fallback; it never inherits from anywhere
    }

    @Override
    public boolean addToList(MobListKind kind, String entry) {
        String value = MobLists.normalise(kind, entry);
        List<String> list = backing(kind);
        if (value.isEmpty() || list.contains(value)) {
            return false;
        }
        list.add(value);
        save();
        return true;
    }

    @Override
    public boolean removeFromList(MobListKind kind, String entry) {
        if (backing(kind).remove(MobLists.normalise(kind, entry))) {
            save();
            return true;
        }
        return false;
    }

    @Override
    public void clearList(MobListKind kind) {
        if (!backing(kind).isEmpty()) {
            backing(kind).clear();
            save();
        }
    }

    @Override
    public void setList(MobListKind kind, List<String> entries) {
        List<String> list = backing(kind);
        list.clear();
        for (String entry : entries) {
            String value = MobLists.normalise(kind, entry);
            if (!value.isEmpty() && !list.contains(value)) {
                list.add(value);
            }
        }
        save();
    }

    public boolean getStackOnSpawn() {
        return stackOnSpawn;
    }

    public void setStackOnSpawn(boolean stackOnSpawn) {
        this.stackOnSpawn = stackOnSpawn;
        save();
    }

    public MobListMode getMobListMode() {
        return mobListMode != null ? mobListMode : MobListMode.BLACKLIST;
    }

    public void setMobListMode(MobListMode mobListMode) {
        this.mobListMode = mobListMode != null ? mobListMode : MobListMode.BLACKLIST;
        save();
    }

    /** Every per-type ceiling, as entity id -> size. Never null. */
    public Map<String, Integer> getMaxStackSizes() {
        if (maxStackSizes == null) {
            maxStackSizes = new LinkedHashMap<>();
        }
        return Collections.unmodifiableMap(maxStackSizes);
    }

    /** The ceiling set for this entity id, or null when it just follows {@code maxStackSize}. */
    public Integer getMaxStackSize(String entityId) {
        if (maxStackSizes == null) {
            return null;
        }
        return maxStackSizes.get(MobLists.normalise(MobListKind.DENY_ENTITIES, entityId));
    }

    /** Sets a per-type ceiling, or drops it when {@code size} is null. */
    public void setMaxStackSize(String entityId, Integer size) {
        if (maxStackSizes == null) {
            maxStackSizes = new LinkedHashMap<>();
        }
        String key = MobLists.normalise(MobListKind.DENY_ENTITIES, entityId);
        if (size == null) {
            maxStackSizes.remove(key);
        } else {
            maxStackSizes.put(key, Math.max(1, size));
        }
        save();
    }

    public StackMode getStackMode() {
        return stackMode != null ? stackMode : StackMode.OFF;
    }

    public void setStackMode(StackMode stackMode) {
        this.stackMode = stackMode;
        save();
    }

    public List<StackRegion> getRegions() {
        return Collections.unmodifiableList(regions);
    }

    public StackRegion getRegion(String name) {
        return regions.stream()
                .filter(region -> region.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public boolean addRegion(StackRegion region) {
        if (getRegion(region.getName()) != null) {
            return false;
        }
        regions.add(region);
        save();
        return true;
    }

    public boolean removeRegion(String name) {
        if (regions.removeIf(region -> region.getName().equalsIgnoreCase(name))) {
            save();
            return true;
        }
        return false;
    }

    public int getMonsterMobCap() { return mobCaps.monster; }
    public int getCreatureMobCap() { return mobCaps.creature; }
    public int getAmbientMobCap() { return mobCaps.ambient; }
    public int getAxolotlsMobCap() { return mobCaps.axolotls; }
    public int getUndergroundWaterCreatureMobCap() { return mobCaps.undergroundWaterCreature; }
    public int getWaterCreatureMobCap() { return mobCaps.waterCreature; }
    public int getWaterAmbientMobCap() { return mobCaps.waterAmbient; }

    public void setMonsterMobCap(int value) { mobCaps.setMonster(value); save(); }
    public void setCreatureMobCap(int value) { mobCaps.setCreature(value); save(); }
    public void setAmbientMobCap(int value) { mobCaps.setAmbient(value); save(); }
    public void setAxolotlsMobCap(int value) { mobCaps.setAxolotls(value); save(); }
    public void setUndergroundWaterCreatureMobCap(int value) { mobCaps.setUndergroundWaterCreature(value); save(); }
    public void setWaterCreatureMobCap(int value) { mobCaps.setWaterCreature(value); save(); }
    public void setWaterAmbientMobCap(int value) { mobCaps.setWaterAmbient(value); save(); }

    private static class MobCaps {
        private int monster = 22;
        private int creature = 5;
        private int ambient = 7;
        private int axolotls = 2;
        private int undergroundWaterCreature = 2;
        private int waterCreature = 2;
        private int waterAmbient = 8;

        private void setMonster(int value) { monster = validateCap(value); }
        private void setCreature(int value) { creature = validateCap(value); }
        private void setAmbient(int value) { ambient = validateCap(value); }
        private void setAxolotls(int value) { axolotls = validateCap(value); }
        private void setUndergroundWaterCreature(int value) { undergroundWaterCreature = validateCap(value); }
        private void setWaterCreature(int value) { waterCreature = validateCap(value); }
        private void setWaterAmbient(int value) { waterAmbient = validateCap(value); }

        private static int validateCap(int value) {
            return Math.min(value, MAX_CAP_VALUE);
        }
    }
}