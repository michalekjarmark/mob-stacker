package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
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

/**
 * Registry-driven config GUI: it iterates the same {@link MobStackerSettings} as the commands and
 * renders one widget per {@link ConfigOption.Type} (on/off + cycle buttons, edit boxes for numbers
 * and item ids), one {@link Category} page at a time.
 * <p>
 * Editing is wired to the integrated server's config in singleplayer / on the LAN host (mutations
 * run on the server thread). On a multiplayer client the screen is informational only — pushing
 * changes to a remote server needs config-sync networking, which is a later phase; until then the
 * commands remain the way to configure a server.
 */
public final class MobStackerConfigScreen extends Screen {
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int ERROR_TEXT = 0xFF5555;

    private final Screen parent;
    private final List<Category> categories = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int categoryIndex;
    private boolean editable;

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
        editable = this.minecraft != null && this.minecraft.hasSingleplayerServer();

        if (!categories.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> switchCategory(-1))
                    .bounds(this.width / 2 - 170, 24, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> switchCategory(1))
                    .bounds(this.width / 2 + 150, 24, 20, 20).build());
        }

        if (editable && !categories.isEmpty()) {
            int y = 52;
            for (ConfigOption option : MobStackerSettings.byCategory(categories.get(categoryIndex))) {
                addOptionRow(option, y);
                y += 24;
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void switchCategory(int delta) {
        if (categories.isEmpty()) {
            return;
        }
        categoryIndex = Math.floorMod(categoryIndex + delta, categories.size());
        rebuildWidgets();
    }

    private void addOptionRow(ConfigOption option, int y) {
        int widgetX = this.width / 2 + 30;
        int widgetW = 140;
        int widgetH = 20;

        switch (option.type()) {
            case BOOL -> {
                boolean[] state = { Boolean.parseBoolean(option.currentValue()) };
                Button button = Button.builder(boolLabel(state[0]), b -> {
                    state[0] = !state[0];
                    b.setMessage(boolLabel(state[0]));
                    applyOption(option, String.valueOf(state[0]));
                }).bounds(widgetX, y, widgetW, widgetH).build();
                button.active = editable;
                addRenderableWidget(button);
            }
            case ENUM -> {
                List<String> values = option.enumValues();
                int[] index = { indexOfIgnoreCase(values, option.currentValue()) };
                if (index[0] < 0) {
                    index[0] = 0;
                }
                Button button = Button.builder(enumLabel(values.get(index[0])), b -> {
                    index[0] = (index[0] + 1) % values.size();
                    String value = values.get(index[0]);
                    b.setMessage(enumLabel(value));
                    applyOption(option, value);
                }).bounds(widgetX, y, widgetW, widgetH).build();
                button.active = editable;
                addRenderableWidget(button);
            }
            default -> {
                EditBox box = new EditBox(this.font, widgetX, y, widgetW, widgetH, Component.literal(option.id()));
                box.setValue(option.currentValue());
                box.setMaxLength(64);
                box.setEditable(editable);
                box.setResponder(text -> {
                    if (isValid(option, text)) {
                        box.setTextColor(NORMAL_TEXT);
                        applyOption(option, text);
                    } else {
                        box.setTextColor(ERROR_TEXT);
                    }
                });
                addRenderableWidget(box);
            }
        }
        rows.add(new Row(option, y));
    }

    private void applyOption(ConfigOption option, String raw) {
        if (!editable || this.minecraft == null) {
            return;
        }
        MinecraftServer server = this.minecraft.getSingleplayerServer();
        if (server != null) {
            // Mutate the config on the server thread; the widget already reflects the new value.
            server.execute(() -> option.apply(raw));
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (!categories.isEmpty()) {
            Category category = categories.get(categoryIndex);
            Component header = Component.literal(category.display() + "  (" + (categoryIndex + 1) + "/" + categories.size() + ")")
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawCenteredString(this.font, header, this.width / 2, 30, 0xFFFFFF);
        }

        if (editable) {
            for (Row row : rows) {
                guiGraphics.drawString(this.font, row.option.id(), this.width / 2 - 170, row.y + 6, NORMAL_TEXT);
            }
            Row hovered = rowAt(mouseX, mouseY);
            if (hovered != null) {
                guiGraphics.renderTooltip(this.font, Component.literal(hovered.option.description()), mouseX, mouseY);
            }
        } else {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Editing here works in singleplayer / on the LAN host.").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2 - 16, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("On a server, configure with /mobstacker commands for now.").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height / 2, 0xFFFFFF);
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

        private Row(ConfigOption option, int y) {
            this.option = option;
            this.y = y;
        }
    }
}
