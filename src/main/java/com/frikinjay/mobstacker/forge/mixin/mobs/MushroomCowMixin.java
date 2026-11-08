package com.frikinjay.mobstacker.forge.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.forge.ForgeShearing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A player's shears on a mooshroom stack, on Forge (see {@link ForgeShearing}), and the cow the top
 * one turns into taking the stack along - which on Forge happens in its private {@code shearInternal},
 * the one place both a player's shears ({@code onSheared}) and a dispenser's ({@code shear}) end up.
 * Both are Forge's own methods, so they are not remapped; what they call is.
 */
@Mixin(MushroomCow.class)
public class MushroomCowMixin {
    /** Members under the top owed a harvest, from the head of the shear in progress to its return. */
    @Unique
    private int mobstacker$membersOwed;

    @Inject(method = "onSheared", at = @At("HEAD"), cancellable = true, remap = false)
    private void mobstacker$beforeShear(Player player, ItemStack item, Level level, BlockPos pos, int fortune,
                                        CallbackInfoReturnable<List<ItemStack>> cir) {
        mobstacker$membersOwed = ForgeShearing.beforeShear((MushroomCow) (Object) this, player, item, level, pos, fortune, cir);
    }

    @Inject(method = "onSheared", at = @At("RETURN"), remap = false)
    private void mobstacker$afterShear(Player player, ItemStack item, Level level, BlockPos pos, int fortune,
                                       CallbackInfoReturnable<List<ItemStack>> cir) {
        int owed = mobstacker$membersOwed;
        mobstacker$membersOwed = 0;
        ForgeShearing.harvestMembers((MushroomCow) (Object) this, player, item, owed);
    }

    /**
     * The cow takes the whole stack with it. Caught on its way into the world, which is after vanilla
     * has copied the mooshroom's name onto it: that name is the stack's "Mooshroom x10" label, and left
     * there it named a stack of cows that then took no other cow.
     */
    @ModifyArg(method = "shearInternal", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobstacker$carryStackIntoCow(Entity entity) {
        if (entity instanceof Cow cow && !(entity instanceof MushroomCow)) {
            MobStacker.carryStackInto((MushroomCow) (Object) this, cow);
        }
        return entity;
    }
}
