package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns feeding a stacked animal into a stack-aware action: feeding a stacked adult breeds its
 * members in pairs (into a baby-stack), and feeding a stacked baby speeds up the whole stack's
 * growth in proportion to its size. Single (unstacked) animals and non-food interactions fall
 * through to vanilla untouched.
 */
@Mixin(Animal.class)
public abstract class AnimalMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mobstacker$stackBreeding(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Animal self = (Animal) (Object) this;
        if (self.level().isClientSide || !MobStacker.getEnableStackBreeding()) {
            return;
        }
        if (MobStacker.getStackSize(self) <= 1) {
            return; // a lone animal breeds the normal vanilla way
        }
        ItemStack food = player.getItemInHand(hand);
        if (food.isEmpty() || !self.isFood(food)) {
            return; // not its breeding food: leave leashing/other interactions to vanilla
        }

        InteractionResult result = self.isBaby()
                ? MobStacker.handleBabyStackFeeding(self, player, hand, food)
                : MobStacker.handleStackBreeding(self, player, hand, food);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
