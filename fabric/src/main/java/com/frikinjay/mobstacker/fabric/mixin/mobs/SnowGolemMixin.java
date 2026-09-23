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
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shearing a stack of snow golems with {@code stackedHarvest} on takes every golem's pumpkin, the way
 * a stack of sheep gives every sheep's wool. The stack is one mob, so vanilla's shear of the top golem
 * has already taken the pumpkin off all of them; until 1.9.2 it paid out one carved pumpkin for the
 * lot. With {@code stackedHarvest} off nothing here runs: one golem is taken out of the stack and
 * sheared on its own ({@code MobStacker.isShearingOneInteraction}), as before.
 */
@Mixin(SnowGolem.class)
public class SnowGolemMixin implements StackShearable {

    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
    private void mobstacker$onShearAllGolems(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        SnowGolem self = (SnowGolem) (Object) this;
        ItemStack itemStack = player.getItemInHand(interactionHand);
        // As in SheepMixin: vanilla's own shear already dropped the top golem's pumpkin.
        int extra = MobStacker.extraHarvests(self);
        mobstacker$shearMembers(SoundSource.PLAYERS, extra);
        if (!self.level().isClientSide) {
            for (int i = 0; i < extra; i++) {
                itemStack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(mobstacker$getSlotForHand(interactionHand)));
            }
        }
    }

    /**
     * One carved pumpkin for each golem under the top one, dropped the way vanilla drops the top
     * one's. No sound: the top one's shear already made the one a click should.
     */
    @Override
    public void mobstacker$shearMembers(SoundSource source, int count) {
        SnowGolem self = (SnowGolem) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        for (int i = 0; i < count; i++) {
            self.spawnAtLocation(new ItemStack(Items.CARVED_PUMPKIN), 1.7F);
        }
    }

    private static EquipmentSlot mobstacker$getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
