package com.frikinjay.mobstacker.fabric.mixin.client;

import com.frikinjay.mobstacker.fabric.client.MobStackerRegionOverlay;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the region overlay keep the world's depth at the one moment no Fabric event marks: just before
 * the translucent terrain is drawn.
 *
 * <p>By then the solid terrain and every mob are in the depth buffer - including the mobs Iris holds
 * back and draws only when the game empties its buffers, which it does just before this - and nothing
 * see-through is yet. Under "Fast" and "Fancy" graphics water, stained glass and clouds write depth
 * into the same buffer the boxes are tested against, so a box under water was hidden by the surface
 * above it (round 9), where "Fabulous", which draws them into buffers of its own, showed it.
 *
 * <p>The point is found by the profiler's {@code "translucent"} section, which both branches of
 * {@code renderLevel} (Fabulous and not) open right before the translucent terrain - Iris hooks the
 * same constant. Not required: if a mod has moved it, the overlay falls back to the depth it keeps
 * before the debug renderer, which is the same picture except for the mobs Iris has not drawn yet.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=translucent"), require = 0)
    private void mobstacker$keepSolidDepth(CallbackInfo ci) {
        MobStackerRegionOverlay.keepSolidDepth("before translucent");
    }
}
