package com.frikinjay.mobstacker.client;

import com.frikinjay.mobstacker.MobStacker;
import com.google.gson.Gson;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
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
 * <p>Boxes are depth-tested by default, so terrain and mobs hide them the way they hide everything
 * else - but not water, glass or clouds, which a box is drawn over in every graphics mode (see
 * {@link #solidDepth}).
 * <b>Through walls</b> is a switch of its own: it draws the same boxes with render types whose depth
 * test is off ({@link MobStackerRenderTypes}), so a region can be seen from anywhere in it or around
 * it. One box hiding <em>another</em> is a different question, answered either way: faces write no
 * depth, so nothing this overlay draws can hide anything else it draws.
 *
 * <p>Through walls is the <b>server's</b> to allow ({@link MobStacker#XRAY_PROPERTY}), because it
 * shows the player a little of what is behind a wall. The switch is still the player's own and is
 * remembered as they left it; it just does nothing where the server has not allowed it.
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

    /**
     * The solid world's depth, which is what the boxes and their names are tested against: terrain
     * and mobs hide them, nothing see-through does. Kept just before the translucent terrain is drawn
     * ({@link #keepSolidDepth}) and put back just before the boxes are, in every graphics mode.
     *
     * <p>Under "Fabulous" the pass that puts the frame together wipes the main depth on its way, so
     * there is nothing else to test against. Under "Fast" and "Fancy" there is, and it was used until
     * round 9 - but water, stained glass and clouds write their depth into it there, so a box under
     * water could not be seen from above the surface, and a name with X-ray on was only ever drawn at
     * its faint see-through strength, while Fabulous drew both. Created the first time it is needed,
     * and sized to the window.
     */
    private static TextureTarget solidDepth;
    /** True from the moment {@link #solidDepth} is filled in a frame until that frame's boxes are drawn. */
    private static boolean solidDepthKept;
    /**
     * The frame's own depth, set aside while the boxes are drawn against {@link #solidDepth} and put
     * back after them, so whatever is drawn after the boxes (another mod's, say) is tested against what
     * the world really drew.
     */
    private static TextureTarget frameDepth;

    /**
     * The overlay's own vertex buffer, drawn from and emptied on the spot for every batch.
     *
     * <p>Not the game's shared buffer source ({@code renderBuffers().bufferSource()}), which is what
     * the boxes used until round 7 - and which is not the game's to give out while the world is
     * drawn when Iris is installed, shader pack or not: Iris hands out its own buffered source for
     * that time, and what goes into it is drawn when Iris flushes it, not when it is asked to. In the
     * round 5-7 modpack that put the boxes under water and clouds whatever this code did, and before
     * that made them depend on the cloud setting. A buffer of our own is drawn exactly when and how
     * this class says. Grows as needed.
     */
    private static final BufferBuilder BUILDER = new BufferBuilder(RenderType.SMALL_BUFFER_SIZE);

    /**
     * Where the region names are drawn from, for the same reason as {@link #BUILDER}: text goes through
     * a buffer source, and the game's own is Iris's while the world is being drawn. Emptied at the end
     * of every frame's labels.
     */
    private static final MultiBufferSource.BufferSource LABELS = MultiBufferSource.immediate(new BufferBuilder(256));
    /** Past this, a region's name is not drawn: it would be a speck, and there may be many. */
    private static final double LABEL_RANGE = 128.0;

    private MobStackerRegionOverlay() {
    }

    /**
     * Reads the player's overlay settings. Called once by the loader's client entrypoint; the drawing
     * itself is hooked into the world renderer by {@code LevelRendererMixin}, on every loader alike.
     */
    public static void init() {
        file = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("mobstacker-overlay.json");
        load();
        // Every box is drawn last of all, straight into the finished frame. Drawn any earlier, what
        // the world drew after it covered it: clouds with every setting, and with "Fabulous" graphics
        // water and mobs too, because that frame is put together from separate buffers by depth and
        // a box that writes none sat at the depth of whatever was behind it - in front of the box or
        // not (round 6, both with X-ray on and, once that was fixed, with it off). What they are
        // tested against is the solid world's depth, kept just before the translucent terrain, and
        // before the debug renderer as well, in case a mod has moved that point: the later copy
        // wins. (Until round 9 it was also kept after the translucent terrain, which under Fast and
        // Fancy has water in it.) The three points are Fabric's BEFORE_DEBUG_RENDER and LAST - the
        // same instructions its events are fired from - and the translucent terrain's own.
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

    /** Whether boxes are drawn through walls right now: switched on, and allowed here. */
    public static boolean throughWalls() {
        return state.throughWalls && throughWallsAllowed();
    }

    /**
     * Whether whoever runs this world lets boxes be drawn through walls. Always the server's answer,
     * never this client's: in singleplayer, and for a LAN host, the server is this same game, so it
     * is this game's own {@code -Dmobstacker.xray} flag; anywhere else it is what the server's last
     * snapshot said, and a server that has said nothing (older, or no snapshot yet) has not allowed it.
     */
    public static boolean throughWallsAllowed() {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return MobStacker.regionXrayAllowed();
        }
        return MobStackerClientNetworking.serverAllowsXray();
    }

    /** @return true if boxes are drawn through walls afterwards. Does nothing where it is not allowed. */
    public static boolean toggleThroughWalls() {
        if (!throughWallsAllowed()) {
            return false;
        }
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

    /**
     * Copies the main depth buffer aside while it holds the solid world and nothing see-through yet
     * (see {@link #solidDepth}). Called twice in a frame, before the debug renderer and just before
     * the translucent terrain; the later copy wins. Only when a box is about to be drawn this frame;
     * otherwise it costs nothing. With X-ray on the boxes ignore depth, but their names do not.
     */
    public static void keepSolidDepth(String hook) {
        MobStackerOverlayDebug.report(hook, "showing=" + anythingShowing() + " xray=" + throughWalls());
        if (Minecraft.getInstance().level == null
                || (!anythingShowing() && MobStackerRegionPicker.previewBox() == null)) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        solidDepth = sizedLike(main, solidDepth);
        solidDepth.copyDepthFrom(main);
        // copyDepthFrom leaves no framebuffer bound; the main one was, and the game carries on
        // drawing without binding it again.
        main.bindWrite(false);
        solidDepthKept = true;
    }

    /** {@code target}, created or resized to match {@code main} when it does not already. */
    private static TextureTarget sizedLike(RenderTarget main, TextureTarget target) {
        if (target == null) {
            return new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        }
        if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        return target;
    }

    /**
     * Draws every showing box, its name and the picker's preview. Called just before the world
     * renderer's debug pass, after everything else in the world (Fabric's {@code LAST}), with the
     * world's own pose stack and camera.
     */
    public static void render(PoseStack pose, Camera camera) {
        boolean depthKept = solidDepthKept;
        solidDepthKept = false;
        if (Minecraft.getInstance().level == null) {
            return;
        }
        boolean xray = throughWalls();
        List<MobStackerClientRegions.View> regions = anythingShowing()
                ? MobStackerClientRegions.inCurrentDimension()
                : List.of();
        // The box being picked right now is drawn whatever the player's overlay settings say: they
        // asked for it by starting to pick, and it disappears again the moment they stop.
        AABB preview = MobStackerRegionPicker.previewBox();
        MobStackerOverlayDebug.report("LAST", "regions=" + regions.size() + " preview=" + (preview != null)
                + " xray=" + xray + " depthKept=" + depthKept);
        if (regions.isEmpty() && preview == null) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (depthKept) {
            // Test against the solid world only: terrain and mobs hide a box, water and clouds do
            // not, the same in every graphics mode. The frame's own depth goes back once the boxes
            // are drawn; the hand is drawn after a depth clear of its own either way.
            frameDepth = sizedLike(main, frameDepth);
            frameDepth.copyDepthFrom(main);
            main.copyDepthFrom(solidDepth);
            main.bindWrite(false);
        }

        Vec3 eye = camera.getPosition();
        Style style = state.style;
        // Through walls changes only which types draw the boxes (and when), never what is drawn:
        // the same faces and edges, with the depth test off.
        RenderType faceType = xray
                ? MobStackerRenderTypes.REGION_FACES_XRAY : MobStackerRenderTypes.REGION_FACES;
        RenderType edgeType = xray
                ? MobStackerRenderTypes.REGION_EDGES_XRAY : MobStackerRenderTypes.REGION_EDGES;

        pose.pushPose();
        // The world is drawn relative to the camera, so every box moves with it.
        pose.translate(-eye.x, -eye.y, -eye.z);

        // Faces and edges are drawn with types of our own that write no depth (see
        // MobStackerRenderTypes). Vanilla's filled box does write it, and that is what swallowed
        // every edge behind a face - the far side of a box, and a whole region standing inside
        // another. Faces go first and edges on top, so an edge is never tinted over and nothing here
        // can hide anything else here.
        if (style == Style.FILLED || style == Style.BOTH) {
            BufferBuilder faces = begin(faceType);
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
            faceType.end(faces, RenderSystem.getVertexSorting());
        }

        if (style == Style.WIREFRAME || style == Style.BOTH) {
            BufferBuilder edges = begin(edgeType);
            for (MobStackerClientRegions.View view : regions) {
                if (!isShown(view.name())) {
                    continue;
                }
                float[] rgb = rgbOf(view);
                LevelRenderer.renderLineBox(pose, edges, boxOf(view), rgb[0], rgb[1], rgb[2], EDGE_ALPHA);
            }
            edgeType.end(edges, RenderSystem.getVertexSorting());
        }

        if (preview != null) {
            // Always both faces and edges, in white: it is a transient answer to "is this the area
            // I mean", so being unmistakable matters more than matching the chosen style. It does
            // follow "through walls", which is at its most useful exactly while a corner is being
            // looked for on the far side of a hill.
            BufferBuilder faces = begin(faceType);
            LevelRenderer.addChainedFilledBoxVertices(pose, faces,
                    preview.minX, preview.minY, preview.minZ,
                    preview.maxX, preview.maxY, preview.maxZ,
                    1.0F, 1.0F, 1.0F, FACE_ALPHA);
            faceType.end(faces, RenderSystem.getVertexSorting());

            BufferBuilder edges = begin(edgeType);
            LevelRenderer.renderLineBox(pose, edges, preview, 1.0F, 1.0F, 1.0F, EDGE_ALPHA);
            edgeType.end(edges, RenderSystem.getVertexSorting());
        }

        drawNames(pose, camera, regions, xray);

        pose.popPose();

        if (depthKept) {
            main.copyDepthFrom(frameDepth);
            main.bindWrite(false);
        }
    }

    /**
     * Writes each showing region's name on its box, the way a mob's name tag is written: facing the
     * camera, with a dark backing. With a few regions on screen, boxes alone did not say which was which.
     *
     * <p>The name sits on the point of the box nearest the camera, at eye height where the box reaches
     * it, so it is on the side being looked at however big the region is, and it grows with distance so
     * it can still be read. A region the camera is inside gets no name in the world - the line at the
     * top of the screen ({@link MobStackerRegionHud}) says where the player is. Hidden by terrain like
     * the boxes, unless the server allows them through walls and the player has that on.
     */
    private static void drawNames(PoseStack pose, Camera camera, List<MobStackerClientRegions.View> regions, boolean xray) {
        if (regions.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Vec3 eye = camera.getPosition();
        int backing = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        for (MobStackerClientRegions.View view : regions) {
            if (!isShown(view.name())) {
                continue;
            }
            AABB box = boxOf(view);
            if (box.contains(eye)) {
                continue;
            }
            double x = Mth.clamp(eye.x, box.minX, box.maxX);
            double y = Mth.clamp(eye.y, box.minY, box.maxY);
            double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
            double distance = Math.sqrt(eye.distanceToSqr(x, y, z));
            if (distance > LABEL_RANGE) {
                continue;
            }
            float[] rgb = rgbOf(view);
            int colour = 0xFF000000 | ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
            Component name = Component.literal(view.name()
                    + (view.type() == com.frikinjay.mobstacker.config.StackRegion.Type.DENY ? " (deny)" : ""));
            float scale = 0.025F * (float) Math.max(1.0, distance / 10.0);

            pose.pushPose();
            pose.translate(x, y + 0.5, z);
            pose.mulPose(camera.rotation());
            pose.scale(-scale, -scale, scale);
            Matrix4f matrix = pose.last().pose();
            float left = -font.width(name) / 2.0F;
            if (xray) {
                // As a name tag behind a wall: faint through it, full strength where it is in view.
                font.drawInBatch(name, left, 0.0F, 0x40FFFFFF & colour, false, matrix, LABELS,
                        Font.DisplayMode.SEE_THROUGH, backing, LightTexture.FULL_BRIGHT);
            }
            font.drawInBatch(name, left, 0.0F, colour, false, matrix, LABELS,
                    Font.DisplayMode.NORMAL, xray ? 0 : backing, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        LABELS.endBatch();
    }

    /**
     * Starts a batch of one type in {@link #BUILDER}; {@code type.end(...)} draws and empties it.
     * A batch left half-built by an exception in an earlier frame is thrown away first.
     */
    private static BufferBuilder begin(RenderType type) {
        if (BUILDER.building()) {
            BUILDER.discard();
        }
        BUILDER.begin(type.mode(), type.format());
        return BUILDER;
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
