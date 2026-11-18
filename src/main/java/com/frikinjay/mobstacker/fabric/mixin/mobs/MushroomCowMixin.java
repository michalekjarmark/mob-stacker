package com.frikinjay.mobstacker.fabric.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The cow a sheared mooshroom turns into taking the stack along, on Fabric below 26.1 (from 26.1 the
 * conversion is convertTo, which MobMixin handles). A player's shears harvesting the members are
 * {@code MushroomCowShearMixin}'s, in common.
 */
@Mixin(value = MushroomCow.class)
public class MushroomCowMixin {

    /**
     * The cow a sheared mooshroom turns into takes the whole stack with it. Caught on its way into the
     * world, which is after vanilla has copied the mooshroom's name onto it: that name is the stack's
     * "Mooshroom x10" label, and left there it named a stack of cows that then took no other cow.
     * Every entity {@code shear} adds comes through here; only the cow is touched. From 1.21.2
     * the cow is a {@code convertTo}, which MobMixin carries the stack through.
     */
    //? if <1.21.2 {
    @ModifyArg(method = "shear", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobstacker$carryStackIntoCow(Entity entity) {
        if (entity instanceof Cow cow && !(entity instanceof MushroomCow)) {
            MobStacker.carryStackInto((MushroomCow) (Object) this, cow);
        }
        return entity;
    }
    //?}
}
