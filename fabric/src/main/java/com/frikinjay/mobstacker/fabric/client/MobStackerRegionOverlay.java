package com.frikinjay.mobstacker.fabric.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * <p>Boxes are depth-tested by default, so terrain hides them the way it hides everything else.
 * <b>Through walls</b> is a switch of its own: it draws the same boxes with render types whose depth
 * test is off ({@link MobStackerRenderTypes}), so a region can be seen from anywhere in it or around
 * it. One box hiding <em>another</em> is a different question, answered either way: faces write no
 * depth, so nothing this overlay draws can hide anything else it draws.
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
        /**
         * One entry per world or server, keyed by {@link #worldKey()}. Regions belong to the world
         * they were drawn in, and so does the decision to look at them: "show all" switched on in a
         * test world used to follow the player onto the server and light up boxes there they had
         * never asked to see. A file written before this was split carries {@code shown} and
         * {@code showAll} at the top level; Gson ignores them and they are dropped the next time
         * this is saved, which costs one switch nobody can mistake for a bug.
         */
        Map<String, WorldView> worlds = new LinkedHashMap<>();
        /** Global on purpose: how a box is drawn is a taste, not a fact about a world. */
        Style style = Style.BOTH;
        /**
         * Whether terrain hides a box. Global for the same reason as {@link #style}. Off by default:
         * a box seen through a mountain is exactly what somebody laying out regions wants, and
         * exactly what somebody who switched boxes on to glance at one farm does not.
         */
        boolean throughWalls = false;
    }

    /** What one world's or server's boxes look like to this player. */
    private static final class WorldView {
        Set<String> shown = new LinkedHashSet<>();
        boolean showAll = false;
    }

    /** Handed back for a world nothing has been chosen in yet. Read from, never written to. */
    private static final WorldView NOTHING_SHOWN = new WorldView();

    private static State state = new State();
    private static Path file;
    // worldKey() is asked every frame; the folder name only changes when the world does. Weak, so
    // a world the player has left is not kept alive by the one thing that remembers its name.
    private static WeakReference<IntegratedServer> keyedServer = new WeakReference<>(null);
    private static String keyedFolder;

    private MobStackerRegionOverlay() {
    }

    public static void register() {
        file = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("mobstacker-overlay.json");
        load();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(MobStackerRegionOverlay::render);
    }

    // ------------------------------------------------------------------ what is showing

    public static boolean isShowingAll() {
        return here().showAll;
    }

    public static boolean isShown(String region) {
        WorldView view = here();
        return view.showAll || view.shown.contains(region);
    }

    /** @return true if the region is showing afterwards. */
    public static boolean toggle(String region) {
        WorldView view = mine();
        // Turning one region on while "show all" is active is the player narrowing down to that one,
        // so the blanket switch gives way rather than fighting the per-region list.
        if (view.showAll) {
            view.showAll = false;
            view.shown.clear();
            view.shown.add(region);
        } else if (!view.shown.remove(region)) {
            view.shown.add(region);
        }
        save();
        return isShown(region);
    }

    /** @return true if everything is showing afterwards. */
    public static boolean toggleAll() {
        WorldView view = mine();
        view.showAll = !view.showAll;
        if (!view.showAll) {
            view.shown.clear();
        }
        save();
        return view.showAll;
    }

    public static void hideEverything() {
        WorldView view = mine();
        view.showAll = false;
        view.shown.clear();
        save();
    }

    /** This world's view, for reading. Never creates an entry, so looking costs nothing. */
    private static WorldView here() {
        WorldView view = state.worlds.get(worldKey());
        return view == null ? NOTHING_SHOWN : view;
    }

    /** This world's view, for changing: the entry is created the first time something is shown. */
    private static WorldView mine() {
        return state.worlds.computeIfAbsent(worldKey(), key -> new WorldView());
    }

    /**
     * What this player is looking at right now: the singleplayer world's save folder, or the address
     * of the server. A region name only means anything inside one of those, so that is the scope
     * the choice of what to show is remembered at.
     */
    private static String worldKey() {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer local = client.getSingleplayerServer();
        if (local != null) {
            return "world:" + worldFolder(local);
        }
        ServerData server = client.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isEmpty()) {
            return "server:" + server.ip.toLowerCase(Locale.ROOT);
        }
        // Connected to something that did not say what it was. One shared key is still better than
        // none: the switches keep working, they just cannot tell that server from another.
        return "server:unknown";
    }

    /**
     * The folder the world is saved in, which is what actually tells two worlds apart. The name on
     * the world list does not: two worlds both called "New World" are saved as {@code New World} and
     * {@code New World (1)}, and keyed by the name they shared one set of switches.
     */
    private static String worldFolder(IntegratedServer server) {
        if (server != keyedServer.get()) {
            Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            keyedFolder = folder != null ? folder.toString() : server.getWorldData().getLevelName();
            keyedServer = new WeakReference<>(server);
        }
        return keyedFolder;
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

    public static boolean throughWalls() {
        return state.throughWalls;
    }

    /** @return true if boxes are drawn through walls afterwards. */
    public static boolean toggleThroughWalls() {
        state.throughWalls = !state.throughWalls;
        save();
        return state.throughWalls;
    }

    /** True when at least one box would be drawn, so the render hook can leave early. */
    public static boolean anythingShowing() {
        WorldView view = here();
        return view.showAll || !view.shown.isEmpty();
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
        // Through walls changes only which types draw the boxes, never what is drawn: the same
        // faces and edges, with the depth test off.
        RenderType faceType = state.throughWalls
                ? MobStackerRenderTypes.REGION_FACES_XRAY : MobStackerRenderTypes.REGION_FACES;
        RenderType edgeType = state.throughWalls
                ? MobStackerRenderTypes.REGION_EDGES_XRAY : RenderType.lines();

        pose.pushPose();
        // The world is drawn relative to the camera, so every box moves with it.
        pose.translate(-camera.x, -camera.y, -camera.z);

        // Faces are drawn with a type of our own that writes no depth (see
        // MobStackerRenderTypes.REGION_FACES). Vanilla's filled box does write it, and that is what
        // swallowed every edge behind a face - the far side of a box, and a whole region standing
        // inside another. Drawing the edges first was tried in round 2 and was not enough: with
        // "Fabulous" graphics the edges go into a buffer of their own and the faces still covered
        // them when the frame was put together. Now faces go first and edges on top, so an edge is
        // never tinted over and nothing here can hide anything else here.
        if (style == Style.FILLED || style == Style.BOTH) {
            VertexConsumer faces = buffers.getBuffer(faceType);
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
            buffers.endBatch(faceType);
        }

        if (style == Style.WIREFRAME || style == Style.BOTH) {
            VertexConsumer edges = buffers.getBuffer(edgeType);
            for (MobStackerClientRegions.View view : regions) {
                if (!isShown(view.name())) {
                    continue;
                }
                float[] rgb = rgbOf(view);
                LevelRenderer.renderLineBox(pose, edges, boxOf(view), rgb[0], rgb[1], rgb[2], EDGE_ALPHA);
            }
            buffers.endBatch(edgeType);
        }

        if (preview != null) {
            // Always both faces and edges, in white: it is a transient answer to "is this the area
            // I mean", so being unmistakable matters more than matching the chosen style. It does
            // follow "through walls", which is at its most useful exactly while a corner is being
            // looked for on the far side of a hill.
            VertexConsumer faces = buffers.getBuffer(faceType);
            LevelRenderer.addChainedFilledBoxVertices(pose, faces,
                    preview.minX, preview.minY, preview.minZ,
                    preview.maxX, preview.maxY, preview.maxZ,
                    1.0F, 1.0F, 1.0F, FACE_ALPHA);
            buffers.endBatch(faceType);

            VertexConsumer edges = buffers.getBuffer(edgeType);
            LevelRenderer.renderLineBox(pose, edges, preview, 1.0F, 1.0F, 1.0F, EDGE_ALPHA);
            buffers.endBatch(edgeType);
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
                if (state.worlds == null) {
                    state.worlds = new LinkedHashMap<>();
                }
                state.worlds.values().removeIf(view -> view == null);
                for (WorldView view : state.worlds.values()) {
                    if (view.shown == null) {
                        view.shown = new LinkedHashSet<>();
                    }
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
