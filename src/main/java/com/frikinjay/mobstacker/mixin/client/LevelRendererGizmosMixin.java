package com.frikinjay.mobstacker.mixin.client;

//? if >=26.1 {
/*import com.frikinjay.mobstacker.client.MobStackerRegionOverlay;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * From 26.1 the region boxes are gizmos: they are handed to the game while it collects the frame's
 * gizmos, just before it closes that collection in {@code submitFeatures}. The same on every loader.
 ^/
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGizmosMixin {
    @Inject(method = "submitFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void mobstacker$regionGizmos(CallbackInfo ci) {
        MobStackerRegionOverlay.emitGizmos();
    }
}
*///?}
