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
    // The colour the region is drawn in when a player switches its overlay on. Null means "no colour
    // chosen", which reads as green for an allow region and red for a deny one — see effectiveColor.
    private StackColor color;

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

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    /**
     * Moves or resizes the region, keeping everything else about it — its settings, its priority and
     * its name. Redrawing the area used to mean deleting the region and adding it again, which threw
     * all of that away.
     */
    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public boolean contains(String dimension, int x, int y, int z) {
        return this.dimension != null && this.dimension.equals(dimension)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /** The colour explicitly set for this region, or null if none was. */
    public StackColor getColor() {
        return color;
    }

    public void setColor(StackColor color) {
        this.color = color;
    }

    /**
     * The colour the overlay actually draws this region in. A region nobody has given a colour to
     * falls back to what its kind means — green for allow, red for deny — so switching the overlay
     * on tells you something useful before you have coloured anything.
     */
    public StackColor effectiveColor() {
        if (color != null) {
            return color;
        }
        return isDeny() ? StackColor.RED : StackColor.GREEN;
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
