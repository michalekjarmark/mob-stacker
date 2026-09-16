package com.frikinjay.mobstacker.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An axis-aligned cuboid region bound to a single dimension.
 * Regions are either {@link Type#ALLOW} (stacking is permitted here) or
 * {@link Type#DENY} (stacking is forbidden here, overriding everything else).
 * Custom shapes can be approximated by adding several cuboid regions.
 * <p>
 * A region can also carry its own settings: any number of the ids from
 * {@link MobStackerSettings} mapped to the value that applies inside it. The map is sparse — a
 * setting that is not listed simply follows the global config — so regions stay small in the config
 * file and only differ where you asked them to.
 */
public class StackRegion {

    public enum Type {
        ALLOW,
        DENY
    }

    private String name;
    private String dimension;
    private Type type;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    // Settings that differ inside this region, keyed by the same ids the commands and the GUI use.
    // Volatile because in singleplayer the config screen reads this from the client thread while the
    // integrated server writes it, and a stale reference would leave the screen showing old state.
    private volatile Map<String, String> settings;
    // Decides which region wins where two overlap: higher first, then the smaller region.
    private int priority;

    // Required for Gson deserialization.
    public StackRegion() {
    }

    public StackRegion(String name, String dimension, Type type,
                       int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.dimension = dimension;
        this.type = type;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String getName() {
        return name;
    }

    public String getDimension() {
        return dimension;
    }

    public Type getType() {
        return type != null ? type : Type.ALLOW;
    }

    public boolean isAllow() {
        return getType() == Type.ALLOW;
    }

    public boolean isDeny() {
        return getType() == Type.DENY;
    }

    public boolean contains(String dimension, int x, int y, int z) {
        return this.dimension != null && this.dimension.equals(dimension)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /** How many blocks this region covers, used to let a smaller region win an overlap. */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** The settings overridden here, keyed by setting id. Never null. */
    public Map<String, String> getSettings() {
        return settings == null ? Collections.emptyMap() : Collections.unmodifiableMap(settings);
    }

    /** The value this region gives {@code id}, or null when it follows the global config. */
    public String getSetting(String id) {
        return settings == null ? null : settings.get(id);
    }

    /** @return true when the stored value actually changed */
    public boolean setSetting(String id, String value) {
        if (settings == null) {
            settings = new LinkedHashMap<>();
        }
        return !value.equals(settings.put(id, value));
    }

    /** @return true when an override was actually removed */
    public boolean clearSetting(String id) {
        if (settings == null) {
            return false;
        }
        boolean removed = settings.remove(id) != null;
        if (settings.isEmpty()) {
            settings = null;
        }
        return removed;
    }

    public String describeBounds() {
        return "[" + minX + ", " + minY + ", " + minZ + "] -> [" + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
