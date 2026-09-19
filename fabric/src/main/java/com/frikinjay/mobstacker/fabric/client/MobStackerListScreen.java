package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.MobListKind;
import com.frikinjay.mobstacker.config.MobListMode;
import com.frikinjay.mobstacker.config.MobLists;
import com.frikinjay.mobstacker.config.StackRegion;
import com.frikinjay.mobstacker.fabric.network.MobStackerNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one screen for editing mob lists, opened either on the global lists or on a single region's.
 *
 * <p>There are eight lists in the mod (four kinds × global and per region) plus the per-type stack
 * ceilings, and they are all the same shape: a set of entries somebody adds to and removes from.
 * Writing nine screens, or even two, is how the eight would slowly stop behaving alike — so this is
 * built once and told which scope it is looking at, the same way the command tree is.
 *
 * <p>It works in singleplayer (editing the integrated server's config on its own thread) and against
 * a remote server that has the mod (showing the synced snapshot and sending edits as packets), and
 * it shows without editing for a player who is not an operator.
 */
public final class MobStackerListScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    /** Where the rows start with nothing above them but the tab title. */
    private static final int GLOBAL_LIST_TOP = 72;
    /** Where they start on a region, which carries the inherit button and the line explaining it. */
    private static final int REGION_LIST_TOP = 84;
    private static final int LIST_BOTTOM_MARGIN = 62;
    /** The same, with room kept for the two lines saying the list is not being read. */
    private static final int WARNED_BOTTOM_MARGIN = 86;
    private static final int SUGGESTION_HEIGHT = 12;
    private static final int MAX_SUGGESTIONS = 8;
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int MUTED_TEXT = 0xA0A0A0;
    private static final int WARN_TEXT = 0xFF5555;
    private static final int SUGGESTION_TEXT = 0xAAAAAA;
    private static final int SUGGESTION_PICKED = 0xFFFF55;
    private static final int SUGGESTION_BACKGROUND = 0xF0100010;

    /** The tabs, in the order the {@code >} button walks them. Null kind = the ceilings tab. */
    private static final MobListKind[] TABS = MobListKind.values();
    private static final int CEILINGS_TAB = TABS.length;

    /** Every entity id in the game, and every namespace that has one. Worked out once. */
    private static List<String> entityIds;
    private static List<String> modIds;

    private final Screen parent;
    /** The region being edited, or null for the global lists. */
    private final String regionName;

    private int tab;
    private int scrollOffset;
    private int visibleRows = 1;
    private boolean remote;
    private boolean editable;
    private EditBox entryBox;
    private EditBox sizeBox;
    /** Set after the first click on the button that would drop this region's own list. */
    private boolean inheritArmed;
    private List<String> suggestions = List.of();
    private int suggestionIndex;

    private MobStackerListScreen(Screen parent, String regionName) {
        super(Component.literal(regionName == null
                ? "MobStacker: Restacked — Mob lists"
                : "MobStacker: Restacked — Mob lists: " + regionName));
        this.parent = parent;
        this.regionName = regionName;
    }

    /** The global lists, the ones that apply everywhere a region does not say otherwise. */
    public static MobStackerListScreen global(Screen parent) {
        return new MobStackerListScreen(parent, null);
    }

    /** One region's own lists, which replace the global ones wherever the region sets them. */
    public static MobStackerListScreen forRegion(Screen parent, String regionName) {
        return new MobStackerListScreen(parent, regionName);
    }

    @Override
    protected void init() {
        boolean singleplayer = this.minecraft != null && this.minecraft.hasSingleplayerServer();
        this.remote = !singleplayer && MobStackerClientNetworking.serverHasMod();
        this.editable = singleplayer || (remote && MobStackerClientNetworking.authorized());
        this.suggestions = List.of();

        addRenderableWidget(Button.builder(Component.literal("<"), b -> switchTab(-1))
                .bounds(this.width / 2 - 170, 20, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> switchTab(1))
                .bounds(this.width / 2 + 150, 20, 20, 20).build());

        // A region's list is either its own or the global one; this button moves between the two.
        // While it is inherited the rows below are the global list, and editing them is refused
        // rather than quietly turned into an override - adding one entry to a list of ten you did
        // not write, and ending up with a list of one, is not what anybody meant by "Add".
        if (regionName != null && tab != CEILINGS_TAB) {
            MobListKind kind = TABS[tab];
            boolean own = editableHere();
            int wouldLose = own ? entries(kind).size() : 0;
            Button inherit = Button.builder(inheritLabel(wouldLose), b -> {
                // Going back to the global list throws this region's copy away, and there is no
                // undo: somebody who has just typed ten entries in deserves to be asked once.
                if (own && wouldLose > 0 && !inheritArmed) {
                    inheritArmed = true;
                    b.setMessage(inheritLabel(wouldLose));
                    return;
                }
                inheritArmed = false;
                send(listId(kind, own ? "inherit" : "override"), "");
                rebuildWidgets();
            }).bounds(this.width / 2 - 170, 44, 340, 20).build();
            inherit.active = editable;
            addRenderableWidget(inherit);
        }

        List<String> rows = rowLabels();
        int listTop = listTop();
        this.visibleRows = Math.max(1, (this.height - listBottomMargin() - listTop) / ROW_HEIGHT);
        this.scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visibleRows)));

        int y = listTop;
        int last = Math.min(rows.size(), scrollOffset + visibleRows);
        for (int i = scrollOffset; i < last; i++) {
            String entry = rows.get(i);
            Button remove = Button.builder(Component.literal("Remove"), b -> removeEntry(entry))
                    .bounds(this.width / 2 + 90, y, 80, 20).build();
            remove.active = editable && editableHere();
            addRenderableWidget(remove);
            y += ROW_HEIGHT;
        }

        addAddRow();

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private Component inheritLabel(int wouldLose) {
        if (!editableHere()) {
            return Component.literal("Inherited — give this region its own copy");
        }
        if (inheritArmed) {
            return Component.literal("Click again — this discards " + wouldLose
                    + (wouldLose == 1 ? " entry" : " entries")).withStyle(ChatFormatting.RED);
        }
        return Component.literal("Set here — follow the global list again");
    }

    /** The rows start lower on a region, which has a button and a line of its own above them. */
    private int listTop() {
        return regionName == null ? GLOBAL_LIST_TOP : REGION_LIST_TOP;
    }

    private int listBottomMargin() {
        return tabInUse() ? LIST_BOTTOM_MARGIN : WARNED_BOTTOM_MARGIN;
    }

    /** The "type a new entry here" row: an id box, a size box on the ceilings tab, and a button. */
    private void addAddRow() {
        int y = this.height - 52;
        boolean ceilings = tab == CEILINGS_TAB;
        int boxWidth = ceilings ? 180 : 250;

        EditBox box = new EditBox(this.font, this.width / 2 - 170, y, boxWidth, 20,
                Component.literal("entry"));
        box.setMaxLength(128);
        box.setHint(Component.literal(hintFor()));
        box.setEditable(editable && editableHere());
        box.setResponder(this::updateSuggestions);
        this.entryBox = box;
        addRenderableWidget(box);

        if (ceilings) {
            EditBox size = new EditBox(this.font, this.width / 2 - 170 + boxWidth + 4, y, 60, 20,
                    Component.literal("size"));
            size.setMaxLength(6);
            size.setHint(Component.literal("16"));
            size.setEditable(editable);
            this.sizeBox = size;
            addRenderableWidget(size);
        } else {
            this.sizeBox = null;
        }

        Button add = Button.builder(Component.literal(ceilings ? "Set" : "Add"), b -> addEntry())
                .bounds(this.width / 2 + 90, y, 80, 20).build();
        add.active = editable && editableHere();
        addRenderableWidget(add);
    }

    private String hintFor() {
        if (tab == CEILINGS_TAB) {
            return "minecraft:cow";
        }
        return TABS[tab].flavour() == MobListKind.Flavour.ENTITY ? "minecraft:cow" : "alexsmobs";
    }

    private void switchTab(int delta) {
        tab = Math.floorMod(tab + delta, TABS.length + 1);
        scrollOffset = 0;
        inheritArmed = false;
        suggestions = List.of();
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, rowLabels().size() - visibleRows);
        if (max > 0 && delta != 0.0) {
            int next = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), max));
            if (next != scrollOffset) {
                scrollOffset = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Called on the client thread when a fresh config snapshot arrives from the server. */
    public void onConfigSynced() {
        if (this.getFocused() instanceof EditBox) {
            return; // don't yank a box out from under somebody mid-word
        }
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ suggestions

    /**
     * What the entry box could be completed to, in the same spirit as the command line's
     * suggestions: an entity id has to be spelled exactly right, and nobody remembers whether it is
     * {@code minecraft:zombified_piglin} or {@code zombie_pigman} until the game says so.
     */
    private void updateSuggestions(String typed) {
        suggestionIndex = 0;
        String text = typed.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || !editable || !editableHere()) {
            suggestions = List.of();
            return;
        }
        List<String> already = tab == CEILINGS_TAB ? List.of() : entries(TABS[tab]);
        // Three buckets, best first: the id itself, then the part after the colon (people type
        // "cow"), then anything that merely contains what was typed.
        List<String> byId = new ArrayList<>();
        List<String> byPath = new ArrayList<>();
        List<String> anywhere = new ArrayList<>();
        for (String candidate : candidates()) {
            if (candidate.equals(text) || already.contains(candidate)) {
                continue;
            }
            if (candidate.startsWith(text)) {
                byId.add(candidate);
            } else if (pathOf(candidate).startsWith(text)) {
                byPath.add(candidate);
            } else if (candidate.contains(text)) {
                anywhere.add(candidate);
            }
        }
        List<String> out = new ArrayList<>(byId);
        out.addAll(byPath);
        out.addAll(anywhere);
        suggestions = List.copyOf(out.subList(0, Math.min(out.size(), MAX_SUGGESTIONS)));
    }

    private List<String> candidates() {
        boolean mods = tab != CEILINGS_TAB && TABS[tab].flavour() == MobListKind.Flavour.MOD;
        return mods ? modIds() : entityIds();
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private static List<String> entityIds() {
        if (entityIds == null) {
            List<String> ids = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                ids.add(id.toString());
            }
            ids.sort(String::compareTo);
            entityIds = List.copyOf(ids);
        }
        return entityIds;
    }

    /** Namespaces that actually have an entity in them — the only ones a mod list can mean. */
    private static List<String> modIds() {
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

    /** Where the suggestion list is drawn: x, top, width, bottom, sitting on top of the entry box. */
    private int[] suggestionArea() {
        int x = this.width / 2 - 170;
        int width = tab == CEILINGS_TAB ? 180 : 250;
        int bottom = this.height - 54;
        return new int[]{x, bottom - suggestions.size() * SUGGESTION_HEIGHT, width, bottom};
    }

    private void acceptSuggestion() {
        if (suggestions.isEmpty() || entryBox == null) {
            return;
        }
        String chosen = suggestions.get(Math.min(suggestionIndex, suggestions.size() - 1));
        entryBox.setValue(chosen);
        // setValue runs the responder, which fills the list again from the completed id; it has
        // served its purpose either way, so it closes here rather than a moment later.
        suggestions = List.of();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean inEntry = this.getFocused() == entryBox && entryBox != null;
        if (inEntry && !suggestions.isEmpty()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_TAB -> {
                    acceptSuggestion();
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    suggestionIndex = Math.floorMod(suggestionIndex + 1, suggestions.size());
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    suggestionIndex = Math.floorMod(suggestionIndex - 1, suggestions.size());
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    // Closes the list, not the screen: losing a half-typed entry to Escape would be
                    // its own small betrayal.
                    suggestions = List.of();
                    return true;
                }
                default -> {
                }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (inEntry && tab == CEILINGS_TAB && sizeBox != null && sizeBox.getValue().trim().isEmpty()) {
                setFocused(sizeBox); // a ceiling needs a number too, so go and ask for it
                return true;
            }
            if (inEntry || (sizeBox != null && this.getFocused() == sizeBox)) {
                addEntry();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!suggestions.isEmpty()) {
            int[] area = suggestionArea();
            if (mouseX >= area[0] && mouseX <= area[0] + area[2] && mouseY >= area[1] && mouseY < area[3]) {
                int index = (int) ((mouseY - area[1]) / SUGGESTION_HEIGHT);
                if (index >= 0 && index < suggestions.size()) {
                    suggestionIndex = index;
                    acceptSuggestion();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ reading

    /** The rows currently on screen: list entries, or "id -> size" lines on the ceilings tab. */
    private List<String> rowLabels() {
        if (tab == CEILINGS_TAB) {
            List<String> out = new ArrayList<>();
            ceilings().forEach((id, size) -> out.add(id + " -> " + size));
            return out;
        }
        return entries(TABS[tab]);
    }

    private List<String> entries(MobListKind kind) {
        if (remote) {
            if (regionName == null) {
                return MobStackerClientNetworking.globalList(kind);
            }
            MobStackerClientNetworking.RegionInfo info = regionInfo();
            return info == null ? List.of() : info.effectiveList(kind);
        }
        if (regionName == null) {
            return MobStacker.config.getList(kind);
        }
        StackRegion region = MobStacker.config.getRegion(regionName);
        if (region == null) {
            return List.of();
        }
        return MobLists.effective(kind, region);
    }

    private Map<String, Integer> ceilings() {
        if (remote) {
            if (regionName == null) {
                return MobStackerClientNetworking.globalCeilings();
            }
            MobStackerClientNetworking.RegionInfo info = regionInfo();
            return info == null ? Map.of() : info.ceilings();
        }
        if (regionName == null) {
            return MobStacker.config.getMaxStackSizes();
        }
        StackRegion region = MobStacker.config.getRegion(regionName);
        return region == null ? Map.of() : region.getMaxStackSizes();
    }

    private boolean regionOverrides(MobListKind kind) {
        if (regionName == null) {
            return true;
        }
        if (remote) {
            MobStackerClientNetworking.RegionInfo info = regionInfo();
            return info != null && info.hasList(kind);
        }
        StackRegion region = MobStacker.config.getRegion(regionName);
        return region != null && region.hasList(kind);
    }

    private MobStackerClientNetworking.RegionInfo regionInfo() {
        for (MobStackerClientNetworking.RegionInfo info : MobStackerClientNetworking.regions()) {
            if (info.name().equals(regionName)) {
                return info;
            }
        }
        return null;
    }

    /** Whether the list on screen is the one {@code mobListMode} is actually reading. */
    private boolean tabInUse() {
        if (tab == CEILINGS_TAB) {
            return true;
        }
        return (modeHere() == MobListMode.WHITELIST) == (TABS[tab].half() == MobListKind.Half.ALLOW);
    }

    private MobListMode modeHere() {
        String raw = regionModeSetting();
        if (raw == null && remote) {
            raw = MobStackerClientNetworking.value("mobListMode");
        }
        if (raw != null) {
            for (MobListMode mode : MobListMode.values()) {
                if (mode.name().equalsIgnoreCase(raw.trim())) {
                    return mode;
                }
            }
        }
        // On a server this client's own config is somebody else's file entirely, so fall back to
        // the default rather than to a local value that is not in force anywhere.
        return remote ? MobListMode.BLACKLIST : MobStacker.config.getMobListMode();
    }

    /** The mode this region sets for itself, or null when it follows the global config. */
    private String regionModeSetting() {
        if (regionName == null) {
            return null;
        }
        if (remote) {
            MobStackerClientNetworking.RegionInfo info = regionInfo();
            return info == null ? null : info.settings().get("mobListMode");
        }
        StackRegion region = MobStacker.config.getRegion(regionName);
        return region == null ? null : region.getSetting("mobListMode");
    }

    // ------------------------------------------------------------------ writing

    /**
     * Whether the rows on screen belong to the scope being edited. False only for a region list it
     * is inheriting: those rows are the global list, and this screen is not where it gets changed.
     */
    private boolean editableHere() {
        return regionName == null || tab == CEILINGS_TAB || regionOverrides(TABS[tab]);
    }

    private void addEntry() {
        if (!editable || !editableHere() || entryBox == null) {
            return;
        }
        String entry = entryBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (entry.isEmpty()) {
            return;
        }
        if (tab == CEILINGS_TAB) {
            String size = sizeBox == null ? "" : sizeBox.getValue().trim();
            try {
                if (Integer.parseInt(size) < 1) {
                    return;
                }
            } catch (NumberFormatException e) {
                return; // an unreadable size is not an edit worth sending
            }
            send(ceilingId(entry), size);
        } else {
            send(listId(TABS[tab], "add"), entry);
        }
        entryBox.setValue("");
        if (sizeBox != null) {
            sizeBox.setValue("");
        }
        suggestions = List.of();
        rebuildWidgets();
    }

    private void removeEntry(String label) {
        if (!editable || !editableHere()) {
            return;
        }
        if (tab == CEILINGS_TAB) {
            // The row reads "id -> size"; the id is what identifies it, and an empty value unsets it.
            String id = label.contains(" -> ") ? label.substring(0, label.indexOf(" -> ")) : label;
            send(ceilingId(id), "");
        } else {
            send(listId(TABS[tab], "remove"), label);
        }
        rebuildWidgets();
    }

    private String listId(MobListKind kind, String op) {
        return MobStackerNetworking.LIST_PREFIX + kind.id() + ":" + op;
    }

    private String ceilingId(String entityId) {
        return MobStackerNetworking.MAXSTACK_PREFIX + entityId;
    }

    /**
     * Sends one edit wherever this screen's edits go: as a packet on a server, or straight into the
     * integrated server's config on its own thread.
     */
    private void send(String id, String value) {
        String full = regionName == null ? id : MobStackerNetworking.REGION_PREFIX + regionName + ":" + id;
        if (remote) {
            MobStackerClientNetworking.sendEdit(full, value);
            return;
        }
        if (this.minecraft == null) {
            return;
        }
        Minecraft client = this.minecraft;
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        // The config lives on the server thread, and the screen repaints from what it really stored
        // rather than from what was typed - an edit the config refuses must not linger on screen.
        server.execute(() -> {
            applyLocally(id, value);
            client.execute(this::rebuildWidgets);
        });
    }

    /** The singleplayer half of {@link #send}: the same edits, without a packet in between. */
    private void applyLocally(String id, String value) {
        MobLists.Holder holder = regionName == null
                ? MobStacker.config
                : MobStacker.config.getRegion(regionName);
        if (holder == null) {
            return;
        }
        if (id.startsWith(MobStackerNetworking.MAXSTACK_PREFIX)) {
            String entityId = id.substring(MobStackerNetworking.MAXSTACK_PREFIX.length());
            Integer size = null;
            if (!value.isEmpty()) {
                try {
                    size = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return;
                }
            }
            if (regionName == null) {
                MobStacker.config.setMaxStackSize(entityId, size);
            } else {
                ((StackRegion) holder).setMaxStackSize(entityId, size);
                MobStacker.config.save();
            }
            return;
        }
        String spec = id.substring(MobStackerNetworking.LIST_PREFIX.length());
        int split = spec.lastIndexOf(':');
        MobListKind kind = MobListKind.byId(spec.substring(0, split));
        if (kind == null) {
            return;
        }
        switch (spec.substring(split + 1)) {
            case "add" -> holder.addToList(kind, value);
            case "remove" -> holder.removeFromList(kind, value);
            case "inherit" -> holder.clearList(kind);
            // Seeded with what was being inherited, so taking a list over does not empty it.
            case "override" -> holder.setList(kind, MobStacker.config.getList(kind));
            default -> {
                return;
            }
        }
        MobStacker.config.save();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(tabTitle()), this.width / 2, 26, 0xFFFFFF);

        // Under the button it explains, never across it.
        if (regionName != null && tab != CEILINGS_TAB) {
            graphics.drawCenteredString(this.font,
                    Component.literal(editableHere()
                                    ? "This region has its own copy of this list"
                                    : "Showing the global list — give this region its own copy to change it")
                            .withStyle(ChatFormatting.GRAY),
                    this.width / 2, 68, MUTED_TEXT);
        }

        List<String> rows = rowLabels();
        int listTop = listTop();
        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(emptyText()),
                    this.width / 2, listTop + 6, MUTED_TEXT);
        } else {
            int y = listTop + 6;
            int last = Math.min(rows.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < last; i++) {
                graphics.drawString(this.font, rows.get(i), this.width / 2 - 170, y, NORMAL_TEXT);
                y += ROW_HEIGHT;
            }
        }

        // Say plainly when the list on screen is not the one being read, so nobody carefully fills
        // an allow list while the world is still running a blacklist and wonders why nothing changed.
        if (!tabInUse()) {
            graphics.drawCenteredString(this.font,
                    Component.literal(modeWarning()).withStyle(ChatFormatting.RED),
                    this.width / 2, this.height - 80, WARN_TEXT);
            graphics.drawCenteredString(this.font,
                    Component.literal(modeAdvice()).withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height - 68, MUTED_TEXT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        renderSuggestions(graphics, mouseX, mouseY);
    }

    private void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        if (suggestions.isEmpty()) {
            return;
        }
        int[] area = suggestionArea();
        graphics.fill(area[0] - 1, area[1] - 1, area[0] + area[2] + 1, area[3], SUGGESTION_BACKGROUND);
        int hovered = -1;
        if (mouseX >= area[0] && mouseX <= area[0] + area[2] && mouseY >= area[1] && mouseY < area[3]) {
            hovered = (int) ((mouseY - area[1]) / SUGGESTION_HEIGHT);
        }
        for (int i = 0; i < suggestions.size(); i++) {
            boolean picked = i == suggestionIndex || i == hovered;
            graphics.drawString(this.font, suggestions.get(i), area[0] + 2,
                    area[1] + i * SUGGESTION_HEIGHT + 2, picked ? SUGGESTION_PICKED : SUGGESTION_TEXT);
        }
    }

    private String modeWarning() {
        String where = regionName == null
                ? ""
                : (regionModeSetting() != null ? " in this region" : " (from the global config)");
        return "mobListMode is " + modeHere() + where + " — this list is not being read";
    }

    /** The exact command that would make this list count, rather than a hint to go and find it. */
    private String modeAdvice() {
        String want = TABS[tab].half() == MobListKind.Half.ALLOW ? "whitelist" : "blacklist";
        return regionName == null
                ? "Set it with /mobstacker set mobListMode " + want
                : "Set it with /mobstacker region set " + regionName + " mobListMode " + want;
    }

    private String tabTitle() {
        if (tab == CEILINGS_TAB) {
            return "Per-type stack ceilings";
        }
        MobListKind kind = TABS[tab];
        String scope = regionName == null ? "" : (regionOverrides(kind) ? " (set here)" : " (inherited)");
        return kind.id() + scope;
    }

    private String emptyText() {
        if (tab == CEILINGS_TAB) {
            return "Nothing here — every mob follows maxStackSize";
        }
        if (!tabInUse()) {
            return "Empty";
        }
        MobListKind kind = TABS[tab];
        return kind.half() == MobListKind.Half.ALLOW
                ? "Empty — in WHITELIST mode that means nothing stacks"
                : "Empty — nothing is excluded";
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
