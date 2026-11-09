package com.frikinjay.mobstacker.client;

import com.frikinjay.mobstacker.config.StackRegion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * One line at the top left of the screen saying which region the player is standing in, while the
 * region boxes are switched on.
 *
 * <p>With boxes on, the player is looking at regions, and the one question the boxes cannot answer
 * from inside is "which one am I in" - inside a box there is nothing but faces all round. Named here
 * is the region whose settings apply at the player's feet (see
 * {@link MobStackerClientRegions#containing}); when several overlap, how many more there are. Off
 * with the boxes, with the HUD hidden (F1) and under the debug screen (F3), which uses that corner.
 *
 * <p>Drawn by the loader's HUD hook, after the game's own HUD, through {@link #render}.
 */
public final class MobStackerRegionHud {

    private MobStackerRegionHud() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.1 {
        /*boolean debugScreen = minecraft.getDebugOverlay().showDebugScreen();
        boolean hidden = false; // Fabric draws HUD elements only while the HUD is shown
        *///?} else if >=1.20.2 {
        /*boolean debugScreen = minecraft.gui.getDebugOverlay().showDebugScreen();
        boolean hidden = minecraft.options.hideGui;
        *///?} else {
        boolean debugScreen = minecraft.options.renderDebug;
        boolean hidden = minecraft.options.hideGui;
        //?}
        if (minecraft.player == null || hidden || debugScreen
                || !MobStackerRegionOverlay.anythingShowing()) {
            return;
        }
        BlockPos feet = minecraft.player.blockPosition();
        List<MobStackerClientRegions.View> here = MobStackerClientRegions.containing(feet.getX(), feet.getY(), feet.getZ());
        MutableComponent line = Component.literal("Region: ").withStyle(ChatFormatting.GRAY);
        if (here.isEmpty()) {
            line.append(Component.literal("none").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            MobStackerClientRegions.View applies = here.get(0);
            line.append(Component.literal(applies.name()).withStyle(applies.color().format()))
                    .append(Component.literal(" (" + (applies.type() == StackRegion.Type.DENY ? "deny" : "allow")
                            + ", priority " + applies.priority()
                            + (applies.wholeDimension() ? ", whole dimension" : "") + ")").withStyle(ChatFormatting.GRAY));
            if (here.size() > 1) {
                line.append(Component.literal("  +" + (here.size() - 1) + " more here").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        Gfx.text(graphics, minecraft.font, line, 4, 4, 0xFFFFFF, true);
    }
}
