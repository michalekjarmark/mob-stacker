package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.frog.Tadpole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A stack of tadpoles grows up into a stack of frogs.
 *
 * <p>A tadpole that grows up is replaced by a new frog ({@code EntityType.create}, then
 * {@code discard}), not converted with {@code convertTo}, so the stack was never carried over: ten
 * tadpoles grew up into one frog. Like a pig or a villager struck by lightning, the stack is carried
 * onto the frog just as it is added to the world.
 */
@Mixin(Tadpole.class)
public class TadpoleMixin {

    @ModifyArg(method = "ageUp()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private Entity mobstacker$growUpAsAStack(Entity frog) {
        if (frog instanceof Mob grown) {
            MobStacker.carryStackInto((Mob) (Object) this, grown);
        }
        return frog;
    }
}
