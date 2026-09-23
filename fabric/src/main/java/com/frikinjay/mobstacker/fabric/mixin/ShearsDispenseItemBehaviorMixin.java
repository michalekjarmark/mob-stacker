package com.frikinjay.mobstacker.fabric.mixin;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Shearable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A dispenser's shears treat a stack the way a player's do.
 *
 * <p>Until now a dispenser sheared the stack as the one mob it is to vanilla: with
 * {@code stackedHarvest} on, one sheep's wool for the whole stack; with it off, the whole stack's
 * wool gone for one sheep's worth. Now, like a player's shears, it pays out for every member when
 * {@code stackedHarvest} is on and wears the shears down once for each, and takes one mob out of the
 * stack and shears only that one when it is off.
 */
@Mixin(ShearsDispenseItemBehavior.class)
public abstract class ShearsDispenseItemBehaviorMixin {

    /**
     * How many members under the top one the dispense in progress sheared, so {@code execute} can
     * wear the shears down for each of them. Set and read within one dispense on the server thread.
     */
    @Unique
    private static int mobstacker$membersShorn;

    @Redirect(method = "tryShearLivingEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Shearable;shear(Lnet/minecraft/sounds/SoundSource;)V"))
    private static void mobstacker$shearWithTheStack(Shearable shearable, SoundSource source) {
        if (!(shearable instanceof Mob mob) || MobStacker.getStackSize(mob) <= 1) {
            shearable.shear(source);
            return;
        }
        if (!MobStacker.getStackedHarvest(mob)) {
            // The same as MobStacker.isShearingOneInteraction for a player: shearing changes the mob,
            // and the stack is one mob, so one comes out to be sheared on its own.
            Mob one = MobStacker.separateOne(mob, false);
            if (one instanceof Shearable single && single.readyForShearing()) {
                single.shear(source);
                return;
            }
        }
        // Counted before the top one is sheared: a mooshroom stops being one the moment it is.
        int extra = MobStacker.extraHarvests(mob);
        shearable.shear(source);
        if (extra > 0 && shearable instanceof StackShearable stack) {
            stack.mobstacker$shearMembers(source, extra);
            mobstacker$membersShorn = extra;
        }
    }

    /** One point of wear per member sheared, as a player's shears take, on top of vanilla's one. */
    @ModifyArg(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z"),
            index = 0)
    private int mobstacker$wearForEveryMember(int amount) {
        int extra = mobstacker$membersShorn;
        mobstacker$membersShorn = 0;
        return amount + extra;
    }
}
