package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "interactOn", at = @At("HEAD"))
    private void mobstacker$onInteract(Entity entity, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        if(MobStacker.getEnableSeparator() && entity instanceof Mob && !entity.level().isClientSide) {
            Player player = (Player) (Object) this;
            int stackSize = MobStacker.getStackSize((Mob) entity);
            ItemStack itemStack = player.getItemInHand(interactionHand);

            ResourceLocation separatorResourceLocation = mobstacker$getResourceLocation();

            if (stackSize > 1 && itemStack.is(BuiltInRegistries.ITEM.get(separatorResourceLocation))) {
                MobStacker.separateEntity((Mob) entity);
                if(!player.isCreative() && MobStacker.getConsumeSeparator()) {
                    itemStack.setCount(itemStack.getCount() - 1);
                }
            }
        }
    }

    /**
     * Records whether this swing is one vanilla would have swept with, for the optional
     * sweepingEdgeVanillaConditions rule. It has to be sampled here: attack() resets the
     * attack-strength counter before the target is hurt, so by the time the sweep bonus is computed
     * the charge level of the swing can no longer be read.
     */
    @Inject(method = "attack", at = @At("HEAD"))
    private void mobstacker$captureSweepContext(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.level().isClientSide) {
            MobStacker.setVanillaSweepContext(player, mobstacker$wouldVanillaSweep(player, target));
        }
    }

    @Unique
    private static boolean mobstacker$wouldVanillaSweep(Player player, Entity target) {
        if (!(target instanceof LivingEntity) || !(player.getMainHandItem().getItem() instanceof SwordItem)) {
            return false;
        }
        if (player.getAttackStrengthScale(0.5F) <= 0.9F || player.isSprinting() || !player.onGround()) {
            return false;
        }
        if (player.hasEffect(MobEffects.BLINDNESS) || player.isPassenger()) {
            return false;
        }
        // Vanilla also skips the sweep on a critical hit, but a crit needs the player to be falling,
        // which the onGround() check above already rules out. What is left is vanilla's own
        // "standing still enough" condition.
        double moved = player.walkDist - player.walkDistO;
        return moved < (double) player.getSpeed();
    }

    @Unique
    private static @NotNull ResourceLocation mobstacker$getResourceLocation() {
        String separatorItemId = MobStacker.getSeparatorItem();
        String[] parts = separatorItemId.split(":", 2);
        ResourceLocation separatorResourceLocation;

        if (parts.length == 2) {
            separatorResourceLocation = new ResourceLocation(parts[0], parts[1]);
        } else {
            // Fallback to "minecraft" namespace if no colon is present
            separatorResourceLocation = new ResourceLocation("minecraft", separatorItemId);
        }
        return separatorResourceLocation;
    }

}
