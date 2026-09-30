package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns feeding a stacked animal into a stack-aware action: feeding a stacked adult breeds its
 * members in pairs (into a baby-stack), and feeding a stacked baby speeds up the whole stack's
 * growth in proportion to its size. Single (unstacked) animals and non-food interactions fall
 * through to vanilla untouched.
 */
@Mixin(Animal.class)
public abstract class AnimalMixin {

    /**
     * Vanilla makes a bred baby without {@code finalizeSpawn}, so the origin hook never sees it; it is
     * marked here, on its way into the world.
     */
    @ModifyArg(method = "spawnChildFromBreeding", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private Entity mobstacker$markBred(Entity child) {
        if (child instanceof Mob mob) {
            MobStacker.setOrigin(mob, MobSpawnType.BREEDING);
        }
        return child;
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void mobstacker$stackBreeding(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Animal self = (Animal) (Object) this;
        if (self.level().isClientSide || !MobStacker.getEnableStackBreeding(self)) {
            return;
        }
        if (MobStacker.getStackSize(self) <= 1) {
            return; // a lone animal breeds the normal vanilla way
        }
        if (!MobStacker.canEverBreed(self)) {
            // An untamed wolf may fall in love in vanilla and will still never mate. Counting fed
            // members without asking gave a pack of wild wolves puppies vanilla would have refused.
            return;
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
