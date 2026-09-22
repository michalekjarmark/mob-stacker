package com.frikinjay.mobstacker.fabric.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws a region's bounds in the world, so "where does this region actually reach" stops being a
 * question you answer by walking to a corner and reading coordinates.
 *
 * <p>Entirely client-side and entirely cosmetic. Which regions are showing, and how they are drawn,
 * is the player's own view of the world, not part of the server's config — so it is kept in a small
 * file of its own next to the game directory and never sent anywhere. A player without the mod
 * installed client-side simply sees nothing, exactly as before.
 *
 * <p>The colour comes from the region (see {@code StackRegion.effectiveColor}), so it is shared by
 * everyone looking at the same region; only the decision to look is per player.
 *
 * <p>Boxes are depth-tested, so terrain hides them the way it hides everything else. Drawing them
 * through walls would need a render type with the depth test off, and vanilla keeps the shard that
 * does that package-private — so that variant is a follow-up rather than a switch that quietly does
 * nothing. One box hiding <em>another</em> is not the same question and is fixed here: see the
 * order the two passes run in.
 */
public final class MobStackerRegionOverlay {

    /** How a region's box is drawn. Three of them because which one reads best is a matter of taste. */
    public enum Style {
        /** Just the twelve edges. The lightest thing that still says where the region is. */
        WIREFRAME,
        /** Translucent faces, so the volume is obvious from outside at a glance. */
        FILLED,
        /** Faces and edges together — clearest, and the busiest. */
        BOTH
    }

    private static final Logger logger = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Deliberately faint: the point is to answer a question, not to repaint the world. */
    private static final float FACE_ALPHA = 0.20F;
    private static final float EDGE_ALPHA = 0.85F;

    /** What gets written to disk, as one object so the file stays readable and easy to hand-edit. */
    private static final class State {
        Set<String> shown = new LinkedHashSet<>();
        boolean showAll = false;
        Style style = Style.BOTH;
    }

    private static State state = new State();
    private static Path file;

    private MobStackerRegionOverlay() {
    }

    public static void register() {
        file = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("mobstacker-overlay.json");
        load();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(MobStackerRegionOverlay::render);
    }

    // ------------------------------------------------------------------ what is showing

    public static boolean isShowingAll() {
        return state.showAll;
    }

    public static boolean isShown(String region) {
        return state.showAll || state.shown.contains(region);
    }

    /** @return true if the region is showing afterwards. */
    public static boolean toggle(String region) {
        // Turning one region on while "show all" is active is the player narrowing down to that one,
        // so the blanket switch gives way rather than fighting the per-region list.
        if (state.showAll) {
            state.showAll = false;
            state.shown.clear();
            state.shown.add(region);
        } else if (!state.shown.remove(region)) {
            state.shown.add(region);
        }
        save();
        return isShown(region);
    }

    /** @return true if everything is showing afterwards. */
    public static boolean toggleAll() {
        state.showAll = !state.showAll;
        if (!state.showAll) {
            state.shown.clear();
        }
        save();
        return state.showAll;
    }

    public static void hideEverything() {
        state.showAll = false;
        state.shown.clear();
        save();
    }

    public static Style style() {
        return state.style;
    }

    public static Style cycleStyle() {
        Style[] all = Style.values();
        state.style = all[(state.style.ordinal() + 1) % all.length];
        save();
        return state.style;
    }

    /** True when at least one box would be drawn, so the render hook can leave early. */
    public static boolean anythingShowing() {
        return state.showAll || !state.shown.isEmpty();
    }

    // ------------------------------------------------------------------ drawing

    private static void render(WorldRenderContext context) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        List<MobStackerClientRegions.View> regions = anythingShowing()
                ? MobStackerClientRegions.inCurrentDimension()
                : List.of();
        // The box being picked right now is drawn whatever the player's overlay settings say: they
        // asked for it by starting to pick, and it disappears again the moment they stop.
        AABB preview = MobStackerRegionPicker.previewBox();
        if (regions.isEmpty() && preview == null) {
            return;
        }

