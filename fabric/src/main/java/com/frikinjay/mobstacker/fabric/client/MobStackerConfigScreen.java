package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import com.frikinjay.mobstacker.config.MobStackerSettings;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
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
 */
public final class MobStackerConfigScreen extends Screen {
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int ERROR_TEXT = 0xFF5555;

    private final Screen parent;
    private final List<Category> categories = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int categoryIndex;
    // A category can hold more settings than fit on screen (and a small window fits very few), so a
    // page shows a window of rows that the mouse wheel moves through.
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 52;
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
            addRenderableWidget(Button.builder(Component.literal("<"), b -> switchCategory(-1))
                    .bounds(this.width / 2 - 170, 24, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> switchCategory(1))
                    .bounds(this.width / 2 + 150, 24, 20, 20).build());

            List<ConfigOption> options = MobStackerSettings.byCategory(categories.get(categoryIndex));
            // Keep clear of the status line (height - 46) and the Done button (height - 28).
            this.visibleRows = Math.max(1, (this.height - LIST_BOTTOM_MARGIN - LIST_TOP) / ROW_HEIGHT);
            this.scrollOffset = Math.max(0, Math.min(scrollOffset, options.size() - visibleRows));

            int y = LIST_TOP;
            int last = Math.min(options.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < last; i++) {
                addOptionRow(options.get(i), y);
                y += ROW_HEIGHT;
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
        // updates. Otherwise rebuild so button / cycle states reflect the authoritative values.
        if (this.getFocused() instanceof EditBox) {
            return;
        }
        rebuildWidgets();
    }

    private void switchCategory(int delta) {
        if (categories.isEmpty()) {
            return;
        }
        categoryIndex = Math.floorMod(categoryIndex + delta, categories.size());
        scrollOffset = 0;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showRows && !categories.isEmpty() && delta != 0.0) {
            int maxOffset = maxScrollOffset();
            if (maxOffset > 0) {
                int next = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), maxOffset));
                if (next != scrollOffset) {
                    scrollOffset = next;
                    rebuildWidgets();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int maxScrollOffset() {
        if (categories.isEmpty()) {
            return 0;
        }
        return Math.max(0, MobStackerSettings.byCategory(categories.get(categoryIndex)).size() - visibleRows);
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
                Button button = Button.builder(boolLabel(state[0]), b -> {
                    state[0] = !state[0];
                    b.setMessage(boolLabel(state[0]));
                    applyOption(option, String.valueOf(state[0]));
                }).bounds(widgetX, y, widgetW, widgetH).build();
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
                Button button = Button.builder(enumLabel(values.get(index[0])), b -> {
                    index[0] = (index[0] + 1) % values.size();
                    String value = values.get(index[0]);
                    b.setMessage(enumLabel(value));
                    applyOption(option, value);
                }).bounds(widgetX, y, widgetW, widgetH).build();
                button.active = allowed;
                addRenderableWidget(button);
                row.widget = button;
                row.display = value -> {
                    int found = indexOfIgnoreCase(values, value);
                    if (found >= 0) {
                        index[0] = found;
                        button.setMessage(enumLabel(values.get(found)));
                    }
                };
            }
            default -> {
                EditBox box = new EditBox(this.font, widgetX, y, widgetW, widgetH, Component.literal(option.id()));
                box.setValue(valueOf(option));
                box.setMaxLength(64);
                box.setEditable(allowed);
                box.setResponder(text -> {
                    if (isValid(option, text)) {
                        box.setTextColor(NORMAL_TEXT);
                        applyOption(option, text);
                    } else {
                        box.setTextColor(ERROR_TEXT);
                    }
                });
                addRenderableWidget(box);
                row.widget = box;
                row.display = value -> {
                    repainting = true;
                    box.setValue(value);
                    box.setTextColor(NORMAL_TEXT);
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
            MobStackerClientNetworking.rememberLocal(option.id(), raw);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (showRows) {
            renderRows(guiGraphics, mouseX, mouseY);
        } else if (remote) {
            // Connected to a mod server, still waiting for the first snapshot.
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Loading config from the server…").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2 - 4, 0xFFFFFF);
        } else if (connectedNoMod) {
            // On a server that doesn't run MobStacker: say so plainly, and don't point at the
            // /mobstacker commands (they don't exist there either).
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("This server doesn't have MobStacker: Restacked installed.")
                            .withStyle(ChatFormatting.YELLOW),
                    this.width / 2, this.height / 2 - 16, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("There are no MobStacker settings to configure here.")
                            .withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2, 0xFFFFFF);
        } else {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Editing works in singleplayer, or on a MobStacker server as an operator.")
                            .withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2 - 16, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("On other servers, configure with /mobstacker commands.")
                            .withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2, 0xFFFFFF);
        }
    }

    private void renderRows(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!categories.isEmpty()) {
            Category category = categories.get(categoryIndex);
            Component header = Component.literal(category.display() + "  (" + (categoryIndex + 1) + "/" + categories.size() + ")")
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawCenteredString(this.font, header, this.width / 2, 30, 0xFFFFFF);
        }

        if (maxScrollOffset() > 0) {
            int total = MobStackerSettings.byCategory(categories.get(categoryIndex)).size();
            Component hint = Component.literal("scroll for more  (" + (scrollOffset + 1) + "-"
                            + Math.min(total, scrollOffset + visibleRows) + " of " + total + ")")
                    .withStyle(ChatFormatting.DARK_GRAY);
            guiGraphics.drawCenteredString(this.font, hint, this.width / 2, 41, 0xFFFFFF);
        }

        for (Row row : rows) {
            guiGraphics.drawString(this.font, row.option.id(), this.width / 2 - 170, row.y + 6, NORMAL_TEXT);
        }

        // A short note about why edits are disabled, when relevant.
        if (remote && !editable) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Read-only — operator permission is required to edit.").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height - 46, 0xFFFFFF);
        } else if (remote) {
            String status = MobStackerClientNetworking.status();
            if (status != null && !status.isEmpty()) {
                guiGraphics.drawCenteredString(this.font,
                        Component.literal(status).withStyle(ChatFormatting.YELLOW),
                        this.width / 2, this.height - 46, 0xFFFFFF);
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
            ScreenTooltip.render(guiGraphics, this.font, lines, this.width, mouseX, mouseY);
        }
    }

    private Row rowAt(int mouseX, int mouseY) {
        // Only trigger the description tooltip over the actual label text, not the empty space
        // around it: bound the hit-box to the label's rendered width and line height.
        int labelX = this.width / 2 - 170;
        for (Row row : rows) {
            int labelWidth = this.font.width(row.option.id());
            int labelTop = row.y + 6;
            if (mouseX >= labelX && mouseX <= labelX + labelWidth
                    && mouseY >= labelTop && mouseY <= labelTop + this.font.lineHeight) {
                return row;
            }
        }
        return null;
    }

    private static Component boolLabel(boolean value) {
        return Component.literal(value ? "ON" : "OFF").withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static Component enumLabel(String value) {
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
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
