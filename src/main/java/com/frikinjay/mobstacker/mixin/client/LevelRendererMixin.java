package com.frikinjay.mobstacker.mixin.client;

//? if <26.1 {

import com.frikinjay.mobstacker.client.MobStackerRegionOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where in a frame the region overlay does its work: the same three points on every loader, so the
 * timing the boxes depend on - found the hard way over rounds 5 to 9 - is never a loader's own idea.
 *
 * <p>Two of them are exactly where Fabric fires {@code BEFORE_DEBUG_RENDER} and {@code LAST}, which is
 * what the overlay used until it ran on Forge too. The third has no event anywhere: just before the
 * translucent terrain is drawn. By then the solid terrain and every mob are in the depth buffer -
 * including the mobs Iris holds back and draws only when the game empties its buffers, which it does
 * just before this - and nothing see-through is yet. Under "Fast" and "Fancy" graphics water, stained
 * glass and clouds write depth into the same buffer the boxes are tested against, so a box under water
 * was hidden by the surface above it (round 9), where "Fabulous", which draws them into buffers of its
 * own, showed it. It is found by the profiler's {@code "translucent"} section, which both branches of
 * {@code renderLevel} (Fabulous and not) open right before the translucent terrain; Iris hooks the same
 * constant.
 *
 * <p>None of them is required. The boxes are a view, and a client that cannot draw them because some
 * other mod rewrote these lines is better off without them than unable to start. The keep before the
 * debug renderer is the fallback for the translucent one: the same picture, bar the mobs Iris has not
 * drawn yet.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V"))
    private void mobstacker$keepDepthBeforeDebug(CallbackInfo ci) {
        MobStackerRegionOverlay.keepSolidDepth("before debug renderer");
    }

    @Inject(method = "renderLevel", require = 0, at = @At(value = "CONSTANT", args = "stringValue=translucent"))
    private void mobstacker$keepDepthBeforeTranslucent(CallbackInfo ci) {
        MobStackerRegionOverlay.keepSolidDepth("before translucent");
    }

    @Inject(method = "renderLevel", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderDebug(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/Camera;)V"))
    //? if >=1.21 {
    /*private void mobstacker$drawRegions(net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline,
                                        Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                        Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        // renderLevel has no pose stack to hand out any more: the camera's rotation is on the model-view
        // matrix, which is still pushed here, and vanilla's debug renderer draws from a fresh stack.
        MobStackerRegionOverlay.render(new PoseStack(), camera);
    }
    *///?} else {
    private void mobstacker$drawRegions(PoseStack poseStack, float partialTick, long finishNanoTime,
                                        boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                        LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        MobStackerRegionOverlay.render(poseStack, camera);
    }
    //?}
}
//?}
