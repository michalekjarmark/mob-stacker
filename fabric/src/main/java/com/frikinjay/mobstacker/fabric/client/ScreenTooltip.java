package com.frikinjay.mobstacker.fabric.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a tooltip whose lines are wrapped to the window instead of running off its edge.
 * <p>
 * Vanilla's component tooltip draws each line whole, so a long setting description — and they get
 * long, because a description has to explain what a setting does without the README — was simply cut
 * off at the right edge of the screen. Each line is split to a width that leaves room for the
 * tooltip to sit on either side of the cursor, so it stays readable however narrow the window is.
 */
final class ScreenTooltip {
    /** Widest a tooltip may get; below that it follows the window, with room for the frame. */
    private static final int MAX_WIDTH = 300;
    private static final int MIN_WIDTH = 140;

    private ScreenTooltip() {
    }

    static void render(GuiGraphics graphics, Font font, List<Component> lines,
                       int screenWidth, int mouseX, int mouseY) {
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, screenWidth - 80));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            List<FormattedCharSequence> split = font.split(line, width);
            if (split.isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY); // keep a deliberate blank line
            } else {
                wrapped.addAll(split);
            }
        }
        graphics.renderTooltip(font, wrapped, mouseX, mouseY);
    }
}
