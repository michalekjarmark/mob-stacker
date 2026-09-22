package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.config.RegionEdit;
import com.frikinjay.mobstacker.config.StackRegion;
import com.frikinjay.mobstacker.fabric.network.MobStackerNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a region: its name, its kind, the dimension it lives in and its two corners.
 * <p>
 * Until now a region's area was fixed the moment {@code /mobstacker region add} ran, and the only
 * way to change it was to delete the region and add it again — which threw away every setting it
 * carried. Here the area is just another editable field, and the same screen creates a region from
 * scratch, so a whole region can be set up without leaving the game.
 * <p>
 * The screen only ever checks the shape of what you typed; whether the edit is allowed is decided by
 * the server, exactly as it is for every other value in the GUI.
 */
public final class MobStackerRegionEditScreen extends Screen {
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int ERROR_TEXT = 0xFF5555;
    private static final int LABEL_X_OFFSET = -150;

    // One ladder of rows, read by init() for the widgets and by render() for the text beside them.
    // They used to be two sets of loose numbers in two methods, and nothing in the code noticed when
    // the X/Y/Z header landed on top of the dimension button.
    private static final int ROW_HEIGHT = 20;
    private static final int NAME_Y = 30;
    private static final int TYPE_Y = 58;
    private static final int DIMENSION_Y = 82;
    /** Between the dimension button (ends at DIMENSION_Y + ROW_HEIGHT) and the first corner row. */
    private static final int AXIS_HEADER_Y = 106;
    private static final int CORNER1_Y = 118;
    private static final int CORNER2_Y = 142;
    private static final int PICK_Y = 166;
    private static final int NOTE_Y = 192;
    /** A label sits this far below the top of the row it names, so it lines up inside the widget. */
    private static final int LABEL_INSET = 6;

    private final MobStackerRegionScreen parent;
    /** The region being redrawn, or null when this screen is creating a new one. */
    private final String existingName;
    /** Held until {@link #init} can resolve it against the dimensions the client knows about. */
    private final String pendingDimension;
    private final boolean remote;
    private final boolean editable;

    private final List<String> dimensions = new ArrayList<>();
    private StackRegion.Type type;
    private int dimensionIndex;
    private String name;
    private final int[] corners = new int[6];

    private EditBox nameBox;
    private final EditBox[] cornerBoxes = new EditBox[6];
    private String message = "";
    private boolean messageIsError;
    /** Set after the first click on Delete, so the second one is the one that means it. */
    private boolean deleteArmed;

    private MobStackerRegionEditScreen(MobStackerRegionScreen parent, String existingName,
                                       StackRegion.Type type, String dimension, int[] corners,
                                       boolean remote, boolean editable) {
        super(Component.literal(existingName == null ? "New region" : "Region: " + existingName));
        this.parent = parent;
        this.existingName = existingName;
        this.name = existingName == null ? "" : existingName;
        this.type = type;
        this.remote = remote;
        this.editable = editable;
        System.arraycopy(corners, 0, this.corners, 0, 6);
        this.pendingDimension = dimension;
    }

    /** Redraws an existing region, keeping everything else about it. */
    public static MobStackerRegionEditScreen forRegion(MobStackerRegionScreen parent, String name,
                                                       StackRegion.Type type, String dimension,
                                                       int[] corners, boolean remote, boolean editable) {
        return new MobStackerRegionEditScreen(parent, name, type, dimension, corners, remote, editable);
    }

    /** Starts a new region around the player, which is nearly always where they want it. */
    public static MobStackerRegionEditScreen forNewRegion(MobStackerRegionScreen parent,
                                                          boolean remote, boolean editable) {
        Minecraft client = Minecraft.getInstance();
        BlockPos pos = client.player != null ? client.player.blockPosition() : BlockPos.ZERO;
        String dimension = client.level != null ? client.level.dimension().location().toString() : "";
        int[] corners = {pos.getX() - 8, pos.getY() - 4, pos.getZ() - 8,
                pos.getX() + 8, pos.getY() + 12, pos.getZ() + 8};
        return new MobStackerRegionEditScreen(parent, null, StackRegion.Type.ALLOW, dimension,
                corners, remote, editable);
    }

