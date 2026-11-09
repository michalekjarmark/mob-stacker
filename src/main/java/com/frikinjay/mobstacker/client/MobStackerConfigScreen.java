package com.frikinjay.mobstacker.client;

import com.frikinjay.mobstacker.compat.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import com.frikinjay.mobstacker.config.SettingSearch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registry-driven config GUI: it iterates the same {@link MobStackerSettings} as the commands and
 * renders one widget per {@link ConfigOption.Type} (on/off + cycle buttons, edit boxes for numbers
 * and item ids), one {@link Category} page at a time.
 * <p>
 * The screen works in three situations:
 * <ul>
 *   <li><b>Singleplayer / LAN host</b> — edits mutate the integrated server's config directly on the
 *       server thread.</li>
 *   <li><b>A remote server that has the mod</b> — the screen shows the server's live config (received
 *       over the config-sync channel) and, for operators, pushes edits back as packets. See
 *       {@link MobStackerClientNetworking} / {@code MobStackerNetworking}.</li>
 *   <li><b>Anywhere else</b> — an informational notice; the {@code /mobstacker} commands remain the
 *       way to configure such a server.</li>
 * </ul>
 * <p>
 * A search box over the pages (1.12.0, asked for after round 13) finds a setting on any page by
 * {@link SettingSearch}: typing anywhere on the screen starts it, the hits are the page while
 * there is text in it, each with its page under its name and the letters that matched in yellow,
 * and they are edited right there. Enter, or a click on a name, shows that setting on its own page
 * among its neighbours; Esc empties the box.
 */
public final class MobStackerConfigScreen extends CompatScreen {
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int ERROR_TEXT = 0xFF5555;
    private static final int MATCHED_TEXT = 0xFFFF55;
    /** How long a setting found by a search glows on its page, in ticks. */
    private static final int HIGHLIGHT_TICKS = 50;

    private final Screen parent;
    private final List<Category> categories = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int categoryIndex;
    // A category can hold more settings than fit on screen (and a small window fits very few), so a
    // page shows a window of rows that the mouse wheel moves through.
    private static final int ROW_HEIGHT = 24;
    // One ladder from the top: the title, the search box, the page switcher, its scroll hint, rows.
    private static final int TITLE_Y = 8;
    private static final int SEARCH_Y = 20;
    private static final int SEARCH_HEIGHT = 16;
    private static final int SEARCH_WIDTH = 200;
    private static final int CATEGORY_Y = 40;
    private static final int LIST_TOP = 68;
    // Space kept below the rows for the status line and the Done button.
    private static final int LIST_BOTTOM_MARGIN = 52;
    private int scrollOffset;
    private int visibleRows = 1;

    // Where our config edits go and whether we may make them, resolved in init().
    private boolean remote;         // connected to a server that speaks our config-sync protocol
    private boolean editable;       // may actually change values (SP host, or remote operator)
    private boolean showRows;       // have data to show (SP, or a remote snapshot has arrived)
    private boolean connectedNoMod; // on a multiplayer server that doesn't have the mod
    private boolean syncRequested;
    // Set while a widget is being repainted, so its own responder does not send that back as an edit.
    private boolean repainting;
    // Item ids for separatorItem and assimilatorItem, the way the mob list editor completes entity
    // ids: a list per box (the Separator page has two).
    private final IdCompletions itemCompletions = new IdCompletions();

    // The search. While the box holds text its hits are the page; the page switcher steps aside.
    private EditBox searchBox;
    private String query = "";
    private List<SettingSearch.Hit> hits = List.of();
    private final Map<String, boolean[]> matched = new HashMap<>();
    /** A setting just found by a search: it glows on its page for a moment, and gets the focus. */
    private String highlightId;
    private int highlightTicks;
    private String focusId;

    public MobStackerConfigScreen(Screen parent) {
        super(Component.literal("MobStacker: Restacked"));
        this.parent = parent;
        for (Category category : Category.values()) {
            if (!MobStackerSettings.byCategory(category).isEmpty()) {
                categories.add(category);
            }
        }
    }

