package com.frikinjay.mobstacker;

import net.minecraft.world.entity.MobSpawnType;

/**
 * How a mob came into the world, in the four groups a server chooses between: the spawner farm is
 * stacked and the wildlife is left alone, or the other way round.
 *
 * <p>What is stored on the mob is vanilla's own {@link MobSpawnType} name, recorded at the first
 * {@code finalizeSpawn} (see {@link MobStacker#recordOrigin}), so {@code /mobstacker inspect} can say
 * exactly what it was and the grouping below can change without touching saved mobs. A mob that has
 * nothing recorded - it predates 1.10.0, or it hatched from an egg, which vanilla creates without
 * {@code finalizeSpawn} - counts as natural.
 */
public enum SpawnOrigin {
    NATURAL("stackNaturalMobs", "spawned on its own"),
    SPAWNER("stackSpawnerMobs", "came from a monster spawner"),
    BRED("stackBredMobs", "was born from breeding"),
    PLACED("stackPlacedMobs", "was put there by a player: spawn egg, dispenser, bucket or command");

    private final String settingId;
    private final String description;

    SpawnOrigin(String settingId, String description) {
        this.settingId = settingId;
        this.description = description;
    }

    /** The setting that decides whether mobs of this origin stack. */
    public String settingId() {
        return settingId;
    }

    /** A sentence fragment for a player: "the mob ..." */
    public String description() {
        return description;
    }

    /** The group a recorded {@link MobSpawnType} name belongs to; nothing recorded is natural. */
    public static SpawnOrigin of(String recorded) {
        if (recorded == null || recorded.isEmpty()) {
            return NATURAL;
        }
        return switch (recorded) {
            case "SPAWNER" -> SPAWNER;
            case "BREEDING" -> BRED;
            case "SPAWN_EGG", "DISPENSER", "BUCKET", "COMMAND" -> PLACED;
            // Natural spawns, chunk generation, structures, patrols, raids, reinforcements, jockeys,
            // a vex from an evoker, a warden from a shrieker, and a conversion with nothing to inherit.
            default -> NATURAL;
        };
    }
}
