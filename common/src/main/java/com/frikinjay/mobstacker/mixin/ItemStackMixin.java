package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * damageWeaponPerHit: what vanilla's own hit took off the weapon (1 for a sword, 2 for an axe, nothing
 * for a stick) is measured around {@code hurtEnemy} and taken again for every other mob in a stack
 * that dies as a whole.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Unique
    private int mobstacker$damageBeforeHit = -1;

    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void mobstacker$beforeHit(LivingEntity target, Player attacker, CallbackInfo ci) {
        mobstacker$damageBeforeHit = ((ItemStack) (Object) this).getDamageValue();
    }

    @Inject(method = "hurtEnemy", at = @At("RETURN"))
    private void mobstacker$afterHit(LivingEntity target, Player attacker, CallbackInfo ci) {
        int before = mobstacker$damageBeforeHit;
        mobstacker$damageBeforeHit = -1;
        ItemStack self = (ItemStack) (Object) this;
        if (before >= 0 && !self.isEmpty()) {
            MobStacker.wearWeaponForStack(self, target, attacker, self.getDamageValue() - before);
        }
    }
}
