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

    /** A region reduced to what a client does anything with. */
    public record View(String name, StackRegion.Type type, String dimension,
                       int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                       int priority, StackColor color, boolean colorChosen) {

        public String bounds() {
            return "[" + minX + ", " + minY + ", " + minZ + "] -> [" + maxX + ", " + maxY + ", " + maxZ + "]";
        }

        public int[] corners() {
            return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
        }
    }

    private MobStackerClientRegions() {
    }

    public static List<View> all() {
        List<View> out = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer()) {
            for (StackRegion region : MobStacker.config.getRegions()) {
                out.add(new View(region.getName(), region.getType(),
                        region.getDimension() == null ? "?" : region.getDimension(),
                        region.getMinX(), region.getMinY(), region.getMinZ(),
                        region.getMaxX(), region.getMaxY(), region.getMaxZ(),
                        region.getPriority(), region.effectiveColor(), region.getColor() != null));
            }
            return out;
        }
        for (MobStackerClientNetworking.RegionInfo info : MobStackerClientNetworking.regions()) {
            out.add(new View(info.name(),
                    "DENY".equalsIgnoreCase(info.type()) ? StackRegion.Type.DENY : StackRegion.Type.ALLOW,
                    info.dimension(),
                    info.minX(), info.minY(), info.minZ(),
                    info.maxX(), info.maxY(), info.maxZ(),
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
