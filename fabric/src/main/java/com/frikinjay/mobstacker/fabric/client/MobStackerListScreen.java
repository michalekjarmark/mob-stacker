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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

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
    private static final int LIST_TOP = 72;
    private static final int LIST_BOTTOM_MARGIN = 62;
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int MUTED_TEXT = 0xA0A0A0;
    private static final int WARN_TEXT = 0xFF5555;

    /** The tabs, in the order the {@code >} button walks them. Null kind = the ceilings tab. */
    private static final MobListKind[] TABS = MobListKind.values();
    private static final int CEILINGS_TAB = TABS.length;

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
            Button inherit = Button.builder(
                            Component.literal(editableHere()
                                    ? "This region's own — use the global list"
                                    : "Inherited — give this region its own copy"),
                            b -> {
                                send(listId(kind, editableHere() ? "inherit" : "override"), "");
                                rebuildWidgets();
                            })
                    .bounds(this.width / 2 - 170, 44, 260, 20).build();
            inherit.active = editable;
            addRenderableWidget(inherit);
        }

        List<String> rows = rowLabels();
        this.visibleRows = Math.max(1, (this.height - LIST_BOTTOM_MARGIN - LIST_TOP) / ROW_HEIGHT);
        this.scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visibleRows)));

        int y = LIST_TOP;
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
        String raw = null;
        if (remote) {
            MobStackerClientNetworking.RegionInfo info = regionName == null ? null : regionInfo();
            if (info != null) {
                raw = info.settings().get("mobListMode");
            }
            if (raw == null) {
                raw = MobStackerClientNetworking.value("mobListMode");
            }
        } else if (regionName != null) {
            StackRegion region = MobStacker.config.getRegion(regionName);
            raw = region == null ? null : region.getSetting("mobListMode");
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

        // Say plainly when the list on screen is not the one being read, so nobody carefully fills
        // an allow list while the world is still running a blacklist and wonders why nothing changed.
        if (!tabInUse()) {
            graphics.drawCenteredString(this.font,
                    Component.literal("mobListMode is " + modeHere() + " — this list is not being read")
                            .withStyle(ChatFormatting.RED),
                    this.width / 2, this.height - 66, WARN_TEXT);
        }

        if (!editableHere()) {
            graphics.drawCenteredString(this.font,
                    Component.literal("Showing the global list — give this region its own copy to change it")
                            .withStyle(ChatFormatting.GRAY),
                    this.width / 2, 62, MUTED_TEXT);
        }

        List<String> rows = rowLabels();
        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(emptyText()),
                    this.width / 2, LIST_TOP + 6, MUTED_TEXT);
        } else {
            int y = LIST_TOP + 6;
            int last = Math.min(rows.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < last; i++) {
                graphics.drawString(this.font, rows.get(i), this.width / 2 - 170, y, NORMAL_TEXT);
                y += ROW_HEIGHT;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
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
