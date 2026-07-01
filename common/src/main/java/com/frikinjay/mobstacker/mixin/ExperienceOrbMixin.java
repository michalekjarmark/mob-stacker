package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public class ExperienceOrbMixin {

    /**
     * Experience compaction. All of a mob's death experience is awarded through this static entry
     * point, so while a stacked mob is dying we sum it up (see {@link MobStacker#tryCaptureExperience})
     * and re-emit it as one orb once the death finishes, instead of scattering many small orbs.
     */
    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private static void mobstacker$captureCompactExperience(ServerLevel level, Vec3 position, int amount, CallbackInfo ci) {
        if (MobStacker.tryCaptureExperience(amount)) {
            ci.cancel();
        }
    }
}
