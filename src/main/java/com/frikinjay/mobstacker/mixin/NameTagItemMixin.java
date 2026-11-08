package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records that a name was given on purpose, the moment a name tag is actually used.
 * <p>
 * Without this the mod has to read the player's intent out of the name itself, which cannot tell a
 * pet named "Bella" from a stack labelled "Bella x16" — so renaming a stack used to make it stop
 * accepting mobs. The tag is remembered with the stack instead (see
 * {@link MobStacker#onNameTagApplied}), and the name is stored exactly as typed so the live count
 * is only ever appended, never parsed back off.
 */
@Mixin(NameTagItem.class)
public class NameTagItemMixin {

    @Inject(method = "interactLivingEntity", at = @At("RETURN"))
    private void mobstacker$onNameTagUsed(ItemStack stack, Player player, LivingEntity target,
                                          InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // The method reports success on both sides, and reports it even when the target was too dead
        // to rename, so take the name from the entity itself rather than from the return value.
        if (target.level().isClientSide() || !cir.getReturnValue().consumesAction()) {
            return;
        }
        if (target instanceof Mob mob && mob.hasCustomName()) {
            MobStacker.onNameTagApplied(mob, mob.getCustomName());
        }
    }
}
