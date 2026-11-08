package com.frikinjay.mobstacker.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What every screen of the mod needs from {@link Screen} that Minecraft reshaped between versions,
 * in one place, so a screen is written once:
 * <ul>
 *   <li>{@link #drawBackground} at the top of {@code render}, then {@code super.render} for the
 *       widgets - the 1.20.1 order. From 1.20.2 {@code Screen.render} draws the background itself
 *       (after the screen drew its text, which it would cover), and from 1.20.5 the background is a
 *       blur that may be drawn once a frame, so that second one is skipped here;</li>
 *   <li>{@link #onScroll} for the wheel, whatever {@code mouseScrolled} looks like (1.20.2 added a
 *       horizontal amount);</li>
 *   <li>the text box calls that changed: {@link #cursorToEnd}, {@link #cursorTo}, {@link #tickBox}.</li>
 * </ul>
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    /** The screen's background, drawn first in {@code render}. */
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if >=1.20.2 {
        /*super.renderBackground(graphics, mouseX, mouseY, partialTick);
        *///?} else {
        super.renderBackground(graphics);
        //?}
    }

    //? if >=1.20.2 {
    /*/^* Drawn by {@link #drawBackground} already; {@code Screen.render} would draw it again over the text. ^/
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return onScroll(mouseX, mouseY, scrollY);
    }

    /^* What {@code Screen} does with the wheel: hands it to the widget under the mouse. ^/
    protected boolean superScroll(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(mouseX, mouseY, 0.0, delta);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return onScroll(mouseX, mouseY, delta);
    }

    /** What {@code Screen} does with the wheel: hands it to the widget under the mouse. */
    protected boolean superScroll(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    //?}

    /** The mouse wheel turned by {@code delta} (up is positive). The default is {@link #superScroll}. */
    protected boolean onScroll(double mouseX, double mouseY, double delta) {
        return superScroll(mouseX, mouseY, delta);
    }

    /** Puts the cursor at the end of the text, selecting nothing. */
    protected static void cursorToEnd(EditBox box) {
        //? if >=1.20.2 {
        /*box.moveCursorToEnd(false);
        *///?} else {
        box.moveCursorToEnd();
        //?}
    }

    /** Puts the cursor at {@code position}, selecting nothing. */
    protected static void cursorTo(EditBox box, int position) {
        //? if >=1.20.2 {
        /*box.moveCursorTo(position, false);
        *///?} else {
        box.moveCursorTo(position);
        //?}
    }

    /** Blinks a text box's cursor, which until 1.20.2 the screen had to tick; from then it blinks by itself. */
    protected static void tickBox(EditBox box) {
        //? if <1.20.2 {
        box.tick();
        //?}
    }
}
