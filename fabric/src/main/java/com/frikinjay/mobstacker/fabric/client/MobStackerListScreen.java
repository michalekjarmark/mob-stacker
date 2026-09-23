package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.MobListKind;
import com.frikinjay.mobstacker.config.MobListMode;
import com.frikinjay.mobstacker.config.MobLists;
import com.frikinjay.mobstacker.config.MobStackerSettings;
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
    /** Room under the rows for the add row and Done, before any notes are stacked on top of it. */
    private static final int LIST_BOTTOM_MARGIN = 62;
    private static final int NOTE_HEIGHT = 12;
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
    // Set by addEntry and honoured on the next tick, not straight away: when "Add" is clicked, the
    // screen hands focus to the button that was clicked *after* its action has run, so focusing the
    // entry box from inside that action is undone before anybody sees it.
    private boolean focusEntrySoon;
    private EditBox sizeBox;
    /** Set after the first click on the button that would drop this region's own list. */
    private boolean inheritArmed;
    private final IdCompletion completion = new IdCompletion(this::candidates);
    /**
     * The rows this screen is showing, read once per rebuild.
     *
     * <p>Not read straight out of the config each time it is needed. In singleplayer the config
     * belongs to the server thread while this screen runs on the render thread, and an edit is
     * applied over there while the rebuild is already walking the list over here: sizing the loop
     * from a list that then got shorter is how clicking Remove a few times in a row crashed the
     * game with an IndexOutOfBoundsException. A copy cannot be pulled out from under the loop, and
     * it also keeps {@link #init} and {@link #render} showing the same thing within one frame.
     */
    private List<String> rows = List.of();
    /** The last thing this screen has to say about an edit - a refusal, or a remark worth making. */
    private String message = "";
    private boolean messageIsError;

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

        this.rows = rowLabels();
        List<String> rows = this.rows;
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

    /** Whatever the rows have to leave room for: the add row, Done, and each note under them. */
    private int listBottomMargin() {
        return LIST_BOTTOM_MARGIN + notes().size() * NOTE_HEIGHT;
    }

    /**
     * The lines drawn between the last row and the add box, bottom of the screen upwards.
     *
     * <p>Collected in one place, and the row area is sized from how many there are, because a line
     * of text and the widgets it sits between are laid out in two different methods here - which is
     * exactly how the last round ended up with a button drawn over its own caption.
     */
    private List<Component> notes() {
        List<Component> out = new ArrayList<>();
        if (!tabInUse()) {
            out.add(Component.literal(modeWarning()).withStyle(ChatFormatting.RED));
            out.add(Component.literal(modeAdvice()).withStyle(ChatFormatting.GRAY));
        }
        if (!message.isEmpty()) {
            out.add(Component.literal(message)
                    .withStyle(messageIsError ? ChatFormatting.RED : ChatFormatting.YELLOW));
        }
        return out;
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
        completion.attach(box);
        this.entryBox = box;
        addRenderableWidget(box);

        if (ceilings) {
            EditBox size = new EditBox(this.font, this.width / 2 - 170 + boxWidth + 4, y, 60, 20,
                    Component.literal("size"));
            // Ten characters: the largest ceiling there is (2147483647), and room for "default".
            size.setMaxLength(10);
            // What a type with no ceiling of its own stacks to here - and what Set takes when the box
            // is left empty (round 6: the hint looked like a value, so Set refused to add anything).
            size.setHint(Component.literal(maxStackSizeHere()));
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

    /**
     * The {@code maxStackSize} in force where this screen edits: the region's own when it sets one,
     * the global one otherwise. From the synced snapshot on a server, from the config in singleplayer.
     */
    private String maxStackSizeHere() {
        String value = null;
        if (remote) {
            if (regionName != null) {
                value = MobStackerClientNetworking.regionValue(regionName, "maxStackSize");
            }
            if (value == null) {
                value = MobStackerClientNetworking.value("maxStackSize");
            }
        } else {
            StackRegion region = regionName == null ? null : MobStacker.config.getRegion(regionName);
            if (region != null) {
                value = region.getSetting("maxStackSize");
            }
            if (value == null) {
                ConfigOption option = MobStackerSettings.byId("maxStackSize");
                value = option == null ? null : option.storedValue();
            }
        }
        return value == null || value.isBlank() ? "16" : value.trim();
    }

    /**
     * Enter on an entity id typed without its namespace ("cow") writes the whole id into the box
     * ("minecraft:cow") before it is used, so the box shows what will be stored. Round 6 read the
     * missing completion for such an id - there is nothing to complete, it is already whole - as
     * Enter not working.
     */
    private void expandShortEntityId() {
        if (entryBox == null || !(tab == CEILINGS_TAB || TABS[tab].flavour() == MobListKind.Flavour.ENTITY)) {
            return;
        }
        String typed = entryBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (typed.isEmpty() || typed.contains(":")) {
            return;
        }
        String full = MobLists.normaliseEntityId(typed);
        if (!full.equals(typed)) {
            entryBox.setValue(full);
            entryBox.moveCursorToEnd();
            completion.close();
        }
    }

    private String hintFor() {
        if (tab == CEILINGS_TAB) {
            return "minecraft:cow";
        }
        return TABS[tab].flavour() == MobListKind.Flavour.ENTITY ? "minecraft:cow" : "alexsmobs";
    }

    private void switchTab(int delta) {
        // What was typed belonged to the tab being left - an entity id means nothing on a mod tab.
        if (entryBox != null) {
            entryBox.setValue("");
        }
        if (sizeBox != null) {
            sizeBox.setValue("");
        }
        tab = Math.floorMod(tab + delta, TABS.length + 1);
        scrollOffset = 0;
        inheritArmed = false;
        completion.close();
        clearMessage();
        rebuildWidgets();
    }

    private void say(String text, boolean error) {
        String next = text == null ? "" : text;
        // Gaining or losing a note changes how much room the rows have, and that is worked out in
        // init(). Rebuilding here is what keeps the two from disagreeing - the alternative is a
        // line of text drawn over the last row, which is the bug this screen already had once.
        boolean roomChanged = next.isEmpty() != this.message.isEmpty();
        this.message = next;
        this.messageIsError = error;
        if (roomChanged) {
            rebuildWidgets();
        }
    }

    private void clearMessage() {
        say("", false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, rows.size() - visibleRows);
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

    /**
     * Called on the client thread when a fresh config snapshot arrives from the server. Always
     * repaints: {@link #rebuildWidgets} carries the boxes' text and focus across, so nobody loses a
     * half-typed id to it.
     */
    public void onConfigSynced() {
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ suggestions

    /** The entry box's completion. The list of ids it offers follows the tab. */
    private List<String> candidates() {
        boolean mods = tab != CEILINGS_TAB && TABS[tab].flavour() == MobListKind.Flavour.MOD;
        return mods ? IdCompletion.modIds() : IdCompletion.entityIds();
    }

    private void updateSuggestions(String typed) {
        if (!editable || !editableHere()) {
            completion.close();
            return;
        }
        // Ids already on the list are not worth offering; a ceiling can be set again, so all are.
        completion.exclude(tab == CEILINGS_TAB ? List.of() : rows);
        completion.update(typed);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean inEntry = entryBox != null && this.getFocused() == entryBox;
        if (completion.handleKey(keyCode, inEntry)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (inEntry && completion.shouldEnterComplete()) {
                completion.accept();
            } else if (inEntry) {
                expandShortEntityId();
            }
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
        if (completion.mouseClicked(mouseX, mouseY, this.height)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ reading

    /**
     * The rows currently on screen: list entries, or "id -> size" lines on the ceilings tab.
     *
     * <p>Always a copy. {@code getList} hands back an unmodifiable <em>view</em> of the live list,
     * which is not the same as an unmodifiable list: the server thread can still shorten it under a
     * reader. See {@link #rows}.
     */
    private List<String> rowLabels() {
        if (tab == CEILINGS_TAB) {
            List<String> out = new ArrayList<>();
            ceilings().forEach((id, size) -> out.add(id + " -> " + size));
            return List.copyOf(out);
        }
        return List.copyOf(entries(TABS[tab]));
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
        String typed = entryBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (typed.isEmpty()) {
            return;
        }
        if (tab == CEILINGS_TAB) {
            String entry = MobLists.normaliseEntityId(typed);
            String size = sizeBox == null ? "" : sizeBox.getValue().trim();
            if (size.isEmpty()) {
                // Nothing typed: the number the box shows as its hint, which is what the type
                // stacks to now. Set with an untouched box used to refuse, as if there were no number.
                size = maxStackSizeHere();
            }
            if (ConfigOption.isDefaultKeyword(size)) {
                // The same word `maxstack <entity> default` takes: no ceiling of its own, so the mob
                // goes back to maxStackSize. Sent as the empty value the Remove button sends.
                clearMessage();
                send(ceilingId(entry), "");
            } else {
                Integer parsed = ConfigOption.parseSize(size);
                if (parsed == null || parsed < 1) {
                    say("A stack ceiling must be a whole number, at least 1 — or '"
                            + ConfigOption.MAX_KEYWORD + "', or '" + ConfigOption.DEFAULT_KEYWORD
                            + "' to remove it.", true);
                    return;
                }
                // Refused before it is sent as well as on arrival: the server would say no anyway,
                // and this screen has no status line from it in singleplayer to say it with.
                String problem = MobLists.entityProblem(entry);
                if (problem != null) {
                    say(problem, true);
                    return;
                }
                say(MobLists.entryNote(MobListKind.DENY_ENTITIES, entry), false);
                send(ceilingId(entry), size);
            }
        } else {
            MobListKind kind = TABS[tab];
            String entry = MobLists.normalise(kind, typed);
            String problem = MobLists.entryProblem(kind, entry);
            if (problem != null) {
                say(problem, true);
                return;
            }
            say(MobLists.entryNote(kind, entry), false);
            send(listId(kind, "add"), entry);
        }
        entryBox.setValue("");
        if (sizeBox != null) {
            sizeBox.setValue("");
        }
        completion.close();
        rebuildWidgets();
        // Somebody adding ids is nearly always about to add another one.
        focusEntrySoon = true;
    }

    /**
     * Keeps what is being typed - and where the cursor is - across a rebuild. A rebuild hands out
     * fresh, empty boxes with nothing focused, and one arrives a moment after every edit: once the
     * integrated server has stored it, or once the server's new snapshot comes back. Without this
     * the cursor left the box every time an entry was added, and a snapshot arriving mid-word would
     * have thrown the word away - which is why a synced snapshot used to be ignored while a box had
     * focus, leaving a freshly added entry missing from the rows until something else redrew them.
     */
    @Override
    protected void rebuildWidgets() {
        boolean typing = entryBox != null && this.getFocused() == entryBox;
        boolean sizing = sizeBox != null && this.getFocused() == sizeBox;
        String typed = entryBox == null ? "" : entryBox.getValue();
        String size = sizeBox == null ? "" : sizeBox.getValue();
        super.rebuildWidgets();
        if (entryBox != null) {
            if (!typed.isEmpty()) {
                entryBox.setValue(typed);
            }
            if (typing) {
                setFocused(entryBox);
            }
        }
        if (sizeBox != null) {
            if (!size.isEmpty()) {
                sizeBox.setValue(size);
            }
            if (sizing) {
                setFocused(sizeBox);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (focusEntrySoon && entryBox != null) {
            focusEntrySoon = false;
            setFocused(entryBox);
        }
        // The cursor only blinks in a box that is ticked.
        if (entryBox != null) {
            entryBox.tick();
        }
        if (sizeBox != null) {
            sizeBox.tick();
        }
    }

    private void removeEntry(String label) {
        if (!editable || !editableHere()) {
            return;
        }
        clearMessage();
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
                size = ConfigOption.parseSize(value);
                if (size == null) {
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

        List<String> shown = this.rows;
        int listTop = listTop();
        if (shown.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(emptyText()),
                    this.width / 2, listTop + 6, MUTED_TEXT);
        } else {
            int y = listTop + 6;
            int last = Math.min(shown.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < last; i++) {
                String label = shown.get(i);
                boolean loaded = rowIsLoaded(label);
                graphics.drawString(this.font, label, this.width / 2 - 170, y,
                        loaded ? NORMAL_TEXT : MUTED_TEXT);
                if (!loaded) {
                    // Kept, not deleted - a list written while a mod was installed has to survive
                    // the mod being away - but never silently: this is also what a typo looks like.
                    graphics.drawString(this.font,
                            Component.literal("(not loaded)").withStyle(ChatFormatting.DARK_GRAY),
                            this.width / 2 - 166 + this.font.width(label), y, MUTED_TEXT);
                }
                y += ROW_HEIGHT;
            }
        }

        // Everything with something to say sits here, stacked up from the add row: that the list on
        // screen is not the one being read, which command would change that, and whatever the last
        // edit answered. listBottomMargin() counts the same lines, so the rows always stop above them.
        List<Component> notes = notes();
        int noteY = this.height - LIST_BOTTOM_MARGIN - notes.size() * NOTE_HEIGHT + 2;
        for (Component note : notes) {
            graphics.drawCenteredString(this.font, note, this.width / 2, noteY, WARN_TEXT);
            noteY += NOTE_HEIGHT;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        completion.render(graphics, this.height, mouseX, mouseY);
    }

    /** Whether the thing this row names exists in the running game. */
    private boolean rowIsLoaded(String label) {
        if (tab == CEILINGS_TAB) {
            int arrow = label.indexOf(" -> ");
            return MobLists.isLoaded(MobListKind.DENY_ENTITIES,
                    arrow < 0 ? label : label.substring(0, arrow));
        }
        return MobLists.isLoaded(TABS[tab], label);
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
