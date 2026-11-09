package com.frikinjay.mobstacker.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * The completion lists of one screen, one per box (1.12.0). A page used to share one list, attached
 * to whichever item box was built last, so on the Separator page - which has two since the
 * assimilator came - typing in {@code separatorItem} opened the list under {@code assimilatorItem}.
 * A screen clears this in {@code init}, adds a list per box it builds, and hands over its keys,
 * clicks and a chance to draw.
 */
final class IdCompletions {
    private final List<IdCompletion> all = new ArrayList<>();

    void clear() {
        for (IdCompletion completion : all) {
            completion.attach(null);
        }
        all.clear();
    }

    /** A new list of item ids, for the caller to attach to its box. */
    IdCompletion addItems() {
        IdCompletion completion = new IdCompletion(IdCompletion::itemIds);
        all.add(completion);
        return completion;
    }

    /**
     * Tab, the arrows, Esc and Enter for the list of the focused box.
     *
     * @return true when the key was the list's
     */
    boolean keyPressed(int keyCode, GuiEventListener focused) {
        for (IdCompletion completion : all) {
            if (!completion.isFor(focused)) {
                continue;
            }
            if (completion.handleKey(keyCode, true)) {
                return true;
            }
            if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                    && completion.shouldEnterComplete()) {
                completion.accept();
                return true;
            }
            return false;
        }
        return false;
    }

    /** Gives every list the click, so the ones it misses close. @return true when one took it. */
    boolean mouseClicked(double mouseX, double mouseY, int screenHeight) {
        boolean taken = false;
        for (IdCompletion completion : all) {
            taken |= completion.mouseClicked(mouseX, mouseY, screenHeight);
        }
        return taken;
    }

    void render(GuiGraphics graphics, int screenHeight, int mouseX, int mouseY) {
        for (IdCompletion completion : all) {
            completion.render(graphics, screenHeight, mouseX, mouseY);
        }
    }
}