    @Override
    protected void init() {
        loadDimensions();

        // Editable for an existing region too: renaming keeps its area, settings, priority and
        // colour, which deleting and re-adding it never did.
        nameBox = new EditBox(this.font, this.width / 2 - 40, NAME_Y, 190, ROW_HEIGHT, Component.literal("name"));
        nameBox.setValue(name);
        nameBox.setMaxLength(32);
        nameBox.setEditable(editable);
        nameBox.setResponder(text -> {
            name = text.trim();
            nameBox.setTextColor(RegionEdit.nameProblem(name) == null ? NORMAL_TEXT : ERROR_TEXT);
        });
        addRenderableWidget(nameBox);

        Button typeButton = Button.builder(typeLabel(), b -> {
            type = type == StackRegion.Type.ALLOW ? StackRegion.Type.DENY : StackRegion.Type.ALLOW;
            b.setMessage(typeLabel());
        }).bounds(this.width / 2 - 40, TYPE_Y, 190, ROW_HEIGHT).build();
        typeButton.active = editable;
        addRenderableWidget(typeButton);

        Button dimensionButton = Button.builder(dimensionLabel(), b -> {
            dimensionIndex = Math.floorMod(dimensionIndex + 1, Math.max(1, dimensions.size()));
            b.setMessage(dimensionLabel());
        }).bounds(this.width / 2 - 40, DIMENSION_Y, 190, ROW_HEIGHT).build();
        dimensionButton.active = editable && dimensions.size() > 1;
        addRenderableWidget(dimensionButton);

        addCornerRow(0, CORNER1_Y);
        addCornerRow(3, CORNER2_Y);

        Button pick = Button.builder(Component.literal("Pick in world…"),
                        b -> MobStackerRegionPicker.begin(this))
                .bounds(this.width / 2 - 90, PICK_Y, 240, ROW_HEIGHT).build();
        pick.active = editable && this.minecraft != null && this.minecraft.player != null;
        addRenderableWidget(pick);

        // Two buttons on a new region, three on an existing one - centred either way, so the row
        // never looks like it is missing the one that is not there.
        int buttons = existingName == null ? 2 : 3;
        int left = this.width / 2 - (buttons * 104 - 4) / 2;

        Button save = Button.builder(Component.literal("Save"), b -> save())
                .bounds(left, this.height - 28, 100, 20).build();
        save.active = editable;
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + 104, this.height - 28, 100, 20).build());

        if (existingName != null) {
            Button delete = Button.builder(deleteLabel(), b -> {
                if (deleteArmed) {
                    delete();
                } else {
                    // One click arms it, the next one means it: a region carries settings that took
                    // real effort to set, and there is no undo for losing them.
                    deleteArmed = true;
                    b.setMessage(deleteLabel());
                }
            }).bounds(left + 208, this.height - 28, 100, 20).build();
            delete.active = editable;
            addRenderableWidget(delete);
        }
    }

    /** Three coordinate boxes plus the button that fills them from where the player is standing. */
    private void addCornerRow(int offset, int y) {
        for (int i = 0; i < 3; i++) {
            int index = offset + i;
            EditBox box = new EditBox(this.font, this.width / 2 - 90 + i * 50, y, 46, ROW_HEIGHT,
                    Component.literal("coordinate"));
            box.setValue(String.valueOf(corners[index]));
            box.setMaxLength(9);
            box.setEditable(editable);
            box.setResponder(text -> {
                Integer parsed = parse(text);
                box.setTextColor(parsed == null ? ERROR_TEXT : NORMAL_TEXT);
                if (parsed != null) {
                    corners[index] = parsed;
                }
            });
            cornerBoxes[index] = box;
            addRenderableWidget(box);
        }

        Button here = Button.builder(Component.literal("Here"), b -> fillFromPlayer(offset))
                .bounds(this.width / 2 + 62, y, 88, ROW_HEIGHT).build();
        here.active = editable && this.minecraft != null && this.minecraft.player != null;
        addRenderableWidget(here);
    }

    /**
     * Takes the two corners the player clicked in the world and drops them into the coordinate
     * boxes as they were clicked: the first block is corner 1, the second is corner 2. Sorting them
     * into a minimum and a maximum described the same box, but with coordinates matching neither
     * block that was clicked - which reads as the editor having got them wrong.
     *
     * <p>Nothing is saved: this fills the same fields somebody could have typed, and Save still has
     * to be pressed. That is deliberate — the picker is a nicer way to answer the question, not a
     * second way to change a region.
     */
    void applyPickedCorners(BlockPos a, BlockPos b) {
        corners[0] = a.getX();
        corners[1] = a.getY();
        corners[2] = a.getZ();
        corners[3] = b.getX();
        corners[4] = b.getY();
        corners[5] = b.getZ();
        // The dimension the corners were clicked in is the only one they mean anything in.
        if (this.minecraft != null && this.minecraft.level != null) {
            String here = this.minecraft.level.dimension().location().toString();
            int index = dimensions.indexOf(here);
            if (index >= 0) {
                dimensionIndex = index;
            }
        }
        message = "Corners picked — press Save to keep them";
        messageIsError = false;
        for (int i = 0; i < 6; i++) {
            EditBox box = cornerBoxes[i];
            if (box != null) {
                box.setValue(String.valueOf(corners[i]));
            }
        }
    }

    private void fillFromPlayer(int offset) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        BlockPos pos = this.minecraft.player.blockPosition();
        corners[offset] = pos.getX();
        corners[offset + 1] = pos.getY();
        corners[offset + 2] = pos.getZ();
        for (int i = 0; i < 3; i++) {
            EditBox box = cornerBoxes[offset + i];
            if (box != null) {
                box.setValue(String.valueOf(corners[offset + i]));
            }
        }
    }

    /**
     * The dimensions this client knows the server has, so the choice is a button rather than a name
     * that has to be typed exactly right.
     */
    private void loadDimensions() {
        dimensions.clear();
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            for (ResourceKey<Level> key : this.minecraft.getConnection().levels()) {
                dimensions.add(key.location().toString());
            }
        }
        dimensions.sort(String::compareTo);
        // A region may name a dimension this client has never been told about (another mod's, or one
        // removed since). Keep it in the list rather than silently moving the region somewhere else.
        if (pendingDimension != null && !pendingDimension.isBlank() && !dimensions.contains(pendingDimension)) {
            dimensions.add(0, pendingDimension);
        }
        if (dimensions.isEmpty()) {
            dimensions.add("minecraft:overworld");
        }
        int index = dimensions.indexOf(pendingDimension);
        this.dimensionIndex = index >= 0 ? index : 0;
    }

    private void save() {
        if (!editable) {
            return;
        }
        for (int i = 0; i < cornerBoxes.length; i++) {
            if (cornerBoxes[i] != null && parse(cornerBoxes[i].getValue()) == null) {
                fail("Every coordinate must be a whole number.");
                return;
            }
        }
        String dimension = dimensions.get(dimensionIndex);
        String problem = RegionEdit.problem(name, type, dimension,
                corners[0], corners[1], corners[2], corners[3], corners[4], corners[5]);
        if (problem != null) {
            fail(problem);
            return;
        }
        RegionEdit.Definition definition = new RegionEdit.Definition(type, dimension,
                corners[0], corners[1], corners[2], corners[3], corners[4], corners[5]);
        String target = name.trim();
        // A rename has to land before the area does, or applying the definition under the new name
        // would create a second region beside the one being edited.
        boolean renaming = existingName != null && !existingName.equals(target);
        // "New region…" means new. Redrawing an existing region is a different screen and a
        // different intention, and the server is told which one this is rather than guessing.
        boolean creating = existingName == null;

        if (remote) {
            // The server decides; its answer comes back on the status line of the region screen.
            if (renaming) {
                MobStackerClientNetworking.sendEdit(
                        MobStackerNetworking.REGION_PREFIX + existingName + ":" + MobStackerNetworking.REGION_RENAME,
                        target);
            }
            MobStackerClientNetworking.sendEdit(
                    MobStackerNetworking.REGION_PREFIX + target + ":"
                            + (creating ? MobStackerNetworking.REGION_CREATE : MobStackerNetworking.REGION_DEFINITION),
                    definition.encode());
            backTo(target);
        } else if (this.minecraft != null) {
            Minecraft client = this.minecraft;
            MinecraftServer server = client.getSingleplayerServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                RegionEdit.Result result = renaming ? RegionEdit.rename(existingName, target) : null;
                if (result == null || result.ok()) {
                    result = RegionEdit.apply(target, definition, creating);
                }
                RegionEdit.Result outcome = result;
                client.execute(() -> {
                    if (outcome.ok()) {
                        // Singleplayer has no server status line to carry this back, and the edit
                        // has something to say worth reading - creating an allow region while
                        // stacking is off turns stacking on, and nobody should find that out later.
                        parent.setStatus(outcome.message());
                        backTo(target);
                    } else {
                        fail(outcome.message());
                    }
                });
            });
        }
    }

    private void delete() {
        if (!editable || existingName == null) {
            return;
        }
        if (remote) {
            MobStackerClientNetworking.sendEdit(
                    MobStackerNetworking.REGION_PREFIX + existingName + ":" + MobStackerNetworking.REGION_DELETE, "");
            backTo(null);
        } else if (this.minecraft != null) {
            Minecraft client = this.minecraft;
            MinecraftServer server = client.getSingleplayerServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                RegionEdit.delete(existingName);
                client.execute(() -> backTo(null));
            });
        }
    }

    /** Returns to the region list, asking it to show {@code select} once it knows about it. */
    private void backTo(String select) {
        parent.selectRegion(select);
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void fail(String reason) {
        this.message = reason;
        this.messageIsError = true;
    }

    private static Integer parse(String text) {
        String value = text.trim();
        if (value.isEmpty() || value.equals("-")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Component typeLabel() {
        return type == StackRegion.Type.DENY
                ? Component.literal("DENY").withStyle(ChatFormatting.RED)
                : Component.literal("ALLOW").withStyle(ChatFormatting.GREEN);
    }

    private Component dimensionLabel() {
        String value = dimensions.isEmpty() ? "?" : dimensions.get(Math.min(dimensionIndex, dimensions.size() - 1));
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
    }

    private Component deleteLabel() {
        return deleteArmed
                ? Component.literal("Click again").withStyle(ChatFormatting.RED)
                : Component.literal("Delete").withStyle(ChatFormatting.RED);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int labelX = this.width / 2 + LABEL_X_OFFSET;
        drawLabel(guiGraphics, "name", labelX, NAME_Y + LABEL_INSET);
        drawLabel(guiGraphics, "type", labelX, TYPE_Y + LABEL_INSET);
        drawLabel(guiGraphics, "dimension", labelX, DIMENSION_Y + LABEL_INSET);
        drawLabel(guiGraphics, "corner 1", labelX, CORNER1_Y + LABEL_INSET);
        drawLabel(guiGraphics, "corner 2", labelX, CORNER2_Y + LABEL_INSET);
        // In the gap between the dimension button and the first corner row, not across either. The
        // same grey as the labels beside it: dark grey disappeared into the dirt background.
        guiGraphics.drawString(this.font,
                Component.literal("X            Y            Z").withStyle(ChatFormatting.GRAY),
                this.width / 2 - 86, AXIS_HEADER_Y, NORMAL_TEXT);

        // Below the pick button, not across it. A message always wins over the standing hint,
        // whether it is a refusal or the picker saying it has filled the boxes in - a confirmation
        // nobody can see is the same as no confirmation at all.
        Component note;
        if (!message.isEmpty()) {
            note = Component.literal(message)
                    .withStyle(messageIsError ? ChatFormatting.RED : ChatFormatting.GREEN);
        } else {
            note = Component.literal(existingName == null
                            ? "The corners are inclusive; the region covers both blocks and everything between them."
                            : "Redrawing or renaming a region keeps its settings, its priority and its colour.")
                    .withStyle(ChatFormatting.GRAY);
        }
        guiGraphics.drawCenteredString(this.font, note, this.width / 2, NOTE_Y, 0xFFFFFF);

        if (!editable) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("Read-only — operator permission is required to edit.").withStyle(ChatFormatting.GRAY),
                    this.width / 2, this.height - 46, 0xFFFFFF);
        }
    }

    private void drawLabel(GuiGraphics guiGraphics, String text, int x, int y) {
        guiGraphics.drawString(this.font, Component.literal(text).withStyle(ChatFormatting.GRAY), x, y, NORMAL_TEXT);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
