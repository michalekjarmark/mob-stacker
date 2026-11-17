package com.frikinjay.mobstacker.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The text the screens draw, the same on every version. From 1.21.6 a text colour is ARGB, and one with
 * no alpha (the 0xFFFFFF every screen of the mod passes, as 1.20.1 wants it) is not drawn at all, so a
 * colour without alpha is made opaque here; from 26.1 (unconfirmed) {@code drawString} is {@code text}
 * and {@code drawCenteredString} is {@code centeredText}.
 */
public final class Gfx {
    private Gfx() {
    }

    /** Where this frame's camera is (the game renderer's main camera). */
    public static net.minecraft.world.phys.Vec3 cameraPosition(net.minecraft.client.Minecraft minecraft) {
        //? if >=26.2 {
        /*return minecraft.gameRenderer.mainCamera().position();
        *///?} else if >=1.21.11 {
        /*return minecraft.gameRenderer.getMainCamera().position();
        *///?} else {
        return minecraft.gameRenderer.getMainCamera().getPosition();
        //?}
    }

    //? if >=26.2 {
    /*/^* A name tag line, submitted as vanilla submits its own. ^/
    public static void submitNameTag(net.minecraft.client.renderer.SubmitNodeCollector collector,
                                     com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.phys.Vec3 at,
                                     Component text, boolean seeThrough,
                                     net.minecraft.client.renderer.entity.state.EntityRenderState state,
                                     net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        collector.submitNameTag(pose, at, 0, text, seeThrough, state.lightCoords, camera);
    }
    *///?} else if >=26.1 {
    /*/^* A name tag line, submitted as vanilla submits its own (26.1 also passes the camera's distance). ^/
    public static void submitNameTag(net.minecraft.client.renderer.SubmitNodeCollector collector,
                                     com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.phys.Vec3 at,
                                     Component text, boolean seeThrough,
                                     net.minecraft.client.renderer.entity.state.EntityRenderState state,
                                     net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        collector.submitNameTag(pose, at, 0, text, seeThrough, state.lightCoords, state.distanceToCameraSq, camera);
    }
    *///?} else if >=1.21.9 {
    /*/^* A name tag line, submitted as vanilla submits its own (the camera's state is not under level yet). ^/
    public static void submitNameTag(net.minecraft.client.renderer.SubmitNodeCollector collector,
                                     com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.phys.Vec3 at,
                                     Component text, boolean seeThrough,
                                     net.minecraft.client.renderer.entity.state.EntityRenderState state,
                                     net.minecraft.client.renderer.state.CameraRenderState camera) {
        collector.submitNameTag(pose, at, 0, text, seeThrough, state.lightCoords, state.distanceToCameraSq, camera);
    }
    *///?}

    /** A colour as the version wants it: opaque when it says no alpha. */
    static int color(int rgb) {
        //? if >=1.21.6 {
        /*return (rgb & 0xFF000000) == 0 ? rgb | 0xFF000000 : rgb;
        *///?} else {
        return rgb;
        //?}
    }

    /** A text box's colour: like the text the screens draw, opaque when it says no alpha. */
    public static void textColor(EditBox box, int rgb) {
        box.setTextColor(color(rgb));
    }

    public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        //? if >=26.1 {
        /*graphics.text(font, text, x, y, color(color));
        *///?} else {
        graphics.drawString(font, text, x, y, color(color));
        //?}
    }

    public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        /*graphics.text(font, text, x, y, color(color));
        *///?} else {
        graphics.drawString(font, text, x, y, color(color));
        //?}
    }

    public static void text(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        //? if >=26.1 {
        /*graphics.text(font, text, x, y, color(color));
        *///?} else {
        graphics.drawString(font, text, x, y, color(color));
        //?}
    }

    public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*graphics.text(font, text, x, y, color(color), shadow);
        *///?} else {
        graphics.drawString(font, text, x, y, color(color), shadow);
        //?}
    }

    public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*graphics.text(font, text, x, y, color(color), shadow);
        *///?} else {
        graphics.drawString(font, text, x, y, color(color), shadow);
        //?}
    }

    public static void text(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*graphics.text(font, text, x, y, color(color), shadow);
        *///?} else {
        graphics.drawString(font, text, x, y, color(color), shadow);
        //?}
    }

    public static void centered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        //? if >=26.1 {
        /*graphics.centeredText(font, text, centerX, y, color(color));
        *///?} else {
        graphics.drawCenteredString(font, text, centerX, y, color(color));
        //?}
    }

    public static void centered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        //? if >=26.1 {
        /*graphics.centeredText(font, text, centerX, y, color(color));
        *///?} else {
        graphics.drawCenteredString(font, text, centerX, y, color(color));
        //?}
    }

    public static void centered(GuiGraphics graphics, Font font, FormattedCharSequence text, int centerX, int y, int color) {
        //? if >=26.1 {
        /*graphics.centeredText(font, text, centerX, y, color(color));
        *///?} else {
        graphics.drawCenteredString(font, text, centerX, y, color(color));
        //?}
    }

    /** A tooltip of these lines at the mouse, drawn over everything else. */
    public static void tooltip(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        //? if >=1.21.6 {
        /*graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
        *///?} else {
        graphics.renderTooltip(font, lines, mouseX, mouseY);
        //?}
    }

    /**
     * Draws {@code over} in front of what the screen drew so far (a completion list over the widgets
     * under it): pushed towards the viewer until 1.21.6, in the next layer of the GUI after it.
     */
    public static void inFront(GuiGraphics graphics, float z, Runnable over) {
        //? if >=1.21.6 {
        /*graphics.nextStratum();
        over.run();
        *///?} else {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, z);
        over.run();
        graphics.pose().popPose();
        //?}
    }
}
