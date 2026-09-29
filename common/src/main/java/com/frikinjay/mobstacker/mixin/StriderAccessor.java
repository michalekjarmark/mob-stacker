package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.ItemBasedSteering;
import net.minecraft.world.entity.monster.Strider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches a strider's saddle, which has a setter only through its private steering.
 *
 * <p>For one case: {@code Strider.finalizeSpawn} now and then seats a zombified piglin on the new
 * strider and saddles it for the ride. The mod takes the rider off a mob it creates (see
 * {@code MobStacker.finalizeLikeANewMob}), and the saddle has to come off with it - a saddled strider
 * never stacks again, and drops a saddle nobody put there when it dies.
 */
@Mixin(Strider.class)
public interface StriderAccessor {

    @Accessor("steering")
    ItemBasedSteering mobstacker$steering();
}
