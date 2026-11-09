package com.frikinjay.mobstacker.client;

import com.frikinjay.mobstacker.compat.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Completion for a box that takes a registry id, in the same spirit as the command line's
 * suggestions: an id has to be spelled exactly right, and nobody remembers whether it is
 * {@code minecraft:zombified_piglin} or {@code zombie_pigman} until the game says so.
 *
 * <p>Tab, Enter or a click takes the highlighted line, the arrows walk the list, Esc closes it
 * without closing the screen. It was written for the mob list editor in 1.9.0 and lifted out here in
 * 1.9.1, so {@code separatorItem} on the config and region screens gets the same thing rather than a
 * second copy that would drift from the first.
 *
 * <p>A screen owns one of these per box and has to hand it three things it cannot see for itself:
 * the box ({@link #attach}, from {@code init}, because every rebuild makes a new one), the keys and
 * clicks ({@link #handleKey} / {@link #mouseClicked}, before its own handling) and a chance to draw
 * ({@link #render}, after {@code super.render}).
 *
 * <p>Candidates starting with {@code #} are tags (1.12.0): typing {@code #} offers only those, and
 * {@code #rai} finds {@code #minecraft:raiders} the way {@code cow} finds {@code minecraft:cow}. A box
 * that holds several entries (the region editor's biomes) completes only the last one.
 *
 * <p>The list belongs to its box while the box has focus: it closes when focus moves on or a click
 * lands anywhere else (round 13 found it open for good once something had been typed, lying over
 * the line that explained what was wrong with the entry). Each box has a list of its own - one list
 * per page was attached to the last item box on it, so the separator's opened under the
 * assimilator's.
 */
final class IdCompletion {
    private static final int LINE_HEIGHT = 12;
    private static final int MAX_SHOWN = 8;
    private static final int TEXT = 0xAAAAAA;
    private static final int PICKED = 0xFFFF55;
    private static final int BACKGROUND = 0xF0100010;
    /**
     * How far in front of everything else the list is drawn. Widgets render at z=0 and vanilla puts
     * tooltips at 400, so this sits above the screen and below them - without it the list went up
     * behind the screen's own text, which is worse than no list at all.
     */
    private static final int Z = 200;
    private static final String TAG = "#";

    /** Every entity id, every namespace that has an entity, and every item id. Worked out once. */
    private static List<String> entityIds;
    private static List<String> modIds;
    private static List<String> itemIds;

    private final Supplier<List<String>> candidates;
    /** Whether the box holds a list (commas or spaces between entries) and only the last is completed. */
    private final boolean lastEntryOnly;
    /** Opens over the box whenever it fits there, to keep the line under the box in sight. */
    private boolean preferAbove;
    private EditBox box;
    private Collection<String> exclude = List.of();
    private List<String> shown = List.of();
    private int index;

    /** @param candidates every id the box could mean, asked afresh each time it is needed */
    IdCompletion(Supplier<List<String>> candidates) {
        this(candidates, false);
    }

    /** @param lastEntryOnly the box holds a list of entries and only the one being typed is completed */
    IdCompletion(Supplier<List<String>> candidates, boolean lastEntryOnly) {
        this.candidates = candidates;
        this.lastEntryOnly = lastEntryOnly;
    }

    /** Opens the list over the box when there is room for it there, under it only when there is not. */
    IdCompletion above() {
        this.preferAbove = true;
        return this;
    }

    /** Points this at the box it completes, or at nothing (null) on a page without one. Closes the list. */
    void attach(EditBox box) {
        this.box = box;
        close();
    }

    /** Whether {@code widget} is the box this completes - for a screen asking "is it focused". */
    boolean isFor(GuiEventListener widget) {
        return box != null && widget == box;
    }

    /** Ids that are already there and so are not worth offering, such as a list's own entries. */
    void exclude(Collection<String> ids) {
        this.exclude = ids == null ? List.of() : ids;
    }

    /** Whether the list is showing. It never is while its box does not have focus, and closes then. */
    boolean isOpen() {
        if (box != null && !shown.isEmpty() && !box.isFocused()) {
            close();
        }
        return box != null && !shown.isEmpty();
    }

    void close() {
        shown = List.of();
        index = 0;
    }

    /** Fills the list from what has been typed. Called from the box's responder. */
    void update(String typed) {
        index = 0;
        String text = typed == null ? "" : entryOf(typed).toLowerCase(Locale.ROOT);
        if (box == null || text.isEmpty()) {
            shown = List.of();
            return;
        }
        // A "#" means a tag, and is not part of what is matched: "#rai" is "rai" among the tags.
        boolean tagTyped = text.startsWith(TAG);
        String bare = tagTyped ? text.substring(TAG.length()) : text;
        // Three buckets, best first: the id itself, then the part after the colon (people type
        // "cow"), then anything that merely contains what was typed.
        List<String> byId = new ArrayList<>();
        List<String> byPath = new ArrayList<>();
        List<String> anywhere = new ArrayList<>();
        for (String candidate : candidates.get()) {
            if (candidate.equals(text) || exclude.contains(candidate)) {
                continue;
            }
            boolean tag = candidate.startsWith(TAG);
            String id = tag ? candidate.substring(TAG.length()) : candidate;
            if (tag != tagTyped) {
                // An id typed: a tag only where its name holds the text ("raid" - #raiders); a "#"
                // typed: never an id.
                if (tag && id.contains(bare)) {
                    anywhere.add(candidate);
                }
                continue;
            }
            if (id.startsWith(bare)) {
                byId.add(candidate);
            } else if (pathOf(id).startsWith(bare)) {
                byPath.add(candidate);
            } else if (id.contains(bare)) {
                anywhere.add(candidate);
            }
        }
        List<String> out = new ArrayList<>(byId);
        out.addAll(byPath);
        out.addAll(anywhere);
        shown = List.copyOf(out.subList(0, Math.min(out.size(), MAX_SHOWN)));
    }

    /** Puts the highlighted id in the box. */
    void accept() {
        if (!isOpen()) {
            return;
        }
        String chosen = shown.get(Math.min(index, shown.size() - 1));
        if (lastEntryOnly) {
            String value = box.getValue();
            box.setValue(value.substring(0, entryStart(value)) + chosen);
        } else {
            box.setValue(chosen);
        }
        // setValue runs the responder, which fills the list again from the completed id; it has
        // served its purpose either way, so it closes here rather than a moment later.
        close();
    }

    /**
     * Whether Enter should take the highlighted line before the screen does whatever Enter does
     * there. With the list open, the highlighted line is what the screen is offering - round 4 of
     * the 1.9.0 tests pressed Enter where Tab was meant and reasonably called the missing completion
     * a bug. The one exception is text that is already a whole id on its own: "minecraft:pig" must not
     * become the "minecraft:piglin" listed under it just because the list is open.
     */
    boolean shouldEnterComplete() {
        if (!isOpen()) {
            return false;
        }
        String typed = entryOf(box.getValue()).toLowerCase(Locale.ROOT);
        List<String> known = candidates.get();
        String full = typed.startsWith(TAG) ? TAG + "minecraft:" + typed.substring(TAG.length()) : "minecraft:" + typed;
        return !known.contains(typed) && !known.contains(full);
    }

    /** What is being typed: the whole box, or in a list box the entry after the last comma or space. */
    private String entryOf(String value) {
        return (lastEntryOnly ? value.substring(entryStart(value)) : value).trim();
    }

    /** Where the last entry of a list starts: after its last comma or space. */
    private static int entryStart(String value) {
        for (int i = value.length() - 1; i >= 0; i--) {
            char c = value.charAt(i);
            if (c == ',' || Character.isWhitespace(c)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Tab, the arrows and Esc, while the list is open and the box has focus.
     *
     * @return true when the key was the list's and the screen should do nothing more with it
     */
    boolean handleKey(int keyCode, boolean boxFocused) {
        if (!boxFocused || !isOpen()) {
            return false;
        }
        switch (keyCode) {
            case InputConstants.KEY_TAB -> {
                accept();
                return true;
            }
            case InputConstants.KEY_DOWN -> {
                index = Math.floorMod(index + 1, shown.size());
                return true;
            }
            case InputConstants.KEY_UP -> {
                index = Math.floorMod(index - 1, shown.size());
                return true;
            }
            case InputConstants.KEY_ESCAPE -> {
                // Closes the list, not the screen: losing a half-typed entry to Escape would be its
                // own small betrayal.
                close();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * A click on a line takes it; a click anywhere but the list and its box closes the list.
     *
     * @return true when the click landed on the list and the screen should do nothing more with it
     */
    boolean mouseClicked(double mouseX, double mouseY, int screenHeight) {
        if (!isOpen()) {
            return false;
        }
        int[] area = area(screenHeight);
        if (mouseX >= area[0] && mouseX <= area[0] + area[2] && mouseY >= area[1] && mouseY < area[3]) {
            int line = (int) ((mouseY - area[1]) / LINE_HEIGHT);
            if (line >= 0 && line < shown.size()) {
                index = line;
                accept();
                return true;
            }
        }
        if (!box.isMouseOver(mouseX, mouseY)) {
            close();
        }
        return false;
    }

    void render(GuiGraphics graphics, int screenHeight, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int[] area = area(screenHeight);
        // Drawing last is not enough on its own - the screen's own text is batched and comes out in
        // front of a plain fill. Lifting the whole list forward is how vanilla's own command
        // suggestions do it.
        Gfx.inFront(graphics, Z, () -> drawList(graphics, font, area, mouseX, mouseY));
    }

    private void drawList(GuiGraphics graphics, Font font, int[] area, int mouseX, int mouseY) {
        graphics.fill(area[0] - 1, area[1] - 1, area[0] + area[2] + 1, area[3], BACKGROUND);
        int hovered = -1;
        if (mouseX >= area[0] && mouseX <= area[0] + area[2] && mouseY >= area[1] && mouseY < area[3]) {
            hovered = (mouseY - area[1]) / LINE_HEIGHT;
        }
        for (int i = 0; i < shown.size(); i++) {
            boolean picked = i == index || i == hovered;
            Gfx.text(graphics, font, shown.get(i), area[0] + 2, area[1] + i * LINE_HEIGHT + 2, picked ? PICKED : TEXT);
        }
    }

    /**
     * Where the list goes: x, top, width, bottom. Under the box when a full list would fit under it,
     * over it otherwise - the mob list editor's box sits at the bottom of the screen, the config
     * screen's near the top - or the other way round for a list made {@link #above()}. Judged on a
     * full list rather than the lines showing right now, so it does not jump from one side to the
     * other as the list shrinks under the typing. As wide as the box, or as the longest id when that
     * is wider: item ids run longer than the box they go in.
     */
    private int[] area(int screenHeight) {
        Font font = Minecraft.getInstance().font;
        int width = box.getWidth();
        for (String id : shown) {
            width = Math.max(width, font.width(id) + 4);
        }
        int height = shown.size() * LINE_HEIGHT;
        int below = box.getY() + box.getHeight() + 2;
        int bottom = box.getY() - 2;
        boolean fitsBelow = below + MAX_SHOWN * LINE_HEIGHT <= screenHeight;
        boolean fitsAbove = bottom - MAX_SHOWN * LINE_HEIGHT >= 0;
        if (preferAbove ? !fitsAbove && fitsBelow : fitsBelow) {
            return new int[]{box.getX(), below, width, below + height};
        }
        return new int[]{box.getX(), bottom - height, width, bottom};
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    static List<String> entityIds() {
        if (entityIds == null) {
            entityIds = sorted(BuiltInRegistries.ENTITY_TYPE.keySet());
        }
        return entityIds;
    }

    /** Namespaces that actually have an entity in them — the only ones a mod list can mean. */
    static List<String> modIds() {
        if (modIds == null) {
            List<String> ids = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                if (!ids.contains(id.getNamespace())) {
                    ids.add(id.getNamespace());
                }
            }
            ids.sort(String::compareTo);
            modIds = List.copyOf(ids);
        }
        return modIds;
    }

    static List<String> itemIds() {
        if (itemIds == null) {
            itemIds = sorted(BuiltInRegistries.ITEM.keySet());
        }
        return itemIds;
    }

    /**
     * Every biome and biome tag of the world this game is in (1.12.0, the region editor's filter).
     * Asked afresh each time: biomes are the server's, from its data packs, so they cannot be kept
     * for the game like the entity ids.
     */
    static List<String> biomeIdsAndTags() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        Registry<Biome> biomes = minecraft.level.registryAccess().registry(Registries.BIOME).orElse(null);
        if (biomes == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(sorted(biomes.keySet()));
        List<String> tags = new ArrayList<>();
        Compat.tagKeys(biomes).forEach(tag -> tags.add(TAG + tag.location()));
        tags.sort(String::compareTo);
        ids.addAll(tags);
        return ids;
    }

    private static List<String> sorted(Collection<ResourceLocation> keys) {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : keys) {
            ids.add(id.toString());
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }
}
