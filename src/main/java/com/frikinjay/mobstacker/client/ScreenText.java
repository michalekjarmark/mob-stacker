package com.frikinjay.mobstacker.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * A line of text that has to stay on the screen. A centred line wider than the window runs off both
 * edges at once, which is how the region page lost the ends of two lines in rounds 13 and 14: its
 * footer, and the answer to an edit, which names every biome of the region. Such a line is cut to the
 * width with an ellipsis instead, and hovering it shows the whole text.
 */
final class ScreenText {
    private static final Component ELLIPSIS = Component.literal("…");

    private ScreenText() {
    }

    /**
     * A line as it was drawn: where, how wide, and the whole text for the tooltip.
     *
     * @param cut whether the text did not fit and was cut
     */
    record Line(int left, int top, int width, Component text, boolean cut) {
        boolean isMouseOver(Font font, int mouseX, int mouseY) {
            return mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + font.lineHeight;
        }

        /**
         * The whole text in a tooltip, when the line was cut and the mouse is on it.
         *
         * @return true when the tooltip was drawn
         */
        boolean renderTooltip(GuiGraphics graphics, Font font, int screenWidth, int mouseX, int mouseY) {
            if (!cut || !isMouseOver(font, mouseX, mouseY)) {
                return false;
            }
            ScreenTooltip.render(graphics, font, List.of(text), screenWidth, mouseX, mouseY);
            return true;
        }
    }

    /** The text as it fits into {@code maxWidth}: whole, or cut with an ellipsis. */
    static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, ELLIPSIS));
    }

    /** Draws the text centred on {@code centerX}, cut to {@code maxWidth}, and says where it went. */
    static Line drawCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y, int maxWidth) {
        FormattedCharSequence line = fit(font, text, maxWidth);
        int width = font.width(line);
        int left = centerX - width / 2;
        Gfx.text(graphics, font, line, left, y, 0xFFFFFF);
        return new Line(left, y, width, text, font.width(text) > maxWidth);
    }
}
