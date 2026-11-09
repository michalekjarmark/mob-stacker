package com.frikinjay.mobstacker.neoforge.mixin;

import com.frikinjay.mobstacker.neoforge.NeoForgeShearing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * A player's shears on a stack, on NeoForge: the shears item's call to {@code IShearable.onSheared}
 * goes through {@link NeoForgeShearing}. The shears item spawns the drops it gets back and wears the
 * shears once, as for one mob; the members' wear is NeoForgeShearing's.
 */
@Mixin(ShearsItem.class)
public abstract class ShearsItemMixin {

    @Redirect(method = "interactLivingEntity", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/IShearable;onSheared(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ljava/util/List;",
            remap = false))
    private List<ItemStack> mobstacker$shearTheStack(IShearable shearable, Player player, ItemStack item, Level level, BlockPos pos) {
        return NeoForgeShearing.shear(shearable, player, item, level, pos).drops();
    }
}
