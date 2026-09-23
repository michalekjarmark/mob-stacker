package com.frikinjay.mobstacker.fabric.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
}
