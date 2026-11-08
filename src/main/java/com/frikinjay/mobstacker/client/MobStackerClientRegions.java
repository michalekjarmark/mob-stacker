package com.frikinjay.mobstacker.client;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.RegionBiomes;
import com.frikinjay.mobstacker.config.StackColor;
import com.frikinjay.mobstacker.config.StackRegion;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The regions as the client can see them, from whichever of the two sources applies.
 *
 * <p>In singleplayer the client and the server share one process, so the config object is right
 * there. On a real server the client only knows what the config-sync snapshot told it. Both the
 * region screen and the in-world overlay need the same list, and when each looked it up for itself
 * they were one edit away from disagreeing — so the lookup lives here once.
 */
public final class MobStackerClientRegions {

    /**
     * A region reduced to what a client does anything with. The corners are kept as they were given,
     * which is what the editor shows; the box's extent is worked out from them for the overlay.
     */
    public record View(String name, StackRegion.Type type, String dimension,
                       int x1, int y1, int z1, int x2, int y2, int z2,
                       int priority, StackColor color, boolean colorChosen,
                       boolean enabled, boolean wholeDimension, List<String> biomes) {

        public int minX() { return Math.min(x1, x2); }
        public int minY() { return Math.min(y1, y2); }
        public int minZ() { return Math.min(z1, z2); }
        public int maxX() { return Math.max(x1, x2); }
        public int maxY() { return Math.max(y1, y2); }
        public int maxZ() { return Math.max(z1, z2); }

        public String bounds() {
            if (wholeDimension) {
                return "(the whole dimension)";
            }
            return "[" + x1 + ", " + y1 + ", " + z1 + "] -> [" + x2 + ", " + y2 + ", " + z2 + "]";
        }

        /** Whether the overlay has a box to draw: not for a whole dimension, which has none. */
        public boolean hasBox() {
            return !wholeDimension;
        }

        public int[] corners() {
            return new int[]{x1, y1, z1, x2, y2, z2};
        }

        /**
         * As the server's {@code StackRegion.contains}: never while off, everywhere for a whole
         * dimension, and only in its biomes when it has a filter - looked up in the level the
         * player is in.
         */
        public boolean contains(net.minecraft.world.level.Level level, int x, int y, int z) {
            if (!contains(x, y, z)) {
                return false;
            }
            return biomes.isEmpty() || (level != null && RegionBiomes.matches(
                    level.getBiome(new net.minecraft.core.BlockPos(x, y, z)), biomes));
        }

        /** The area alone, without the biome filter. */
        public boolean contains(int x, int y, int z) {
            if (!enabled) {
                return false;
            }
            return wholeDimension
                    || (x >= minX() && x <= maxX() && y >= minY() && y <= maxY() && z >= minZ() && z <= maxZ());
        }

        public long volume() {
            if (wholeDimension) {
                return Long.MAX_VALUE;
            }
            return (long) (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
        }
    }

    private MobStackerClientRegions() {
    }

    public static List<View> all() {
        List<View> out = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer()) {
            for (StackRegion region : MobStacker.config.getRegions()) {
                int[] c = region.getCorners();
                out.add(new View(region.getName(), region.getType(),
                        region.getDimension() == null ? "?" : region.getDimension(),
                        c[0], c[1], c[2], c[3], c[4], c[5],
                        region.getPriority(), region.effectiveColor(), region.getColor() != null,
                        region.isEnabled(), region.isWholeDimension(), List.copyOf(region.getBiomes())));
            }
            return out;
        }
        for (MobStackerClientNetworking.RegionInfo info : MobStackerClientNetworking.regions()) {
            out.add(new View(info.name(),
                    "DENY".equalsIgnoreCase(info.type()) ? StackRegion.Type.DENY : StackRegion.Type.ALLOW,
                    info.dimension(),
                    info.x1(), info.y1(), info.z1(),
                    info.x2(), info.y2(), info.z2(),
                    info.priority(), info.overlayColor(), info.chosenColor() != null,
                    info.enabled(), info.wholeDimension(), info.biomes()));
        }
        return out;
    }

    /**
     * The regions this block is in, in the dimension the player is in, the one whose settings apply
     * first: the highest priority, and between equal priorities the smaller region - the same rule the
     * server follows ({@code MobStacker.regionAt}), so what the player is told matches what happens.
     */
    public static List<View> containing(int x, int y, int z) {
        List<View> out = new ArrayList<>();
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        for (View view : inCurrentDimension()) {
            if (view.contains(level, x, y, z)) {
                out.add(view);
            }
        }
        out.sort((a, b) -> a.priority() != b.priority()
                ? Integer.compare(b.priority(), a.priority())
                : Long.compare(a.volume(), b.volume()));
        return out;
    }

    /** The regions in the dimension the player is currently looking at, or an empty list. */
    public static List<View> inCurrentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        String here = minecraft.level.dimension().location().toString();
        List<View> out = new ArrayList<>();
        for (View view : all()) {
            if (here.equals(view.dimension())) {
                out.add(view);
            }
        }
        return out;
    }
}
