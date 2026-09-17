package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobMixin {

    /**
     * Gives every mob a periodic chance to join a nearby stack. Merging is otherwise only attempted
     * when a mob crosses a block boundary, so mobs that never move - several spawn eggs used on one
     * block, mobs with no AI, a penned-in group - would stand side by side and never stack. The
     * timer, the staggering and the cheap early-outs live in MobStacker#tickStackScan.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void mobstacker$periodicStackScan(CallbackInfo ci) {
        MobStacker.tickStackScan((Mob) (Object) this);
    }

    @Inject(method = "convertTo", at = @At("RETURN"), cancellable = true)
    private <T extends Mob> void mobstacker$convertTo(EntityType<T> entityType, boolean bl, CallbackInfoReturnable<T> cir) {
        Mob instance = (Mob) (Object) this;
        T mob = cir.getReturnValue();
        if (mob == null) {
                cir.setReturnValue(null);
        } else {
            MobStacker.setStackSize(mob, MobStacker.getStackSize(instance));
            if(mob.hasCustomName()) {
                mob.setCustomName(null);
            }
            // A stack a player named keeps its name through a conversion (zombie -> drowned, ...),
            // so the label is rebuilt from the name they gave rather than dropped on the floor.
            MobStacker.copyStackNaming(instance, mob);
            MobStacker.updateStackDisplay(mob);
        }
    }

}
