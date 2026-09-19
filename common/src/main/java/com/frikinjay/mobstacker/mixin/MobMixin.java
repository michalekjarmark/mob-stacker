package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
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

    /**
     * Touching a herd hands you one animal out of it.
     *
     * <p>A horse is an individual: it is tamed, saddled, given armor and ridden one at a time, and
     * every one of those acts on a stack would have applied to all sixteen at once — you would
     * climb onto a single entity carrying a whole herd. So a stacked mount steps one mob out of the
     * stack and lets that mob take the interaction instead, for every kind of right-click there is.
     *
     * <p>Only mounts, deliberately. A wolf pack has the same problem — a handful of bones tames all
     * sixteen — but an empty-handed right-click on an untamed wolf does nothing in vanilla, and
     * peeling a wolf off the pack for it would be worse than the bug. Taming a wolf stack still
     * tames the whole stack; what it can no longer do is lose anything, because a tamed mob stops
     * stacking entirely (see {@link MobStacker#isPlayerBound}).
     *
     * <p>Server side only. The client keeps predicting the interaction against the stack exactly as
     * vanilla would, and the server's answer — a new mob, a smaller stack — arrives right after.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void mobstacker$interactWithOneOfTheStack(Player player, InteractionHand hand,
                                                      CallbackInfoReturnable<InteractionResult> cir) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide() || MobStacker.getStackSize(self) <= 1) {
            return;
        }
        if (!(self instanceof AbstractHorse)) {
            return;
        }
        Mob separated = MobStacker.separateOne(self, false);
        if (separated != null) {
            cir.setReturnValue(separated.interact(player, hand));
        }
    }

}
