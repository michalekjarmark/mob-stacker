package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla never despawns an enderman that carries a block, so the block is never lost - and an
 * enderman that took one stays in the world for good. With {@code despawnPickedUpMobs} on it may go
 * like any other mob, and drops the block when it does ({@code MobStacker#dropOnDespawn}).
 */
@Mixin(EnderMan.class)
public abstract class EnderManMixin {

    @Inject(method = "requiresCustomPersistence", at = @At("RETURN"), cancellable = true)
    private void mobstacker$despawnWithBlock(CallbackInfoReturnable<Boolean> cir) {
        EnderMan self = (EnderMan) (Object) this;
        // Only when the block is the reason: a passenger stays persistent as vanilla has it.
        if (cir.getReturnValueZ() && self.getCarriedBlock() != null && !self.isPassenger()
                && !self.level().isClientSide() && MobStacker.getDespawnPickedUpMobs(self)) {
            cir.setReturnValue(false);
        }
    }
}
