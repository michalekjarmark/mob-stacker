package com.frikinjay.mobstacker.mixin.client;

//? if >=26.3 {
/*import com.frikinjay.mobstacker.client.LateGizmos;
import com.frikinjay.mobstacker.client.MobStackerRegionOverlay;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * From 1.21.11 the region boxes are gizmos: they are handed to the game while it collects the frame's
 * gizmos, just before it closes that collection ({@code finalizeGizmoCollection}, once a frame: from
 * {@code submitFeatures} on 26.2 and 26.3, from a lambda of the main pass on 1.21.11 and 26.1). The same on every loader.
 *
 * <p>And the translucent gizmos are drawn at the end of the classic transparency, after the water,
 * the clouds and the weather (see {@link LateGizmos}).
 ^/
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGizmosMixin {
    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void mobstacker$regionGizmos(CallbackInfo ci) {
        MobStackerRegionOverlay.emitGizmos();
    }

    @Inject(method = "executeClassicTransparency", at = @At("RETURN"))
    private void mobstacker$gizmosAfterWeather(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame,
                                               RenderPass pass, CallbackInfo ci) {
        ((LateGizmos) frame).mobstacker$executeGizmos(pass);
    }
}
*///?} else if >=26.2 {
/*import com.frikinjay.mobstacker.client.LateGizmos;
import com.frikinjay.mobstacker.client.MobStackerRegionOverlay;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * From 1.21.11 the region boxes are gizmos: they are handed to the game while it collects the frame's
 * gizmos, just before it closes that collection ({@code finalizeGizmoCollection}, once a frame: from
 * {@code submitFeatures} on 26.2 and 26.3, from a lambda of the main pass on 1.21.11 and 26.1). The same on every loader.
 *
 * <p>And the gizmos are drawn in the always-on-top pass, which comes after the clouds, the weather and
 * the transparency's post chain, before that pass clears the depth for its own (see {@link LateGizmos}).
 * The game adds that pass only for always-on-top gizmos (X-ray boxes): it is added for these too, and
 * clears the depth only when it has the game's own to draw, as before.
 ^/
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGizmosMixin {
    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void mobstacker$regionGizmos(CallbackInfo ci) {
        MobStackerRegionOverlay.emitGizmos();
    }

    @Redirect(method = "addAlwaysOnTopPass", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;hasAnyAlwaysOnTop()Z"))
    private boolean mobstacker$passForGizmosToo(FeatureRenderDispatcher.PreparedFrame frame) {
        return frame.hasAnyAlwaysOnTop() || ((LateGizmos) frame).mobstacker$hasGizmos();
    }

    @Redirect(method = "lambda$addAlwaysOnTopPass$0", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void mobstacker$gizmosAfterWeather(CommandEncoder encoder, GpuTexture depth, double clearTo,
                                               GpuBufferSlice fog, ResourceHandle<?> target, FeatureRenderDispatcher.PreparedFrame frame) {
        ((LateGizmos) frame).mobstacker$executeGizmos();
        if (frame.hasAnyAlwaysOnTop()) {
            encoder.clearDepthTexture(depth, clearTo);
        }
    }
}
*///?} else if >=26.1 {
/*import com.frikinjay.mobstacker.client.MobStackerRegionOverlay;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * From 1.21.11 the region boxes are gizmos: they are handed to the game while it collects the frame's
 * gizmos, just before it closes that collection ({@code finalizeGizmoCollection}, once a frame: from
 * {@code submitFeatures} on 26.2 and 26.3, from a lambda of the main pass on 1.21.11 and 26.1). The same on every loader.
 *
 * <p>And the frame's gizmos (not the always-on-top ones) are drawn in the late debug pass, which comes
 * after the clouds, the weather and the transparency's post chain, instead of in the main pass before
 * the translucent terrain: they write no depth, so the water and clouds drawn after them covered
 * boxes standing in front of them. Drawn before that pass clears the depth for its own.
 ^/
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGizmosMixin {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    /^* This frame's gizmos, held from the main pass to the late debug pass. ^/
    @Unique
    private DrawableGizmoPrimitives mobstacker$lateGizmos;

    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void mobstacker$regionGizmos(CallbackInfo ci) {
        MobStackerRegionOverlay.emitGizmos();
    }

    @Redirect(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/gizmos/DrawableGizmoPrimitives;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;)V"))
    private void mobstacker$gizmosLater(DrawableGizmoPrimitives primitives, PoseStack poseStack, MultiBufferSource buffers,
                                        CameraRenderState camera, Matrix4fc matrix) {
        mobstacker$lateGizmos = primitives;
    }

    @Inject(method = "lambda$addLateDebugPass$0", at = @At(value = "FIELD", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizedGizmos:Lnet/minecraft/client/renderer/LevelRenderer$FinalizedGizmos;"))
    private void mobstacker$gizmosAfterWeather(GpuBufferSlice fog, ResourceHandle<?> target, CameraRenderState camera,
                                               Matrix4fc matrix, CallbackInfo ci) {
        DrawableGizmoPrimitives primitives = mobstacker$lateGizmos;
        mobstacker$lateGizmos = null;
        if (primitives != null) {
            MultiBufferSource.BufferSource buffers = renderBuffers.bufferSource();
            primitives.render(new PoseStack(), buffers, camera, matrix);
            buffers.endBatch();
        }
    }
}
*///?} else if >=1.21.11 {
/*import com.frikinjay.mobstacker.client.MobStackerRegionOverlay;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * From 1.21.11 the region boxes are gizmos: they are handed to the game while it collects the frame's
 * gizmos, just before it closes that collection ({@code finalizeGizmoCollection}, once a frame: from
 * {@code submitFeatures} on 26.2 and 26.3, from a lambda of the main pass on 1.21.11 and 26.1). The same on every loader.
 *
 * <p>And the frame's gizmos (not the always-on-top ones) are drawn in the late debug pass, which comes
 * after the clouds, the weather and the transparency's post chain, instead of in the main pass before
 * the translucent terrain: they write no depth, so the water and clouds drawn after them covered
 * boxes standing in front of them. Drawn before that pass clears the depth for its own.
  *
 * <p>1.21.11, where the gizmos came: the same as 26.1. Fabric's mappings give the two lambdas no name of
 * their own: {@code method_62214} is the main pass's, {@code method_75413} the late debug pass's
 * (NeoForge will call them {@code lambda$...}).
^/
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGizmosMixin {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    /^* This frame's gizmos, held from the main pass to the late debug pass. ^/
    @Unique
    private DrawableGizmoPrimitives mobstacker$lateGizmos;

    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void mobstacker$regionGizmos(CallbackInfo ci) {
        MobStackerRegionOverlay.emitGizmos();
    }

    @Redirect(method = "method_62214", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/gizmos/DrawableGizmoPrimitives;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/state/CameraRenderState;Lorg/joml/Matrix4f;)V"))
    private void mobstacker$gizmosLater(DrawableGizmoPrimitives primitives, PoseStack poseStack, MultiBufferSource buffers,
                                        CameraRenderState camera, Matrix4f matrix) {
        mobstacker$lateGizmos = primitives;
    }

    @Inject(method = "method_75413", at = @At(value = "FIELD", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizedGizmos:Lnet/minecraft/client/renderer/LevelRenderer$FinalizedGizmos;"))
    private void mobstacker$gizmosAfterWeather(GpuBufferSlice fog, ResourceHandle<?> target, CameraRenderState camera,
                                               Matrix4f matrix, CallbackInfo ci) {
        DrawableGizmoPrimitives primitives = mobstacker$lateGizmos;
        mobstacker$lateGizmos = null;
        if (primitives != null) {
            MultiBufferSource.BufferSource buffers = renderBuffers.bufferSource();
            primitives.render(new PoseStack(), buffers, camera, matrix);
            buffers.endBatch();
        }
    }
}
*///?}