        Vec3 camera = context.camera().getPosition();
        PoseStack pose = context.matrixStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Style style = state.style;

        pose.pushPose();
        // The world is drawn relative to the camera, so every box moves with it.
        pose.translate(-camera.x, -camera.y, -camera.z);

        // Edges first, faces second. The order matters: vanilla's filled-box type writes depth,
        // so a face drawn before an edge hides it outright - the far side of a box disappeared into
        // its own near face, and a small region inside a big one vanished behind the big one's
        // colour. Lines write no depth of their own, so drawing them first costs nothing and the
        // 20%-alpha faces then tint them instead of swallowing them. (Drawing edges through *walls*
        // is a different question and still a follow-up: that needs a render type with the depth
        // test off, which vanilla keeps package-private.)
        if (style == Style.WIREFRAME || style == Style.BOTH) {
            VertexConsumer edges = buffers.getBuffer(RenderType.lines());
            for (MobStackerClientRegions.View view : regions) {
                if (!isShown(view.name())) {
                    continue;
                }
                float[] rgb = rgbOf(view);
                LevelRenderer.renderLineBox(pose, edges, boxOf(view), rgb[0], rgb[1], rgb[2], EDGE_ALPHA);
            }
            buffers.endBatch(RenderType.lines());
        }

        if (style == Style.FILLED || style == Style.BOTH) {
            VertexConsumer faces = buffers.getBuffer(RenderType.debugFilledBox());
            for (MobStackerClientRegions.View view : regions) {
                if (!isShown(view.name())) {
                    continue;
                }
                AABB box = boxOf(view);
                float[] rgb = rgbOf(view);
                LevelRenderer.addChainedFilledBoxVertices(pose, faces,
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                        rgb[0], rgb[1], rgb[2], FACE_ALPHA);
            }
            buffers.endBatch(RenderType.debugFilledBox());
        }

        if (preview != null) {
            // Always both faces and edges, in white: it is a transient answer to "is this the area
            // I mean", so being unmistakable matters more than matching the chosen style.
            VertexConsumer edges = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(pose, edges, preview, 1.0F, 1.0F, 1.0F, EDGE_ALPHA);
            buffers.endBatch(RenderType.lines());

            VertexConsumer faces = buffers.getBuffer(RenderType.debugFilledBox());
            LevelRenderer.addChainedFilledBoxVertices(pose, faces,
                    preview.minX, preview.minY, preview.minZ,
                    preview.maxX, preview.maxY, preview.maxZ,
                    1.0F, 1.0F, 1.0F, FACE_ALPHA);
            buffers.endBatch(RenderType.debugFilledBox());
        }

        pose.popPose();
    }

    /**
     * A region's corners are block coordinates and both ends are inside it, so the box that actually
     * encloses it runs to the far side of the maximum block — hence the +1 on each axis.
     */
    private static AABB boxOf(MobStackerClientRegions.View view) {
        return new AABB(view.minX(), view.minY(), view.minZ(),
                view.maxX() + 1.0, view.maxY() + 1.0, view.maxZ() + 1.0);
    }

    private static float[] rgbOf(MobStackerClientRegions.View view) {
        Integer packed = view.color().format().getColor();
        int value = packed == null ? 0xFFFFFF : packed;
        return new float[]{
                ((value >> 16) & 0xFF) / 255.0F,
                ((value >> 8) & 0xFF) / 255.0F,
                (value & 0xFF) / 255.0F
        };
    }

    // ------------------------------------------------------------------ persistence

    private static void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            State loaded = GSON.fromJson(reader, State.class);
            if (loaded != null) {
                state = loaded;
                if (state.shown == null) {
                    state.shown = new LinkedHashSet<>();
                }
                if (state.style == null) {
                    state.style = Style.BOTH;
                }
            }
        } catch (Exception e) {
            // A view preference is never worth failing a client launch over.
            logger.warn("MobStacker: could not read the region overlay settings, starting from defaults", e);
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (Exception e) {
            logger.warn("MobStacker: could not save the region overlay settings", e);
        }
    }
}
