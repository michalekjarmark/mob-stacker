package com.frikinjay.mobstacker.fabric.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * What the region overlay finds when it is asked to draw, written to the log - opt-in, with the Java
 * argument {@code -Dmobstacker.overlayDebug=true}, and silent otherwise.
 *
 * <p>For the one kind of report that cannot be reproduced here: a modpack in which the boxes do not
 * show. Whether the hooks are called at all, which buffer is being drawn into and what state the other
 * mods left behind is exactly what the rest of the pack decides, so it is asked of the game where the
 * problem is rather than guessed. At most one line per hook every five seconds, with the number of
 * calls since the last one, so a missing hook shows up as a count that does not move.
 */
final class MobStackerOverlayDebug {

    static final boolean ENABLED = Boolean.getBoolean("mobstacker.overlayDebug");

    private static final Logger logger = LogUtils.getLogger();
    private static final long INTERVAL_NS = 5_000_000_000L;
    // hook -> {nanoTime of the last line, calls since}
    private static final Map<String, long[]> seen = new HashMap<>();

    private MobStackerOverlayDebug() {
    }

    static void report(String hook, String detail) {
        if (!ENABLED) {
            return;
        }
        long[] entry = seen.computeIfAbsent(hook, key -> new long[]{0L, 0L});
        entry[1]++;
        long now = System.nanoTime();
        if (now - entry[0] < INTERVAL_NS) {
            return;
        }
        try {
            logger.info("[MobStacker overlay debug] {} (x{} since the last line): {} | {}",
                    hook, entry[1], detail, glState());
        } catch (Throwable t) {
            logger.info("[MobStacker overlay debug] {}: {} | could not read the GL state: {}", hook, detail, t.toString());
        }
        entry[0] = now;
        entry[1] = 0;
    }

    private static String glState() {
        Minecraft client = Minecraft.getInstance();
        LevelRenderer level = client.levelRenderer;
        StringBuilder out = new StringBuilder();
        out.append("graphics=").append(client.options.graphicsMode().get())
                .append(" fabulous=").append(Minecraft.useShaderTransparency())
                .append(" clouds=").append(client.options.getCloudsType())
                // Whose buffer source the game hands out right now: Iris swaps in its own for the
                // whole of world rendering, and the overlay drawing through it is what hid the boxes
                // in round 5-7's modpack. The overlay has its own buffer since; this says who else is there.
                .append(" bufferSource=").append(client.renderBuffers().bufferSource().getClass().getName())
                .append(" drawFbo=").append(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING))
                .append(" [main=").append(id(client.getMainRenderTarget()))
                .append(" translucent=").append(id(level.getTranslucentTarget()))
                .append(" itemEntity=").append(id(level.getItemEntityTarget()))
                .append(" particles=").append(id(level.getParticlesTarget()))
                .append(" weather=").append(id(level.getWeatherTarget()))
                .append(" clouds=").append(id(level.getCloudsTarget()))
                .append("] depthTest=").append(GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
                .append(" depthFunc=0x").append(Integer.toHexString(GL11.glGetInteger(GL11.GL_DEPTH_FUNC)))
                .append(" depthMask=").append(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))
                .append(" blend=").append(GL11.glIsEnabled(GL11.GL_BLEND))
                .append(" cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            out.append(" colorMask=").append(mask.get(0) != 0).append(',').append(mask.get(1) != 0)
                    .append(',').append(mask.get(2) != 0).append(',').append(mask.get(3) != 0);
        }
        return out.toString();
    }

    private static String id(RenderTarget target) {
        return target == null ? "-" : String.valueOf(target.frameBufferId);
    }
}
