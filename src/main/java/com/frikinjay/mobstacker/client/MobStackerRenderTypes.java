package com.frikinjay.mobstacker.client;

//? if <1.21.11 {

//? if >=1.21.5 {
/*import com.frikinjay.mobstacker.compat.Compat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
*///?} else {
import com.mojang.blaze3d.systems.RenderSystem;
//?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The render types the region overlay needs and vanilla does not have.
 *
 * <p>Extends {@link RenderStateShard} only to reach its shards, which are {@code protected}; it is
 * never instantiated. {@code RenderType.create} is not public, so a type is put together here from
 * the public constructor and the shards themselves, set up and cleared in the same order a vanilla
 * composite type uses (shader, transparency, depth test, cull, layering, output, write mask, line).
 */
final class MobStackerRenderTypes extends RenderStateShard {

    //? if >=1.21.5 {
    /*// From 1.21.5 (the version that brought the pipelines) a type is a render pipeline - the
    // shader, blending, depth test and depth write - plus a composite state for the rest, and the
    // depth test is the pipeline's own: there is no global one left to get wrong, so no shard to
    // switch it off. The same four types as below, with what they say below.

    /^* {@link #REGION_FACES} below 1.21.5: vanilla's debug filled box writing no depth. ^/
    static final RenderType REGION_FACES = RenderType.create("mobstacker_region_faces", RenderType.SMALL_BUFFER_SIZE,
            false, true, faces("region_faces", DepthTestFunction.LEQUAL_DEPTH_TEST), state(false));

    /^* {@link #REGION_EDGES} below 1.21.5: vanilla's lines writing no depth, into the main buffer. ^/
    static final RenderType REGION_EDGES = RenderType.create("mobstacker_region_edges", RenderType.SMALL_BUFFER_SIZE,
            false, false, edges("region_edges", DepthTestFunction.LEQUAL_DEPTH_TEST), state(true));

    /^* {@link #REGION_FACES} with the depth test off (through walls). ^/
    static final RenderType REGION_FACES_XRAY = RenderType.create("mobstacker_region_faces_xray", RenderType.SMALL_BUFFER_SIZE,
            false, true, faces("region_faces_xray", DepthTestFunction.NO_DEPTH_TEST), state(false));

    /^* {@link #REGION_EDGES} with the depth test off (through walls). ^/
    static final RenderType REGION_EDGES_XRAY = RenderType.create("mobstacker_region_edges_xray", RenderType.SMALL_BUFFER_SIZE,
            false, false, edges("region_edges_xray", DepthTestFunction.NO_DEPTH_TEST), state(true));

    /^* Vanilla's {@code DEBUG_FILLED_BOX} (translucent, culled triangle strips) without the depth write. ^/
    private static RenderPipeline faces(String name, DepthTestFunction depthTest) {
        return RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Compat.id("mobstacker", "pipeline/" + name))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
                .withDepthTestFunction(depthTest)
                .withDepthWrite(false)
                .build();
    }

    /^* Vanilla's {@code LINES} (translucent, not culled) without the depth write. ^/
    private static RenderPipeline edges(String name, DepthTestFunction depthTest) {
        return RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Compat.id("mobstacker", "pipeline/" + name))
                .withDepthTestFunction(depthTest)
                .withDepthWrite(false)
                .build();
    }

    /^* Vanilla's layering, into the main buffer, and for lines the game's line width. ^/
    private static RenderType.CompositeState state(boolean lines) {
        RenderType.CompositeState.CompositeStateBuilder builder = RenderType.CompositeState.builder()
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .setOutputState(MAIN_TARGET);
        if (lines) {
            builder.setLineState(new LineStateShard(OptionalDouble.empty()));
        }
        return builder.createCompositeState(false);
    }

    private MobStackerRenderTypes() {
        super("mobstacker_render_types", () -> { }, () -> { });
    }
    *///?} else {
    /**
     * The depth test switched off - actually switched off.
     *
     * <p>Vanilla's {@code NO_DEPTH_TEST} does <b>nothing</b> when it is set up: it takes the test to
     * be off already, which is the state vanilla's own types leave behind. The particle renderer does
     * not - it switches the test on and leaves it on - and Fabric's {@code AFTER_TRANSLUCENT}, where
     * the overlay draws, comes straight after the particles. So the first x-ray types were
     * depth-tested all along, and round 5 saw no box through any wall. This one switches the test off
     * itself, and leaves it off, the state every vanilla type with a depth test leaves when cleared.
     * With the test off nothing is written to the depth buffer either.
     */
    private static final RenderStateShard DEPTH_TEST_OFF = new RenderStateShard("mobstacker_depth_test_off",
            RenderSystem::disableDepthTest, () -> { }) {
    };

