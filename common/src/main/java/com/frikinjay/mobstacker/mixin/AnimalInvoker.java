package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches an animal's own way of taking the food a player feeds it.
 *
 * <p>For stack breeding, which feeds many members in one click: an axolotl eats a bucket of tropical
 * fish and hands the water bucket back ({@code Axolotl.usePlayerItem}), and simply shrinking the food
 * ate the bucket with it (see {@code MobStacker.takeFood}).
 */
@Mixin(Animal.class)
public interface AnimalInvoker {

    @Invoker("usePlayerItem")
    void mobstacker$usePlayerItem(Player player, InteractionHand hand, ItemStack stack);
}
