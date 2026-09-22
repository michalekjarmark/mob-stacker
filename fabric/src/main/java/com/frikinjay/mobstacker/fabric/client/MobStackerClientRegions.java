package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.MobStacker;
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
                       int priority, StackColor color, boolean colorChosen) {

        public int minX() { return Math.min(x1, x2); }
        public int minY() { return Math.min(y1, y2); }
        public int minZ() { return Math.min(z1, z2); }
        public int maxX() { return Math.max(x1, x2); }
        public int maxY() { return Math.max(y1, y2); }
        public int maxZ() { return Math.max(z1, z2); }

        public String bounds() {
            return "[" + x1 + ", " + y1 + ", " + z1 + "] -> [" + x2 + ", " + y2 + ", " + z2 + "]";
        }

        public int[] corners() {
            return new int[]{x1, y1, z1, x2, y2, z2};
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
                        region.getPriority(), region.effectiveColor(), region.getColor() != null));
            }
            return out;
        }
        for (MobStackerClientNetworking.RegionInfo info : MobStackerClientNetworking.regions()) {
            out.add(new View(info.name(),
                    "DENY".equalsIgnoreCase(info.type()) ? StackRegion.Type.DENY : StackRegion.Type.ALLOW,
                    info.dimension(),
                    info.x1(), info.y1(), info.z1(),
                    info.x2(), info.y2(), info.z2(),
                    info.priority(), info.overlayColor(), info.chosenColor() != null));
        }
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