    @Override
    protected void init() {
        rows.clear();
        itemCompletions.clear();
        searchBox = null;

        boolean singleplayer = this.minecraft != null && this.minecraft.hasSingleplayerServer();
        this.remote = !singleplayer && MobStackerClientNetworking.serverHasMod();
        this.editable = singleplayer || (remote && MobStackerClientNetworking.authorized());
        this.showRows = singleplayer || (remote && MobStackerClientNetworking.hasSnapshot());
        boolean connected = this.minecraft != null && this.minecraft.getConnection() != null;
        this.connectedNoMod = connected && !singleplayer && !remote;

        // Ask the server for its config the first time we open against it; the reply rebuilds us.
        if (remote && !syncRequested) {
            syncRequested = true;
            MobStackerClientNetworking.requestSync();
        }

        if (showRows && !categories.isEmpty()) {
            searchBox = new EditBox(this.font, this.width / 2 - SEARCH_WIDTH / 2, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT,
                    Component.literal("search"));
            searchBox.setMaxLength(64);
            searchBox.setHint(Component.literal("type to search settings").withStyle(ChatFormatting.DARK_GRAY));
            searchBox.setValue(query);
            searchBox.setResponder(this::onSearchTyped);
            addRenderableWidget(searchBox);

            // A search shows every page at once, so there is no page to step to while it is on.
            if (!searching()) {
                addRenderableWidget(Button.builder(Component.literal("<"), b -> switchCategory(-1))
                        .bounds(this.width / 2 - 170, CATEGORY_Y, 20, 20).build());
                addRenderableWidget(Button.builder(Component.literal(">"), b -> switchCategory(1))
                        .bounds(this.width / 2 + 150, CATEGORY_Y, 20, 20).build());
            }

            List<ConfigOption> options = shownOptions();
            // Keep clear of the status line (height - 46) and the Done button (height - 28).
            this.visibleRows = Math.max(1, (this.height - LIST_BOTTOM_MARGIN - LIST_TOP) / ROW_HEIGHT);
            this.scrollOffset = Math.max(0, Math.min(scrollOffset, options.size() - visibleRows));

            int y = LIST_TOP;
            int last = Math.min(options.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < last; i++) {
                addOptionRow(options.get(i), y);
                y += ROW_HEIGHT;
            }
            if (focusId != null) {
                for (Row row : rows) {
                    if (row.option.id().equals(focusId)) {
                        setFocused(row.widget);
                    }
                }
                focusId = null;
            }
        }

        // The sub-screens are only worth offering where there is config to show at all. The button
        // that leaves is always the last one in the row, on every screen of the mod: here it used to
        // be the first, and on the region screen the last, which the last test round rightly called
        // inconsistent. "Mob lists…" sits next to it on both screens.
        if (showRows) {
            addRenderableWidget(Button.builder(Component.literal("Regions…"), b -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new MobStackerRegionScreen(this));
                }
            }).bounds(this.width / 2 - 152, this.height - 28, 98, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Mob lists…"), b -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(MobStackerListScreen.global(this));
                }
            }).bounds(this.width / 2 - 50, this.height - 28, 98, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(this.width / 2 + 52, this.height - 28, 98, 20).build());
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
        }
    }

    /** Called on the client thread when a fresh config snapshot arrives from the server. */
    public void onConfigSynced() {
        // Don't yank a text box the user is mid-typing in; the status line (rendered live) still
        // updates. Otherwise rebuild so button / cycle states reflect the authoritative values. The
        // search box is not a value, so typing in it does not hold the snapshot back.
        if (this.getFocused() instanceof EditBox box && box != searchBox) {
            return;
        }
        rebuild();
    }

    /** Builds the widgets again, keeping the search box's focus and cursor if it had them. */
    private void rebuild() {
        boolean searchFocused = searchBox != null && this.getFocused() == searchBox;
        int cursor = searchBox == null ? 0 : searchBox.getCursorPosition();
        rebuildWidgets();
        if (searchFocused && searchBox != null) {
            setFocused(searchBox);
            cursorTo(searchBox, Math.min(cursor, searchBox.getValue().length()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (highlightTicks > 0) {
            highlightTicks--;
        }
    }

    private boolean searching() {
        return !query.isBlank();
    }

    /** The settings the rows are made of: the search's hits, or the page's settings. */
    private List<ConfigOption> shownOptions() {
        if (searching()) {
            List<ConfigOption> found = new ArrayList<>();
            for (SettingSearch.Hit hit : hits) {
                found.add(hit.option());
            }
            return found;
        }
        return MobStackerSettings.byCategory(categories.get(categoryIndex));
    }

    private void onSearchTyped(String text) {
        if (text.equals(query)) {
            return;
        }
        query = text;
        hits = SettingSearch.search(text, MobStackerSettings.all());
        matched.clear();
        for (SettingSearch.Hit hit : hits) {
            matched.put(hit.option().id(), hit.matched());
        }
        scrollOffset = 0;
        // The box calls this last thing in its own key handling, so the old box is done with by now.
        rebuild();
    }

    /** Leaves the search for the page the setting is on, scrolled to it, glowing and focused. */
    private void showOnItsPage(ConfigOption option) {
        int page = categories.indexOf(option.category());
        if (page < 0) {
            return;
        }
        categoryIndex = page;
        int at = MobStackerSettings.byCategory(option.category()).indexOf(option);
        scrollOffset = Math.max(0, at - visibleRows / 2);
        query = "";
        hits = List.of();
        matched.clear();
        highlightId = option.id();
        highlightTicks = HIGHLIGHT_TICKS;
        focusId = option.id();
        rebuildWidgets();
    }

    private void switchCategory(int delta) {
        if (categories.isEmpty()) {
            return;
        }
        categoryIndex = Math.floorMod(categoryIndex + delta, categories.size());
        scrollOffset = 0;
        rebuild();
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double delta) {
        if (showRows && !categories.isEmpty() && delta != 0.0) {
            int maxOffset = maxScrollOffset();
            if (maxOffset > 0) {
                int next = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), maxOffset));
                if (next != scrollOffset) {
                    scrollOffset = next;
                    rebuild();
                }
                return true;
            }
        }
        return superScroll(mouseX, mouseY, delta);
    }

    private int maxScrollOffset() {
        if (categories.isEmpty()) {
            return 0;
        }
        return Math.max(0, shownOptions().size() - visibleRows);
    }

    private void addOptionRow(ConfigOption option, int y) {
        int widgetX = this.width / 2 + 30;
        int widgetW = 140;
        int widgetH = 20;
        Row row = new Row(option, y);
        boolean allowed = rowEditable(option);

        switch (option.type()) {
            case BOOL -> {
                boolean[] state = { Boolean.parseBoolean(valueOf(option)) };
                Button button = new StepButton(widgetX, y, widgetW, widgetH, boolLabel(state[0]), (b, direction) -> {
                    state[0] = !state[0];
                    b.setMessage(boolLabel(state[0]));
                    applyOption(option, String.valueOf(state[0]));
                });
                button.active = allowed;
                addRenderableWidget(button);
                row.widget = button;
                row.display = value -> {
                    state[0] = Boolean.parseBoolean(value);
                    button.setMessage(boolLabel(state[0]));
                };
            }
            case ENUM -> {
                List<String> values = option.enumValues();
                int[] index = { indexOfIgnoreCase(values, valueOf(option)) };
                if (index[0] < 0) {
                    index[0] = 0;
                }
                Button button = new StepButton(widgetX, y, widgetW, widgetH,
                        StepButton.enumLabel(option, values.get(index[0])), (b, direction) -> {
                    index[0] = StepButton.stepIndex(index[0], direction, values.size());
                    String value = values.get(index[0]);
                    b.setMessage(StepButton.enumLabel(option, value));
                    applyOption(option, value);
                });
                button.active = allowed;
                addRenderableWidget(button);
                row.widget = button;
                row.display = value -> {
                    int found = indexOfIgnoreCase(values, value);
                    if (found >= 0) {
                        index[0] = found;
                        button.setMessage(StepButton.enumLabel(option, values.get(found)));
                    }
                };
            }
            default -> {
                EditBox box = new EditBox(this.font, widgetX, y, widgetW, widgetH, Component.literal(option.id()));
                box.setValue(valueOf(option));
                // A list of data keys (ignoredDataKeys) needs more room than an item id.
                box.setMaxLength(option.type() == ConfigOption.Type.STRING ? 512 : 64);
                box.setEditable(allowed);
                IdCompletion completion = option.type() == ConfigOption.Type.ITEM ? itemCompletions.addItems() : null;
                if (completion != null) {
                    completion.attach(box);
                }
                box.setResponder(text -> {
                    if (isValid(option, text)) {
                        Gfx.textColor(box, NORMAL_TEXT);
                        applyOption(option, text);
                    } else {
                        Gfx.textColor(box, ERROR_TEXT);
                    }
                    // Only for what somebody typed: a repaint from the config is not a question, and a
                    // box that may not be edited never gets typed into.
                    if (completion != null && !repainting) {
                        completion.update(text);
                    }
                });
                addRenderableWidget(box);
                row.widget = box;
                row.display = value -> {
                    repainting = true;
                    box.setValue(value);
                    Gfx.textColor(box, NORMAL_TEXT);
                    repainting = false;
                };
            }
        }
        rows.add(row);
    }

    /**
     * Repaints the rows from the config itself after an edit. A value the config refuses - a setting
     * whose dependency is off, or one another setting forces - used to stay on the widget as if it
     * had been saved; now the widget always ends up showing what was really stored. It also re-locks
     * or unlocks the rows that depend on the setting just changed.
     */
    private void refreshRows() {
        for (Row row : rows) {
            setEnabled(row.widget, rowEditable(row.option));
            // Never yank a box out from under someone mid-typing.
            if (row.display != null && row.widget != this.getFocused()) {
                row.display.accept(valueOf(row.option));
            }
        }
    }

    /**
     * Whether this row may be touched: not while another setting forces it on, and not while the
     * setting it depends on is off - it reads as its own default then, so there is nothing to switch
     * back off and no way for a switch to sit on ON while doing nothing.
     */
    private boolean rowEditable(ConfigOption option) {
        return editable && rowProblem(option) == null;
    }

    /** Why this setting cannot be edited right now, or null when it can. */
    private String rowProblem(ConfigOption option) {
        String lock = lockReason(option);
        return lock != null ? lock : blockedReason(option);
    }

    /** Why this setting is pinned by another one (stackHealth forcing killWholeStackOnDeath), or null. */
    private String lockReason(ConfigOption option) {
        return MobStackerSettings.lockProblem(option, this::valueOfId, null);
    }

    /** Why this setting cannot be edited right now (the setting it depends on is off), or null. */
    private String blockedReason(ConfigOption option) {
        return MobStackerSettings.dependencyProblem(option, this::valueOfId, null);
    }

    private String valueOfId(String id) {
        ConfigOption other = MobStackerSettings.byId(id);
        return other == null ? null : valueOf(other);
    }

    private static void setEnabled(AbstractWidget widget, boolean enabled) {
        if (widget instanceof EditBox box) {
            box.setEditable(enabled);
        } else if (widget != null) {
            widget.active = enabled;
        }
    }

    /**
     * The value to display for an option: the server's snapshot when remote, else the live config.
     * Both carry the value as stored, so a setting another one forces on - or holds at its default,
     * because what it needs is off - is resolved here: the row shows what the game acts on rather
     * than the choice parked underneath it.
     */
    private String valueOf(ConfigOption option) {
        String effective = MobStackerSettings.effectiveValue(option, this::valueOfId);
        if (effective != null) {
            return effective;
        }
        if (remote) {
            String value = MobStackerClientNetworking.value(option.id());
            if (value != null) {
                return value;
            }
        }
        return option.storedValue();
    }

    private void applyOption(ConfigOption option, String raw) {
        if (!editable || repainting) {
            return;
        }
        if (remote) {
            // Optimistically keep the typed value across a rebuild; the server echo confirms/corrects.
            // Kept as the value it means, so "max" or "default" is shown as a number meanwhile
            // rather than as the word, just as the server will store it.
            String meant;
            try {
                meant = option.canonicalize(raw);
            } catch (IllegalArgumentException e) {
                meant = raw;
            }
            MobStackerClientNetworking.rememberLocal(option.id(), meant);
            MobStackerClientNetworking.sendEdit(option.id(), raw);
            refreshRows();
        } else if (this.minecraft != null) {
            Minecraft client = this.minecraft;
            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) {
                // Mutate the config on the server thread, then repaint from what it actually stored -
                // an edit the config refuses must not be left sitting on the widget.
                server.execute(() -> {
                    option.apply(raw);
                    client.execute(this::refreshRows);
                });
            }
        }
    }

    private boolean isValid(ConfigOption option, String text) {
        String value = text.trim();
        // "max" and "default" are values like any other here, so the box does not go red while
        // somebody types one.
        if (ConfigOption.isDefaultKeyword(value)) {
            return true;
        }
        if (ConfigOption.MAX_KEYWORD.equalsIgnoreCase(value)
                && (option.type() == ConfigOption.Type.INT || option.type() == ConfigOption.Type.DOUBLE)) {
            return true;
        }
        switch (option.type()) {
            case INT -> {
                try {
                    long parsed = Long.parseLong(value);
                    return parsed >= option.min() && parsed <= option.max();
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case DOUBLE -> {
                try {
                    double parsed = Double.parseDouble(value);
                    return parsed >= option.min() && parsed <= option.max();
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case ITEM -> {
                ResourceLocation id = ResourceLocation.tryParse(value);
                return id != null && BuiltInRegistries.ITEM.containsKey(id);
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    protected boolean onKey(int keyCode, int scanCode, int modifiers) {
        if (itemCompletions.keyPressed(keyCode, this.getFocused())) {
            return true;
        }
        if (searchBox != null) {
            if (keyCode == InputConstants.KEY_F && controlDown(modifiers)) {
                setFocused(searchBox);
                cursorToEnd(searchBox);
                return true;
            }
            if (this.getFocused() == searchBox) {
                // Esc empties the box first and closes the screen only once it is empty.
                if (keyCode == InputConstants.KEY_ESCAPE && !query.isEmpty()) {
                    searchBox.setValue("");
                    return true;
                }
                if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) && !hits.isEmpty()) {
                    showOnItsPage(hits.get(0).option());
                    return true;
                }
            }
        }
        return superKey(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean typesAnywhere() {
        return searchBox != null;
    }

    /** Typing with no text box in focus starts a search, so there is nothing to click first. */
    @Override
    protected boolean onChar(char typed, int modifiers) {
        if (searchBox != null && !(this.getFocused() instanceof EditBox) && !Character.isWhitespace(typed)
                && com.frikinjay.mobstacker.compat.Compat.isAllowedChatCharacter(typed)) {
            setFocused(searchBox);
            cursorToEnd(searchBox);
            return typeInto(searchBox, typed, modifiers);
        }
        return superChar(typed, modifiers);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (itemCompletions.mouseClicked(mouseX, mouseY, this.height)) {
            return true;
        }
        if (searching() && button == InputConstants.MOUSE_BUTTON_LEFT) {
            Row row = rowAt((int) mouseX, (int) mouseY);
            if (row != null) {
                showOnItsPage(row.option);
                return true;
            }
        }
        return superClick(mouseX, mouseY, button);
    }

    @Override
    protected void draw(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderHighlight(guiGraphics, partialTick);
        superRender(guiGraphics, mouseX, mouseY, partialTick);
        // Lifted in front of everything but tooltips, so drawing it before the labels is fine.
        itemCompletions.render(guiGraphics, this.height, mouseX, mouseY);

        Gfx.centered(guiGraphics, this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);

        if (showRows) {
            renderRows(guiGraphics, mouseX, mouseY);
        } else if (remote) {
            // Connected to a mod server, still waiting for the first snapshot.
            Gfx.centered(guiGraphics, this.font, Component.literal("Loading config from the server…").withStyle(ChatFormatting.GRAY), this.width / 2, this.height / 2 - 4, 0xFFFFFF);
        } else if (connectedNoMod) {
            // On a server that doesn't run MobStacker: say so plainly, and don't point at the
            // /mobstacker commands (they don't exist there either).
            Gfx.centered(guiGraphics, this.font, Component.literal("This server doesn't have MobStacker: Restacked installed.")
                            .withStyle(ChatFormatting.YELLOW), this.width / 2, this.height / 2 - 16, 0xFFFFFF);
            Gfx.centered(guiGraphics, this.font, Component.literal("There are no MobStacker settings to configure here.")
                            .withStyle(ChatFormatting.GRAY), this.width / 2, this.height / 2, 0xFFFFFF);
        } else {
            Gfx.centered(guiGraphics, this.font, Component.literal("Editing works in singleplayer, or on a MobStacker server as an operator.")
                            .withStyle(ChatFormatting.GRAY), this.width / 2, this.height / 2 - 16, 0xFFFFFF);
            Gfx.centered(guiGraphics, this.font, Component.literal("On other servers, configure with /mobstacker commands.")
                            .withStyle(ChatFormatting.GRAY), this.width / 2, this.height / 2, 0xFFFFFF);
        }
    }

    /** The glow behind a setting a search led to, fading out; drawn under the widgets. */
    private void renderHighlight(GuiGraphics guiGraphics, float partialTick) {
        if (highlightTicks <= 0 || highlightId == null || searching()) {
            return;
        }
        float left = Math.max(0.0F, highlightTicks - partialTick);
        int alpha = (int) (0x50 * Math.min(1.0F, left / 20.0F));
        for (Row row : rows) {
            if (row.option.id().equals(highlightId)) {
                guiGraphics.fill(this.width / 2 - 174, row.y - 2, this.width / 2 + 174, row.y + ROW_HEIGHT - 2,
                        (alpha << 24) | (MATCHED_TEXT & 0xFFFFFF));
            }
        }
    }

    private void renderRows(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int headerY = CATEGORY_Y + 6;
        int hintY = CATEGORY_Y + 17;
        if (searching()) {
            Component header = hits.isEmpty()
                    ? Component.literal("Search: nothing matches").withStyle(ChatFormatting.RED)
                    : Component.literal("Search: " + hits.size() + (hits.size() == 1 ? " setting" : " settings"))
                            .withStyle(ChatFormatting.GOLD);
            Gfx.centered(guiGraphics, this.font, header, this.width / 2, headerY, 0xFFFFFF);
            if (hits.isEmpty()) {
                Gfx.centered(guiGraphics, this.font, Component.literal("Try fewer letters, or a word from what the setting does.")
                                .withStyle(ChatFormatting.GRAY), this.width / 2, LIST_TOP + 6, 0xFFFFFF);
            } else if (maxScrollOffset() == 0) {
                Gfx.centered(guiGraphics, this.font, Component.literal("Enter or a click on a name: see it on its page").withStyle(ChatFormatting.DARK_GRAY), this.width / 2, hintY, 0xFFFFFF);
            }
        } else if (!categories.isEmpty()) {
            Category category = categories.get(categoryIndex);
            Component header = Component.literal(category.display() + "  (" + (categoryIndex + 1) + "/" + categories.size() + ")")
                    .withStyle(ChatFormatting.GOLD);
            Gfx.centered(guiGraphics, this.font, header, this.width / 2, headerY, 0xFFFFFF);
        }

        if (maxScrollOffset() > 0) {
            int total = shownOptions().size();
            Component hint = Component.literal("scroll for more  (" + (scrollOffset + 1) + "-"
                            + Math.min(total, scrollOffset + visibleRows) + " of " + total + ")")
                    .withStyle(ChatFormatting.DARK_GRAY);
            Gfx.centered(guiGraphics, this.font, hint, this.width / 2, hintY, 0xFFFFFF);
        }

        int labelX = this.width / 2 - 170;
        for (Row row : rows) {
            if (searching()) {
                // The name with what matched in yellow, and the page it lives on under it.
                Gfx.text(guiGraphics, this.font, markedName(row.option), labelX, row.y + 1, NORMAL_TEXT);
                Gfx.text(guiGraphics, this.font, Component.literal("in " + row.option.category().display())
                        .withStyle(ChatFormatting.GRAY), labelX, row.y + 12, NORMAL_TEXT);
            } else {
                Gfx.text(guiGraphics, this.font, row.option.id(), labelX, row.y + 6, NORMAL_TEXT);
            }
        }

        // A short note about why edits are disabled, when relevant.
        if (remote && !editable) {
            Gfx.centered(guiGraphics, this.font, Component.literal("Read-only — operator permission is required to edit.").withStyle(ChatFormatting.GRAY), this.width / 2, this.height - 46, 0xFFFFFF);
        } else if (remote) {
            String status = MobStackerClientNetworking.status();
            if (status != null && !status.isEmpty()) {
                // A server's answer can be longer than the window is wide: cut, and whole on hover.
                ScreenText.Line line = ScreenText.drawCentered(guiGraphics, this.font,
                        Component.literal(status).withStyle(ChatFormatting.YELLOW),
                        this.width / 2, this.height - 46, this.width - 16);
                if (line.renderTooltip(guiGraphics, this.font, this.width, mouseX, mouseY)) {
                    return;
                }
            }
        }

        Row hovered = rowAt(mouseX, mouseY);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.option.description()).withStyle(ChatFormatting.WHITE));
            String blocked = rowProblem(hovered.option);
            if (blocked != null) {
                lines.add(Component.literal(blocked).withStyle(ChatFormatting.RED));
            }
            if (searching()) {
                lines.add(Component.literal("Click to see it on its page, " + hovered.option.category().display() + ".")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            ScreenTooltip.render(guiGraphics, this.font, lines, this.width, mouseX, mouseY);
        }
    }

    /** A setting's id with the letters the search matched in yellow. */
    private Component markedName(ConfigOption option) {
        String id = option.id();
        boolean[] marks = matched.get(id);
        MutableComponent name = Component.empty();
        int i = 0;
        while (i < id.length()) {
            boolean on = marks != null && i < marks.length && marks[i];
            int end = i;
            while (end < id.length() && (marks != null && end < marks.length && marks[end]) == on) {
                end++;
            }
            name.append(Component.literal(id.substring(i, end))
                    .withStyle(Style.EMPTY.withColor(on ? MATCHED_TEXT : NORMAL_TEXT)));
            i = end;
        }
        return name;
    }

    private Row rowAt(int mouseX, int mouseY) {
        // Only trigger the description tooltip over the actual label text, not the empty space
        // around it: bound the hit-box to the label's rendered width and line height. A search's
        // label is two lines, the name and its page.
        int labelX = this.width / 2 - 170;
        boolean twoLines = searching();
        for (Row row : rows) {
            int labelWidth = this.font.width(row.option.id());
            int labelTop = row.y + 6;
            int labelBottom = labelTop + this.font.lineHeight;
            if (twoLines) {
                labelWidth = Math.max(labelWidth, this.font.width("in " + row.option.category().display()));
                labelTop = row.y + 1;
                labelBottom = row.y + 12 + this.font.lineHeight;
            }
            if (mouseX >= labelX && mouseX <= labelX + labelWidth && mouseY >= labelTop && mouseY <= labelBottom) {
                return row;
            }
        }
        return null;
    }

    private static Component boolLabel(boolean value) {
        return Component.literal(value ? "ON" : "OFF").withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static int indexOfIgnoreCase(List<String> values, String target) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(target)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private static final class Row {
        private final ConfigOption option;
        private final int y;
        private AbstractWidget widget;
        /** Shows the given value in this row's widget, so it can be repainted from the config. */
        private Consumer<String> display;

        private Row(ConfigOption option, int y) {
            this.option = option;
            this.y = y;
        }
    }
}
