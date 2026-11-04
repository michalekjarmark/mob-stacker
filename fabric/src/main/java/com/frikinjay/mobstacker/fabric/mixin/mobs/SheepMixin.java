package com.frikinjay.mobstacker.fabric.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When a player's shears harvest the members of a stack on Fabric, where shearing is vanilla's
 * {@code mobInteract}. What a member gives is {@code SheepShearMixin}'s, in common.
 */
@Mixin(Sheep.class)
public class SheepMixin {
    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
    private void mobstacker$onShearAllSheep(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        Sheep self = (Sheep) (Object) this;
        ItemStack itemStack = player.getItemInHand(interactionHand);
        // Vanilla has already sheared one sheep's worth by the time this runs, so only the members
        // under it are owed anything. Looping the whole stack size gave a lone sheep double wool.
        int extra = MobStacker.extraHarvests(self);
        ((StackShearable) self).mobstacker$shearMembers(SoundSource.PLAYERS, extra);
        if (!self.level().isClientSide) {
            for (int i = 0; i < extra; i++) {
                itemStack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(mobstacker$getSlotForHand(interactionHand)));
            }
        }
    }

    private static EquipmentSlot mobstacker$getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
