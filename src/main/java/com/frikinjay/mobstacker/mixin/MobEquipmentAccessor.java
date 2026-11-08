package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Mob}'s protected drop-chance arrays so a stacked mob's stored equipment can be put
 * back exactly as it was.
 * <p>
 * The chance is what decides whether a piece of gear survives its owner's death — 0.085 for
 * naturally-spawned armor, 2.0 for anything the mob picked up (which then always drops, undamaged).
 * Storing the items without their chances would quietly turn every armed mob into a guaranteed
 * armor dispenser, so the stack keeps both and hands both back before the mob's death loot is rolled.
 */
@Mixin(Mob.class)
public interface MobEquipmentAccessor {
    /** Drop chances for the two hand slots, indexed by {@code EquipmentSlot#getIndex}. */
    @Accessor("handDropChances")
    float[] mobstacker$handDropChances();

    /** Drop chances for the four armor slots, indexed by {@code EquipmentSlot#getIndex}. */
    @Accessor("armorDropChances")
    float[] mobstacker$armorDropChances();
}
