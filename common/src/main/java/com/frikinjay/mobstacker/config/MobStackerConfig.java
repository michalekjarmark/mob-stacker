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
import java.util.List;

public class MobStackerConfig {
    private static final int MAX_CAP_VALUE = 128;
    private static final double MAX_RADIUS = 42000.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean killWholeStackOnDeath = false;
    private boolean stackHealth = false;
    private boolean enableDamageOverflow = true;
    private boolean sweepingEdgeOverflow = true;
    private boolean stackEquippedMobs = false;
    private boolean stackKillActionBar = true;
    private boolean stackKillParticles = true;
    private boolean stackKillHologram = true;
    private int maxMobStackSize = 16;
    private double stackRadius = 6.0;
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

    private StackMode stackMode = StackMode.REGIONS;
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
        if (stackHealth && !killWholeStackOnDeath) {
            killWholeStackOnDeath = true;
        }
        try (FileWriter writer = new FileWriter(MobStacker.configFile)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            MobStacker.logger.error("Failed to save config", e);
        }
    }

    public String getSeparatorItem() { return separatorItem; }
    public boolean getConsumeSeparator() { return consumeSeparator; }
    public boolean getEnableSeparator() { return enableSeparator; }
    public boolean getKillWholeStackOnDeath() { return killWholeStackOnDeath; }
    public boolean getStackHealth() { return stackHealth; }
    public boolean getDamageOverflow() { return enableDamageOverflow; }
    public boolean getSweepingEdgeOverflow() { return sweepingEdgeOverflow; }
    public boolean getStackEquippedMobs() { return stackEquippedMobs; }
    public boolean getStackKillActionBar() { return stackKillActionBar; }
    public boolean getStackKillParticles() { return stackKillParticles; }
    public boolean getStackKillHologram() { return stackKillHologram; }
    public int getMaxMobStackSize() { return maxMobStackSize; }
    public double getStackRadius() { return stackRadius; }

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

    public void setStackEquippedMobs(boolean stackEquippedMobs) {
        this.stackEquippedMobs = stackEquippedMobs;
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

    public void setMaxMobStackSize(int maxMobStackSize) {
        this.maxMobStackSize = maxMobStackSize;
        save();
    }

    public void setStackRadius(double stackRadius) {
        this.stackRadius = Math.min(stackRadius, MAX_RADIUS);
        save();
    }

    public List<String> getIgnoredEntities() {
        return Collections.unmodifiableList(ignoredEntities);
    }

    public List<String> getIgnoredMods() {
        return Collections.unmodifiableList(ignoredMods);
    }

    public boolean addIgnoredEntity(String entityId) {
        return addToList(entityId, ignoredEntities);
    }

    public boolean removeIgnoredEntity(String entityId) {
        return removeFromList(entityId, ignoredEntities);
    }

    public boolean addIgnoredMod(String modId) {
        return addToList(modId, ignoredMods);
    }

    public boolean removeIgnoredMod(String modId) {
        return removeFromList(modId, ignoredMods);
    }

    private boolean addToList(String item, List<String> list) {
        if (!list.contains(item)) {
            list.add(item);
            save();
            return true;
        }
        return false;
    }

    private boolean removeFromList(String item, List<String> list) {
        if (list.remove(item)) {
            save();
            return true;
        }
        return false;
    }

    public StackMode getStackMode() {
        return stackMode != null ? stackMode : StackMode.REGIONS;
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