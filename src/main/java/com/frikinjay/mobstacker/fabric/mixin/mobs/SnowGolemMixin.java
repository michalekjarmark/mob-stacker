package com.frikinjay.mobstacker.fabric.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.frikinjay.mobstacker.compat.Compat;

/**
 * Shearing a stack of snow golems with {@code stackedHarvest} on takes every golem's pumpkin, the way
 * a stack of sheep gives every sheep's wool. The stack is one mob, so vanilla's shear of the top golem
 * has already taken the pumpkin off all of them; until 1.9.2 it paid out one carved pumpkin for the
 * lot. With {@code stackedHarvest} off nothing here runs: one golem is taken out of the stack and
 * sheared on its own ({@code MobStacker.isShearingOneInteraction}), as before.
 */
@Mixin(SnowGolem.class)
public class SnowGolemMixin {

    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = Compat.HURT_AND_BREAK, shift = At.Shift.AFTER))
    private void mobstacker$onShearAllGolems(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        SnowGolem self = (SnowGolem) (Object) this;
        ItemStack itemStack = player.getItemInHand(interactionHand);
        // As in SheepMixin: vanilla's own shear already dropped the top golem's pumpkin.
        int extra = MobStacker.extraHarvests(self);
        ((StackShearable) self).mobstacker$shearMembers(SoundSource.PLAYERS, extra);
        if (!self.level().isClientSide()) {
            for (int i = 0; i < extra; i++) {
                Compat.hurtItem(itemStack, 1, player, mobstacker$getSlotForHand(interactionHand));
            }
        }
    }

    private static EquipmentSlot mobstacker$getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
