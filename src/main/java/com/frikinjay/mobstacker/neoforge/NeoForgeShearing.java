package com.frikinjay.mobstacker.neoforge;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import com.frikinjay.mobstacker.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;

import java.util.List;

/**
 * Shearing a stack on NeoForge. A sheep's, mooshroom's or snow golem's {@code mobInteract} does not
 * shear there: the shears item and the dispenser call {@code IShearable.onSheared}, whose default
 * shears the mob and hands its drops back. Both call sites go through {@link #shear} instead (the
 * shears item's mixin here, the dispenser's in common), so a stack pays out per member as with a
 * player's shears on the other loaders; what a member gives is the same {@link StackShearable}.
 */
public final class NeoForgeShearing {

    private NeoForgeShearing() {
    }

    /** What one shear did: the drops for the caller to spawn, and how many members under the top it sheared. */
    public record Sheared(List<ItemStack> drops, int members) {
    }

    /**
     * Shears {@code shearable} as NeoForge would, but as a stack: with {@code stackedHarvest} off one
     * mob comes out and only that one is sheared; otherwise the top is sheared and every member under it
     * gives its harvest too, and a player's shears wear once for each of them (the caller wears them for
     * the top). A dispenser ({@code player} null) wears its shears itself.
     */
    public static Sheared shear(IShearable shearable, Player player, ItemStack item, Level level, BlockPos pos) {
        if (!(shearable instanceof Mob mob) || level.isClientSide() || MobStacker.getStackSize(mob) <= 1) {
            return new Sheared(shearable.onSheared(player, item, level, pos), 0);
        }
        if (!MobStacker.getStackedHarvest(mob)) {
            Mob one = MobStacker.separateOne(mob, false);
            if (one instanceof IShearable single && single.isShearable(player, item, level, pos)) {
                return new Sheared(single.onSheared(player, item, level, pos), 0);
            }
        }
        // Counted before the top one is sheared: a mooshroom stops being one the moment it is.
        int members = MobStacker.extraHarvests(mob);
        List<ItemStack> drops = shearable.onSheared(player, item, level, pos);
        if (members > 0 && mob instanceof StackShearable stack) {
            stack.mobstacker$shearMembers(player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, members);
            if (player != null) {
                EquipmentSlot slot = player.getItemInHand(InteractionHand.OFF_HAND) == item
                        ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
                for (int i = 0; i < members && !item.isEmpty(); i++) {
                    Compat.hurtItem(item, 1, player, slot);
                }
            }
        }
        return new Sheared(drops, members);
    }
}
