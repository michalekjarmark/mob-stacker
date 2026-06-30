package com.frikinjay.mobstacker.config;

/**
 * An axis-aligned cuboid region bound to a single dimension.
 * Regions are either {@link Type#ALLOW} (stacking is permitted here) or
 * {@link Type#DENY} (stacking is forbidden here, overriding everything else).
 * Custom shapes can be approximated by adding several cuboid regions.
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

    public String describeBounds() {
        return "[" + minX + ", " + minY + ", " + minZ + "] -> [" + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
