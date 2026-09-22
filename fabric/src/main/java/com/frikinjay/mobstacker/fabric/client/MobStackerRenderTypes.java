package com.frikinjay.mobstacker.fabric.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

/**
 * The render types the region overlay needs and vanilla does not have.
 *
 * <p>Extends {@link RenderStateShard} only to reach its shards, which are {@code protected}; it is
 * never instantiated. {@code RenderType.create} is not public, so a type is put together here from
 * the public constructor and the shards themselves, set up and cleared in the same order a vanilla
 * composite type uses.
 */
final class MobStackerRenderTypes extends RenderStateShard {

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
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP,
            List.of(POSITION_COLOR_SHADER, TRANSLUCENT_TRANSPARENCY, LEQUAL_DEPTH_TEST,
                    VIEW_OFFSET_Z_LAYERING, COLOR_WRITE));

    private MobStackerRenderTypes() {
        super("mobstacker_render_types", () -> { }, () -> { });
    }

    private static RenderType composite(String name, VertexFormat format, VertexFormat.Mode mode,
                                        List<RenderStateShard> shards) {
        return new RenderType(name, format, mode, RenderType.SMALL_BUFFER_SIZE, false, true,
                () -> shards.forEach(RenderStateShard::setupRenderState),
                () -> shards.forEach(RenderStateShard::clearRenderState)) {
        };
    }
}
