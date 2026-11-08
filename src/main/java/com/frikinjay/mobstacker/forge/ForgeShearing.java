package com.frikinjay.mobstacker.forge;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.IForgeShearable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Shearing a stack on Forge, where a player's shears go through {@code IForgeShearable.onSheared}
 * (called by {@code ShearsItem}) rather than through the mob's {@code mobInteract} as in vanilla. The
 * sheep, mooshroom and snow golem mixins call these two at the head and the return of their
 * {@code onSheared}; what a member gives is the same {@link StackShearable} every loader uses.
 *
 * <p>A dispenser does not come through here: on Forge it still calls vanilla's
 * {@code Shearable.shear}, which {@code ShearsDispenseItemBehaviorMixin} in common already handles.
 */
public final class ForgeShearing {

    private ForgeShearing() {
    }

    /**
     * At the head of {@code onSheared}. With {@code stackedHarvest} off a stack hands one mob over and
     * only that one is sheared - its drops are returned in place of the stack's, so {@code ShearsItem}
     * drops them and wears the shears once, as for one mob. Otherwise returns how many members under
     * the top are owed a harvest, counted now because a mooshroom stops being one the moment it is
     * sheared; the caller keeps it for {@link #harvestMembers}.
     */
    public static int beforeShear(Mob mob, Player player, ItemStack item, Level level, BlockPos pos, int fortune,
                                  CallbackInfoReturnable<List<ItemStack>> cir) {
        if (level.isClientSide() || MobStacker.getStackSize(mob) <= 1) {
            return 0;
        }
        if (!MobStacker.getStackedHarvest(mob)) {
            Mob one = MobStacker.separateOne(mob, false);
            if (one instanceof IForgeShearable single && single.isShearable(item, level, pos)) {
                cir.setReturnValue(single.onSheared(player, item, level, pos, fortune));
            }
            return 0;
        }
        return MobStacker.extraHarvests(mob);
    }

    /**
     * At the return of {@code onSheared}, once the top mob has been sheared: harvests the members under
     * it and wears a player's shears down once for each, as one shear each would have.
     */
    public static void harvestMembers(Mob mob, Player player, ItemStack item, int members) {
        if (members <= 0 || mob.level().isClientSide()) {
            return;
        }
        ((StackShearable) mob).mobstacker$shearMembers(player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, members);
        if (player == null) {
            return;
        }
        InteractionHand hand = player.getItemInHand(InteractionHand.OFF_HAND) == item
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        for (int i = 0; i < members; i++) {
            item.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
        }
    }
}