    /**
     * A region's translucent faces: vanilla's {@code debugFilledBox} in every respect but one - it
     * <b>does not write depth</b>.
     *
     * <p>Writing depth is what hid everything behind a face. With "Fabulous" graphics it is worse
     * than it sounds: edges are lines, lines are drawn into a buffer of their own, and when the
     * frame is put together the main buffer - faces included - counts as solid. A face that had
     * written its depth there therefore covered every edge behind it outright, however faint the
     * face was, and the far side of a box and any box inside another disappeared. From inside a box
     * its faces are culled, which is why everything came back the moment the player stepped in.
     * A face that writes no depth is drawn over what is behind it and stops nothing else being
     * drawn - terrain still hides it, because the depth test is still on.
     */
    static final RenderType REGION_FACES = composite("mobstacker_region_faces",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP, true,
            List.of(POSITION_COLOR_SHADER, TRANSLUCENT_TRANSPARENCY, LEQUAL_DEPTH_TEST,
                    VIEW_OFFSET_Z_LAYERING, COLOR_WRITE));

    /**
     * A region's edges: vanilla's {@code lines()} in every respect but two. It writes no depth, like
     * {@link #REGION_FACES}, so nothing the overlay draws can hide anything else it draws. And it
     * draws into the <b>main</b> buffer rather than the item-entity one.
     *
     * <p>The second is what matters. The boxes are drawn last of all, and under "Fabulous" graphics
     * the item-entity buffer has been laid into the frame by then - a line drawn there would never be
     * seen.
     */
    static final RenderType REGION_EDGES = composite("mobstacker_region_edges",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, false,
            List.of(RENDERTYPE_LINES_SHADER, TRANSLUCENT_TRANSPARENCY, LEQUAL_DEPTH_TEST, NO_CULL,
                    VIEW_OFFSET_Z_LAYERING, COLOR_WRITE,
                    new LineStateShard(OptionalDouble.empty())));

    /**
     * {@link #REGION_FACES} with the depth test off as well, so terrain no longer hides a region:
     * the "through walls" view. Nothing else about it differs, so switching it on changes what hides
     * a box and nothing about how the box looks.
     *
     * <p>Wrapping the vanilla type in {@code RenderSystem.disableDepthTest()} does not do this - a
     * render type sets its own depth test up when its batch is drawn, which overwrites whatever was
     * set before. It has to be a type whose own depth-test shard is off, which is why this exists,
     * and that shard has to be {@link #DEPTH_TEST_OFF} rather than vanilla's.
     *
     * <p>Drawn from {@code WorldRenderEvents.LAST}, after everything else in the world, so nothing
     * the world draws later can cover it again.
     */
    static final RenderType REGION_FACES_XRAY = composite("mobstacker_region_faces_xray",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP, true,
            List.of(POSITION_COLOR_SHADER, TRANSLUCENT_TRANSPARENCY, DEPTH_TEST_OFF,
                    VIEW_OFFSET_Z_LAYERING, COLOR_WRITE));

    /**
     * Vanilla's {@code lines()} with the depth test off, for the same view: the same shader, line
     * width and layering, so an edge looks exactly as it does without it.
     *
     * <p>It also writes no depth ({@code COLOR_WRITE}, where vanilla's writes both): with the test
     * off there is nothing depth would be good for.
     *
     * <p>Unlike vanilla's, it draws into the <b>main</b> buffer, not the item-entity one. It is drawn
     * from {@code WorldRenderEvents.LAST}, and under "Fabulous" graphics that comes after the
     * item-entity buffer has already been laid into the frame - a line drawn there would never be
     * seen. The first version drew there earlier instead, and with "Fabulous" an edge was covered by
     * any mob, water or cloud in front of whatever lay behind the box: that frame is put together by
     * depth, and an edge that writes none sits at the depth of whatever is behind it. (Clouds, drawn
     * after it, covered it with every setting.)
     */
    static final RenderType REGION_EDGES_XRAY = composite("mobstacker_region_edges_xray",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, false,
            List.of(RENDERTYPE_LINES_SHADER, TRANSLUCENT_TRANSPARENCY, DEPTH_TEST_OFF, NO_CULL,
                    VIEW_OFFSET_Z_LAYERING, COLOR_WRITE,
                    new LineStateShard(OptionalDouble.empty())));

    private MobStackerRenderTypes() {
        super("mobstacker_render_types", () -> { }, () -> { });
    }

    /**
     * @param sortOnUpload what vanilla passes for the type this one is modelled on: true for the
     *                     filled box, false for lines
     */
    private static RenderType composite(String name, VertexFormat format, VertexFormat.Mode mode,
                                        boolean sortOnUpload, List<RenderStateShard> shards) {
        return new RenderType(name, format, mode, RenderType.SMALL_BUFFER_SIZE, false, sortOnUpload,
                () -> shards.forEach(RenderStateShard::setupRenderState),
                () -> shards.forEach(RenderStateShard::clearRenderState)) {
        };
    }
    //?}
}
//?}
