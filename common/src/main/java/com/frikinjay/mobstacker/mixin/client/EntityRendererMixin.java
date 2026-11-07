package com.frikinjay.mobstacker.mixin.client;

import com.frikinjay.mobstacker.client.MobStackerClientLabels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a stack's label (MobStackerClientLabels) the way vanilla draws a name tag: one line above the
 * mob's own name when that is showing, and in its place when it is not.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    /** One line of name-tag text, in blocks: vanilla's own spacing for the score under a player's name. */
    @Unique
    private static final float MOBSTACKER$LINE = 0.26F;

    @Shadow @Final protected EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    protected abstract boolean shouldShowName(T entity);

    @Shadow
    protected abstract void renderNameTag(T entity, Component name, PoseStack pose, MultiBufferSource buffers, int light);

    /**
     * At every return, not the last one: vanilla's {@code render} is {@code if (!shouldShowName)
     * return; renderNameTag(...)}, so TAIL - the final return only - ran only when the mob's own name
     * was showing. A stack without a name, or a named one not looked at, got no label at all (round 12).
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void mobstacker$stackLabel(T entity, float yaw, float partialTick, PoseStack pose,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        Component label = MobStackerClientLabels.labelFor(entity, entityRenderDispatcher.crosshairPickEntity);
        if (label == null) {
            return;
        }
        pose.pushPose();
        if (shouldShowName(entity)) {
            pose.translate(0.0F, MOBSTACKER$LINE, 0.0F);
        }
        renderNameTag(entity, label, pose, buffers, light);
        pose.popPose();
    }
}
