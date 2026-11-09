package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Mob}'s protected drop-chance getter so a stacked mob's stored equipment can be put
 * back exactly as it was (the setter, {@code Mob.setDropChance}, is public).
 * <p>
 * The chance is what decides whether a piece of gear survives its owner's death — 0.085 for
 * naturally-spawned armor, 2.0 for anything the mob picked up (which then always drops, undamaged).
 * Storing the items without their chances would quietly turn every armed mob into a guaranteed
 * armor dispenser, so the stack keeps both and hands both back before the mob's death loot is rolled.
 * <p>
 * Vanilla's own getter rather than its arrays: from 1.20.5 there is a body slot (horse and wolf
 * armor) with a chance of its own, outside both arrays.
 */
@Mixin(Mob.class)
public interface MobEquipmentAccessor {
    //? if <26.1 {
    @Invoker("getEquipmentDropChance")
    float mobstacker$dropChance(EquipmentSlot slot);
    //?}
}
