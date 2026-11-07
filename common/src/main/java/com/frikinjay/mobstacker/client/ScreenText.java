package com.frikinjay.mobstacker.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * A line of text that has to stay on the screen. A centred line wider than the window runs off both
 * edges at once, which is how the region page's footer lost its ends in round 13 (a region with
 * biomes, on a narrow window). Such a line is cut to the width with an ellipsis instead; the screen
 * says the rest in a tooltip.
 */
final class ScreenText {
    private static final Component ELLIPSIS = Component.literal("…");

    private ScreenText() {
    }

    /** The text as it fits into {@code maxWidth}: whole, or cut with an ellipsis. */
    static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, ELLIPSIS));
    }

    /**
     * Draws the text centred on {@code centerX}, cut to {@code maxWidth}.
     *
     * @return how wide it came out, for a screen that puts a tooltip over it
     */
    static int drawCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int maxWidth) {
        FormattedCharSequence line = fit(font, text, maxWidth);
        int width = font.width(line);
        graphics.drawString(font, line, centerX - width / 2, y, 0xFFFFFF);
        return width;
    }
}
