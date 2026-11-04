package com.frikinjay.mobstacker.forge.mixin.mobs;

import com.frikinjay.mobstacker.forge.ForgeShearing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A player's shears on a stack, on Forge: see {@link ForgeShearing}. {@code onSheared} is Forge's own
 * method, so it is not remapped.
 */
@Mixin(SnowGolem.class)
public class SnowGolemMixin {
    /** Members under the top owed a harvest, from the head of the shear in progress to its return. */
    @Unique
    private int mobstacker$membersOwed;

    @Inject(method = "onSheared", at = @At("HEAD"), cancellable = true, remap = false)
    private void mobstacker$beforeShear(Player player, ItemStack item, Level level, BlockPos pos, int fortune,
                                        CallbackInfoReturnable<List<ItemStack>> cir) {
        mobstacker$membersOwed = ForgeShearing.beforeShear((SnowGolem) (Object) this, player, item, level, pos, fortune, cir);
    }

    @Inject(method = "onSheared", at = @At("RETURN"), remap = false)
    private void mobstacker$afterShear(Player player, ItemStack item, Level level, BlockPos pos, int fortune,
                                       CallbackInfoReturnable<List<ItemStack>> cir) {
        int owed = mobstacker$membersOwed;
        mobstacker$membersOwed = 0;
        ForgeShearing.harvestMembers((SnowGolem) (Object) this, player, item, owed);
    }
}
