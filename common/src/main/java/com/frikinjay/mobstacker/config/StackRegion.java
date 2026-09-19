package com.frikinjay.mobstacker.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
public class StackRegion implements MobLists.Holder {

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
    // The region's own mob lists. Null means "not overridden here" and is NOT the same as an empty
    // list, which means "nothing"; that difference is the whole point of hasList. Volatile for the
    // same reason as settings — the singleplayer config screen reads them off the client thread.
    private volatile List<String> ignoredEntities;
    private volatile List<String> ignoredMods;
    private volatile List<String> allowedEntities;
    private volatile List<String> allowedMods;
    // Per-type ceilings that apply only here, entity id -> size. Sparse like settings: a type that
    // is not named follows the region's own maxStackSize, and failing that the global one.
    private volatile Map<String, Integer> maxStackSizes;

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

    /**
     * Renames the region in place, so everything it carries — its area, its settings, its priority
     * and its colour — survives. Go through {@code RegionEdit.rename}, which is what checks the new
     * name is usable and not already taken.
     */
    public void setName(String name) {
        this.name = name;
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

    /** The backing list, or null when this region does not override it. */
    private List<String> backing(MobListKind kind) {
        switch (kind) {
            case DENY_ENTITIES:
                return ignoredEntities;
            case DENY_MODS:
                return ignoredMods;
            case ALLOW_ENTITIES:
                return allowedEntities;
            case ALLOW_MODS:
            default:
                return allowedMods;
        }
    }

    private void store(MobListKind kind, List<String> list) {
        switch (kind) {
            case DENY_ENTITIES -> ignoredEntities = list;
            case DENY_MODS -> ignoredMods = list;
            case ALLOW_ENTITIES -> allowedEntities = list;
            case ALLOW_MODS -> allowedMods = list;
        }
    }

    @Override
    public List<String> getList(MobListKind kind) {
        return MobLists.view(backing(kind));
    }

    @Override
    public boolean hasList(MobListKind kind) {
        return backing(kind) != null;
    }

    @Override
    public boolean addToList(MobListKind kind, String entry) {
        String value = MobLists.normalise(kind, entry);
        if (value.isEmpty()) {
            return false;
        }
        List<String> list = backing(kind);
        if (list == null) {
            // The first entry is also what turns the override on: until now this region inherited.
            list = new ArrayList<>();
            store(kind, list);
        } else if (list.contains(value)) {
            return false;
        }
        list.add(value);
        return true;
    }

    @Override
    public boolean removeFromList(MobListKind kind, String entry) {
        List<String> list = backing(kind);
        // An emptied list is kept, not dropped: "nothing stacks here" is a thing a region can mean,
        // and silently falling back to the global list instead would be the opposite of what was asked.
        return list != null && list.remove(MobLists.normalise(kind, entry));
    }

    @Override
    public void clearList(MobListKind kind) {
        store(kind, null);
    }

    @Override
    public void setList(MobListKind kind, List<String> entries) {
        List<String> copy = new ArrayList<>();
        for (String entry : entries) {
            String value = MobLists.normalise(kind, entry);
            if (!value.isEmpty() && !copy.contains(value)) {
                copy.add(value);
            }
        }
        // Stored even when empty: that is how a region says "nothing", as opposed to saying nothing.
        store(kind, copy);
    }

    /** The ceilings set here, entity id -> size. Never null. */
    public Map<String, Integer> getMaxStackSizes() {
        return maxStackSizes == null ? Collections.emptyMap() : Collections.unmodifiableMap(maxStackSizes);
    }

    /** Whether this region sets any per-type ceiling. Cheap; see the note on the global one. */
    public boolean hasMaxStackSizes() {
        Map<String, Integer> sizes = maxStackSizes;
        return sizes != null && !sizes.isEmpty();
    }

    /** The ceiling this region gives that entity id, or null when it does not set one. */
    public Integer getMaxStackSize(String entityId) {
        Map<String, Integer> sizes = maxStackSizes;
        return sizes == null ? null : sizes.get(MobLists.normaliseEntityId(entityId));
    }

    /** Sets a ceiling here, or drops it when {@code size} is null. @return true when something changed */
    public boolean setMaxStackSize(String entityId, Integer size) {
        String key = MobLists.normaliseEntityId(entityId);
        if (size == null) {
            if (maxStackSizes == null) {
                return false;
            }
            boolean removed = maxStackSizes.remove(key) != null;
            if (maxStackSizes.isEmpty()) {
                maxStackSizes = null;
            }
            return removed;
        }
        if (maxStackSizes == null) {
            maxStackSizes = new LinkedHashMap<>();
        }
        return !Integer.valueOf(Math.max(1, size)).equals(maxStackSizes.put(key, Math.max(1, size)));
    }

    public String describeBounds() {
        return "[" + minX + ", " + minY + ", " + minZ + "] -> [" + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
