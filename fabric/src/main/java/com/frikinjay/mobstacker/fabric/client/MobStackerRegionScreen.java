package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import com.frikinjay.mobstacker.config.StackRegion;
import com.frikinjay.mobstacker.fabric.network.MobStackerNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * The per-region editor: the same registry-driven rows as {@link MobStackerConfigScreen}, but every
 * value belongs to one region instead of the world.
 * <p>
 * A region only stores the settings it actually changes, so each row shows either the region's own
 * value (a gold label) or the global one it inherits (a grey label), and the small button beside it
 * drops the override again. Settings that cannot differ per region — the stack mode
 * and the mob caps — are simply not offered here.
 * <p>
 * It works in the same three situations as the main screen: singleplayer edits the integrated
 * server's config directly, a remote server with the mod is edited over the config-sync channel (as
 * an operator; read-only otherwise), and anywhere else there is nothing to show.
 */
public final class MobStackerRegionScreen extends Screen {
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int ERROR_TEXT = 0xFF5555;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 94;
    private static final int LIST_BOTTOM_MARGIN = 52;

    private final Screen parent;
    private final List<Category> categories = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private List<RegionView> regions = new ArrayList<>();

    private int regionIndex;
    private int categoryIndex;
    private int scrollOffset;
    private int visibleRows = 1;

    private boolean remote;
    private boolean editable;
    private boolean showRows;
    /** Set while a widget is being repainted, so its own responder does not send that back as an edit. */
    private boolean repainting;

    /** A region as the screen needs it, from the local config or from the server's snapshot. */
    private record RegionView(String name, String type, String dimension, String bounds, int priority) {
    }

    /**
     * One editable setting on screen. The override state is mutable because dropping an override
     * has to repaint the row straight away, without rebuilding (and unfocusing) every widget.
     */
    private static final class Row {
        final ConfigOption option;
        final int y;
        boolean overridden;
        Button clear;
        AbstractWidget widget;
        /** Shows the given value in this row's widget, e.g. after an override was dropped. */
        Consumer<String> display;

        Row(ConfigOption option, int y, boolean overridden) {
            this.option = option;
            this.y = y;
            this.overridden = overridden;
        }
    }

    public MobStackerRegionScreen(Screen parent) {
        super(Component.literal("MobStacker: Restacked — Regions"));
        this.parent = parent;
        for (Category category : Category.values()) {
            if (!overridableIn(category).isEmpty()) {
                categories.add(category);
            }
        }
    }

    /** The settings of one category that a region is allowed to override. */
    private static List<ConfigOption> overridableIn(Category category) {
        List<ConfigOption> out = new ArrayList<>();
        for (ConfigOption option : MobStackerSettings.byCategory(category)) {
            if (MobStackerSettings.isRegionOverridable(option.id())) {
                out.add(option);
            }
        }
        return out;
    }

