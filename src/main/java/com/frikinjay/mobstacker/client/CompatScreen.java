package com.frikinjay.mobstacker.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What every screen of the mod needs from {@link Screen} that Minecraft reshaped between versions,
 * in one place, so a screen is written once:
 * <ul>
 *   <li>{@link #draw} instead of {@code render} (26.1: {@code extractRenderState}), with
 *       {@link #drawBackground} at the top and {@link #superRender} for the widgets - the 1.20.1 order.
 *       From 1.20.2 {@code Screen.render} draws the background itself (after the screen drew its text,
 *       which it would cover), and from 1.20.5 the background is a blur that may be drawn once a frame,
 *       so that second one is skipped here;</li>
 *   <li>{@link #onKey}, {@link #onChar}, {@link #onClick} instead of {@code keyPressed},
 *       {@code charTyped}, {@code mouseClicked}, which take event objects from 1.21.9 (unconfirmed),
 *       with {@link #superKey}, {@link #superChar}, {@link #superClick} for what {@code Screen} does;</li>
 *   <li>{@link #onScroll} for the wheel, whatever {@code mouseScrolled} looks like (1.20.2 added a
 *       horizontal amount);</li>
 *   <li>the text box calls that changed: {@link #cursorToEnd}, {@link #cursorTo}, {@link #tickBox},
 *       {@link #typeInto}; and {@link #controlDown}.</li>
 * </ul>
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    // ---- drawing ----

    /** The screen's drawing; the default is {@link #superRender}, the widgets. */
    protected void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        superRender(graphics, mouseX, mouseY, partialTick);
    }

    //? if >=26.1 {
    /*@Override
    public final void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(graphics, mouseX, mouseY, partialTick);
    }

    /^* What {@code Screen} draws: the widgets (its background is drawn by {@link #drawBackground}). ^/
    protected void superRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /^* The screen's background, drawn first in {@link #draw}. ^/
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    /^* Drawn by {@link #drawBackground} already; {@code Screen} would draw it again over the text. ^/
    @Override
    public void extractBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
    *///?} else if >=1.20.2 {
    /*@Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(graphics, mouseX, mouseY, partialTick);
    }

    /^* What {@code Screen} draws: the widgets (its background is drawn by {@link #drawBackground}). ^/
    protected void superRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /^* The screen's background, drawn first in {@link #draw}. ^/
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    /^* Drawn by {@link #drawBackground} already; {@code Screen.render} would draw it again over the text. ^/
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
    *///?} else {
    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(graphics, mouseX, mouseY, partialTick);
    }

    /** What {@code Screen} draws: the widgets (its background is drawn by {@link #drawBackground}). */
    protected void superRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The screen's background, drawn first in {@link #draw}. */
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics);
    }
    //?}

    // ---- keys, characters, clicks ----

    /** A key went down. The default is {@link #superKey}. */
    protected boolean onKey(int keyCode, int scanCode, int modifiers) {
        return superKey(keyCode, scanCode, modifiers);
    }

    /** A character was typed. The default is {@link #superChar}. */
    protected boolean onChar(char typed, int modifiers) {
        return superChar(typed, modifiers);
    }

    /** A mouse button went down. The default is {@link #superClick}. */
    protected boolean onClick(double mouseX, double mouseY, int button) {
        return superClick(mouseX, mouseY, button);
    }

    //? if >=26.1 {
    /*@Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return onKey(event.key(), event.keycode(), event.modifiers());
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return onChar((char) event.codepoint(), 0);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return onClick(event.x(), event.y(), event.button());
    }

    protected boolean superKey(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }

    protected boolean superChar(char typed, int modifiers) {
        return super.charTyped(new net.minecraft.client.input.CharacterEvent(typed));
    }

    protected boolean superClick(double mouseX, double mouseY, int button) {
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY,
                new net.minecraft.client.input.MouseButtonInfo(button, 0)), false);
    }

    /^* Whether Ctrl (Cmd on a Mac) is held, by the modifiers of a key event. ^/
    protected static boolean controlDown(int modifiers) {
        return new net.minecraft.client.input.KeyEvent(0, 0, modifiers).hasControlDown();
    }

    /^* Types a character into a text box, as the screen would. ^/
    protected static boolean typeInto(EditBox box, char typed, int modifiers) {
        return box.charTyped(new net.minecraft.client.input.CharacterEvent(typed));
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKey(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        return onChar(typed, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onClick(mouseX, mouseY, button);
    }

    protected boolean superKey(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected boolean superChar(char typed, int modifiers) {
        return super.charTyped(typed, modifiers);
    }

    protected boolean superClick(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Whether Ctrl (Cmd on a Mac) is held. */
    protected static boolean controlDown(int modifiers) {
        return Screen.hasControlDown();
    }

    /** Types a character into a text box, as the screen would. */
    protected static boolean typeInto(EditBox box, char typed, int modifiers) {
        return box.charTyped(typed, modifiers);
    }
    //?}

    // ---- the mouse wheel ----

    //? if >=1.20.2 {
    /*@Override
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

    // ---- text boxes ----

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
