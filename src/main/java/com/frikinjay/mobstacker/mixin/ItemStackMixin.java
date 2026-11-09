package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.compat.Compat;
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
 *
 * <p>From 1.21 the wear is taken in {@code postHurtEnemy}, after {@code hurtEnemy} ({@link Compat#WEAPON_HIT_METHOD}).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Unique
    private int mobstacker$damageBeforeHit = -1;

    @Inject(method = Compat.WEAPON_HIT_METHOD, at = @At("HEAD"))
    //? if >=26.1 {
    /*private void mobstacker$beforeHit(LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
    *///?} else {
    private void mobstacker$beforeHit(LivingEntity target, Player attacker, CallbackInfo ci) {
    //?}
        mobstacker$damageBeforeHit = ((ItemStack) (Object) this).getDamageValue();
    }

    @Inject(method = Compat.WEAPON_HIT_METHOD, at = @At("RETURN"))
    //? if >=26.1 {
    /*private void mobstacker$afterHit(LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
    *///?} else {
    private void mobstacker$afterHit(LivingEntity target, Player attacker, CallbackInfo ci) {
    //?}
        int before = mobstacker$damageBeforeHit;
        mobstacker$damageBeforeHit = -1;
        ItemStack self = (ItemStack) (Object) this;
        if (before >= 0 && !self.isEmpty() && (LivingEntity) attacker instanceof Player player) {
            MobStacker.wearWeaponForStack(self, target, player, self.getDamageValue() - before);
        }
    }
}