    @Override
    protected void init() {
        rows.clear();

        boolean singleplayer = this.minecraft != null && this.minecraft.hasSingleplayerServer();
        this.remote = !singleplayer && MobStackerClientNetworking.serverHasMod();
        this.editable = singleplayer || (remote && MobStackerClientNetworking.authorized());
        this.regions = loadRegions(singleplayer);
        this.showRows = !regions.isEmpty() && !categories.isEmpty()
                && (singleplayer || (remote && MobStackerClientNetworking.hasSnapshot()));

        if (showRows) {
            this.regionIndex = Math.floorMod(regionIndex, regions.size());

            addRenderableWidget(Button.builder(Component.literal("<"), b -> switchRegion(-1))
                    .bounds(this.width / 2 - 170, 20, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> switchRegion(1))
                    .bounds(this.width / 2 + 150, 20, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("<"), b -> switchCategory(-1))
                    .bounds(this.width / 2 - 170, 44, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> switchCategory(1))
                    .bounds(this.width / 2 + 150, 44, 20, 20).build());

            addPriorityBox();

            List<ConfigOption> options = overridableIn(categories.get(categoryIndex));
            this.visibleRows = Math.max(1, (this.height - LIST_BOTTOM_MARGIN - LIST_TOP) / ROW_HEIGHT);
            this.scrollOffset = Math.max(0, Math.min(scrollOffset, options.size() - visibleRows));

            int y = LIST_TOP;
            int last = Math.min(options.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < last; i++) {
                addOptionRow(options.get(i), y);
                y += ROW_HEIGHT;
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private List<RegionView> loadRegions(boolean singleplayer) {
        List<RegionView> out = new ArrayList<>();
        if (singleplayer) {
            for (StackRegion region : MobStacker.config.getRegions()) {
                out.add(new RegionView(region.getName(), String.valueOf(region.getType()),
                        region.getDimension() == null ? "?" : region.getDimension(),
                        region.describeBounds(), region.getPriority()));
            }
        } else if (remote) {
            for (MobStackerClientNetworking.RegionInfo info : MobStackerClientNetworking.regions()) {
                out.add(new RegionView(info.name(), info.type(), info.dimension(), info.bounds(), info.priority()));
            }
        }
        return out;
    }

    private RegionView currentRegion() {
        return regions.get(regionIndex);
    }

    private void addPriorityBox() {
        EditBox box = new EditBox(this.font, this.width / 2 + 30, 68, 60, 20, Component.literal("priority"));
        box.setValue(String.valueOf(currentRegion().priority()));
        box.setMaxLength(11);
        box.setEditable(editable);
        box.setResponder(text -> {
            String value = text.trim();
            boolean valid = !value.isEmpty() && !value.equals("-");
            if (valid) {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    valid = false;
                }
            }
            box.setTextColor(valid ? NORMAL_TEXT : ERROR_TEXT);
            if (valid) {
                applyEdit(MobStackerNetworking.REGION_PRIORITY, value);
            }
        });
        addRenderableWidget(box);
    }

    private void addOptionRow(ConfigOption option, int y) {
        int widgetX = this.width / 2 + 30;
        int widgetW = 140;
        int widgetH = 20;
        Row row = new Row(option, y, regionValue(option.id()) != null);
        boolean allowed = rowEditable(option);

        // Drops the override, so the setting follows the global config again. Always allowed: going
        // back to the global value is how a setting is switched off again.
        Button clear = Button.builder(Component.literal("↺"), b -> applyEdit(option.id(), ""))
                .bounds(this.width / 2 + 6, y, 20, widgetH).build();
        clear.active = editable && row.overridden;
        row.clear = clear;
        addRenderableWidget(clear);

        switch (option.type()) {
            case BOOL -> {
                boolean[] state = { Boolean.parseBoolean(valueOf(option)) };
                Button button = Button.builder(boolLabel(state[0]), b -> {
                    state[0] = !state[0];
                    b.setMessage(boolLabel(state[0]));
                    applyEdit(option.id(), String.valueOf(state[0]));
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
                    applyEdit(option.id(), value);
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
                        applyEdit(option.id(), text);
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
     * Repaints the rows against what the region currently stores. Called right after an edit, so the
     * gold label and the {@code ↺} button follow the change immediately instead of waiting for the
     * screen to be rebuilt. Only rows whose override state actually changed are touched, which keeps
     * a box the player is typing in out of the way.
     */
    private void refreshRows() {
        for (Row row : rows) {
            // A setting whose dependency was just switched on (or off) in here becomes editable
            // (or stops being) without leaving the screen.
            setEnabled(row.widget, rowEditable(row.option));

            boolean overridden = regionValue(row.option.id()) != null;
            if (overridden == row.overridden) {
                continue;
            }
            row.overridden = overridden;
            if (row.clear != null) {
                row.clear.active = editable && overridden;
            }
            // The override is gone, so the row now shows whatever the global config says.
            if (!overridden && row.display != null) {
                row.display.accept(globalValue(row.option));
            }
        }
    }

    /**
     * Whether this row may be touched. A setting another one forces on in this region cannot be
     * changed at all; a setting whose dependency is off here is locked too - except while it still
     * holds a non-default value, because there has to be a way to switch it back off. The clear
     * button stays usable either way.
     */
    private boolean rowEditable(ConfigOption option) {
        if (!editable || lockReason(option) != null) {
            return false;
        }
        return blockedReason(option) == null
                || !valueOf(option).equalsIgnoreCase(option.defaultValue());
    }

    /** Why this setting cannot be edited in this region, or null when it can. */
    private String rowProblem(ConfigOption option) {
        String lock = lockReason(option);
        return lock != null ? lock : blockedReason(option);
    }

    /**
     * Why this setting is pinned by another one here — {@code stackHealth} forcing
     * {@code killWholeStackOnDeath} — or null. Judged on the region's own values, so a region that
     * turns {@code stackHealth} off for itself may set it freely even where the world forces it.
     */
    private String lockReason(ConfigOption option) {
        if (regions.isEmpty()) {
            return null;
        }
        return MobStackerSettings.lockProblem(option, this::valueOfId, currentRegion().name());
    }

    /**
     * Why this setting cannot be edited in this region — the setting it depends on is off here — or
     * null when it can. Resolved against the region first, so a region that enables
     * {@code sweepingEdgeOverflow} for itself unlocks the options built on it even when the global
     * config has it off.
     */
    private String blockedReason(ConfigOption option) {
        if (regions.isEmpty()) {
            return null;
        }
        return MobStackerSettings.dependencyProblem(option, this::valueOfId, currentRegion().name());
    }

    /** The value in force in this region for another setting's id, for dependency checks. */
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

    /** The value this region gives the setting, or null when it follows the global config. */
    private String regionValue(String id) {
        if (regions.isEmpty()) {
            return null;
        }
        String name = currentRegion().name();
        if (remote) {
            return MobStackerClientNetworking.regionValue(name, id);
        }
        StackRegion region = MobStacker.config.getRegion(name);
        return region == null ? null : region.getSetting(id);
    }

    /**
     * What the row shows: whatever another setting forces on it in here, else the region's own value
     * when it has one, else the global value. The lock is resolved against this region, so a region
     * that turns {@code stackHealth} on shows {@code killWholeStackOnDeath} as the game reads it
     * there — and a region that turns it off does not inherit the world's lock.
     */
    private String valueOf(ConfigOption option) {
        String locked = MobStackerSettings.lockedValue(option, this::valueOfId);
        if (locked != null) {
            return locked;
        }
        String override = regionValue(option.id());
        return override != null ? override : globalValue(option);
    }

    /**
     * The value this setting has outside the region, i.e. what dropping the override falls back to.
     * As stored: any lock is applied per region by {@link #valueOf}, never carried in from the world.
     */
    private String globalValue(ConfigOption option) {
        if (remote) {
            String global = MobStackerClientNetworking.value(option.id());
            if (global != null) {
                return global;
            }
        }
        return option.storedValue();
    }

    /**
     * Sends one change for the selected region: a setting id with a value, an empty value to drop
     * the override, or {@link MobStackerNetworking#REGION_PRIORITY} for the region's priority.
     */
    private void applyEdit(String id, String raw) {
        if (!editable || repainting || regions.isEmpty()) {
            return;
        }
        String name = currentRegion().name();
        String value = raw;
        if (!MobStackerNetworking.REGION_PRIORITY.equals(id) && !value.isEmpty()) {
            ConfigOption option = MobStackerSettings.byId(id);
            if (option != null && value.trim().equalsIgnoreCase(globalValue(option))) {
                // Picking exactly what the global config already says is not an override: the row
                // goes back to following it, so a gold label always means "different in here".
                value = "";
            }
        }

        final String edit = value;
        if (remote) {
            if (!MobStackerNetworking.REGION_PRIORITY.equals(id)) {
                MobStackerClientNetworking.rememberRegionLocal(name, id, edit);
            }
            MobStackerClientNetworking.sendEdit(MobStackerNetworking.REGION_PREFIX + name + ":" + id, edit);
            refreshRows();
        } else if (this.minecraft != null) {
            Minecraft client = this.minecraft;
            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) {
                // The config lives on the server thread, so the rows are repainted once it has
                // really applied the change - otherwise the row would still claim the old state.
                server.execute(() -> {
                    applyOnServer(name, id, edit);
                    client.execute(this::refreshRows);
                });
            }
        }
    }

    /** Singleplayer path: the same change, applied straight to the integrated server's config. */
    private static void applyOnServer(String regionName, String id, String raw) {
        StackRegion region = MobStacker.config.getRegion(regionName);
        if (region == null) {
            return;
        }
        if (MobStackerNetworking.REGION_PRIORITY.equals(id)) {
            try {
                region.setPriority(Integer.parseInt(raw.trim()));
                MobStacker.config.save();
            } catch (NumberFormatException ignored) {
                // The box already marks an unparseable priority red.
            }
            return;
        }
        if (raw.isEmpty()) {
            if (region.clearSetting(id)) {
                MobStacker.config.save();
            }
            return;
        }
        ConfigOption option = MobStackerSettings.byId(id);
        if (option == null || !MobStackerSettings.isRegionOverridable(option.id())) {
            return;
        }
        try {
            String canonical = option.canonicalize(raw);
            // Judged against this region's own values, so a region that enables sweepingEdgeOverflow
            // for itself may use the options built on it even when the global config has it off.
            if (MobStackerSettings.regionEditProblem(option, region, canonical) != null) {
                return;
            }
            region.setSetting(option.id(), canonical);
            MobStacker.config.save();
        } catch (IllegalArgumentException ignored) {
            // Rejected values stay red in the box; the stored value is left alone.
        }
    }

    private boolean isValid(ConfigOption option, String text) {
        String value = text.trim();
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

    private void switchRegion(int delta) {
        regionIndex = Math.floorMod(regionIndex + delta, regions.size());
        scrollOffset = 0;
        rebuildWidgets();
    }

    private void switchCategory(int delta) {
        categoryIndex = Math.floorMod(categoryIndex + delta, categories.size());
        scrollOffset = 0;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showRows && delta != 0.0) {
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
        return Math.max(0, overridableIn(categories.get(categoryIndex)).size() - visibleRows);
    }

    /** Called on the client thread when a fresh config snapshot arrives from the server. */
    public void onConfigSynced() {
        if (this.getFocused() instanceof EditBox) {
            return;
        }
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        if (!showRows) {
            renderEmptyNotice(guiGraphics);
            return;
        }

        RegionView region = currentRegion();
        ChatFormatting typeColor = "DENY".equalsIgnoreCase(region.type()) ? ChatFormatting.RED : ChatFormatting.GREEN;
        guiGraphics.drawCenteredString(this.font,
                Component.literal(region.name() + "  [" + region.type() + "]").withStyle(typeColor),
                this.width / 2, 26, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.literal(categories.get(categoryIndex).display()
                        + "  (" + (categoryIndex + 1) + "/" + categories.size() + ")").withStyle(ChatFormatting.GOLD),
                this.width / 2, 50, 0xFFFFFF);
        guiGraphics.drawString(this.font,
                Component.literal("priority").withStyle(ChatFormatting.GRAY),
                this.width / 2 - 170, 74, NORMAL_TEXT);

        if (maxScrollOffset() > 0) {
            int total = overridableIn(categories.get(categoryIndex)).size();
            guiGraphics.drawCenteredString(this.font, Component.literal("scroll for more  ("
                            + (scrollOffset + 1) + "-" + Math.min(total, scrollOffset + visibleRows)
                            + " of " + total + ")").withStyle(ChatFormatting.GRAY),
                    this.width / 2, 84, 0xFFFFFF);
        }

        // Gold means the region has its own value for that setting; grey means it follows the
        // global config. Keeping it on the label avoids a second column that long ids would run into.
        for (Row row : rows) {
            Component label = Component.literal(row.option.id())
                    .withStyle(row.overridden ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            guiGraphics.drawString(this.font, label, this.width / 2 - 170, row.y + 6, NORMAL_TEXT);
        }

        renderFooter(guiGraphics);

        if (overPriority(mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.literal("priority").withStyle(ChatFormatting.WHITE),
                    Component.literal("Which region wins where two of them overlap:").withStyle(ChatFormatting.GRAY),
                    Component.literal("the higher priority first, then the smaller region.").withStyle(ChatFormatting.GRAY),
                    Component.literal("A deny region always stops stacking, whatever its priority.")
                            .withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return;
        }

        Row hovered = rowAt(mouseX, mouseY);
        if (hovered != null) {
            // Say in words what the colour means, so a value that happens to match the global one
            // is still clearly either the region's own or inherited.
            String global = globalValue(hovered.option);
            Component state = hovered.overridden
                    ? Component.literal("Set in this region  (global: " + global + ")").withStyle(ChatFormatting.GOLD)
                    : Component.literal("Follows the global config  (" + global + ")").withStyle(ChatFormatting.GRAY);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.option.description()).withStyle(ChatFormatting.WHITE));
            lines.add(state);
            String blocked = rowProblem(hovered.option);
            if (blocked != null) {
                lines.add(Component.literal(blocked).withStyle(ChatFormatting.RED));
            }
            guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    /** True over the priority label or its box, which share one tooltip. */
    private boolean overPriority(int mouseX, int mouseY) {
        if (!showRows) {
            return false;
        }
        int labelX = this.width / 2 - 170;
        boolean overLabel = mouseX >= labelX && mouseX <= labelX + this.font.width("priority")
                && mouseY >= 74 && mouseY <= 74 + this.font.lineHeight;
        boolean overBox = mouseX >= this.width / 2 + 30 && mouseX <= this.width / 2 + 90
                && mouseY >= 68 && mouseY <= 88;
        return overLabel || overBox;
    }

    private void renderEmptyNotice(GuiGraphics guiGraphics) {
        if (remote && !MobStackerClientNetworking.hasSnapshot()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Loading regions from the server…").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2 - 4, 0xFFFFFF);
            return;
        }
        guiGraphics.drawCenteredString(this.font,
                Component.literal("No regions defined.").withStyle(ChatFormatting.YELLOW),
                this.width / 2, this.height / 2 - 16, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.literal("Create one with /mobstacker region add <name> allow <corner> <corner>")
                        .withStyle(ChatFormatting.GRAY),
                this.width / 2, this.height / 2, 0xFFFFFF);
    }

    private void renderFooter(GuiGraphics guiGraphics) {
        RegionView region = currentRegion();
        // Readable grey, not the dark grey that looks like a disabled line lying behind the screen.
        Component footer = Component.literal(region.dimension() + "  " + region.bounds())
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("   -   ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("gold").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" = set here, grey = global").withStyle(ChatFormatting.GRAY));
        guiGraphics.drawCenteredString(this.font, footer, this.width / 2, this.height - 58, 0xFFFFFF);

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
    }

    private Row rowAt(int mouseX, int mouseY) {
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

    private static int indexOfIgnoreCase(List<String> values, String value) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(value)) {
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
}
