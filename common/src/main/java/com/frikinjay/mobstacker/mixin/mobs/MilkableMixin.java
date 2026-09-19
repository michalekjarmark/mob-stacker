package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Milking a stack fills as many buckets as you brought, up to one per mob in it.
 *
 * <p>Runs before vanilla rather than after, and only for the members underneath: vanilla still
 * milks the top mob out of the bucket in hand exactly as it always did, so the sound, the swing and
 * the creative-mode behaviour are its own. One bucket is always left for it to work with.
 *
 * <p>Targets {@link Cow} and {@link Goat}, which covers mooshrooms too — their own
 * {@code mobInteract} hands a bucket straight to {@code Cow.mobInteract}.
 */
@Mixin({Cow.class, Goat.class})
public class MilkableMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void mobstacker$milkTheWholeStack(Player player, InteractionHand hand,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        Animal self = (Animal) (Object) this;
        if (self.level().isClientSide() || self.isBaby()) {
            return;
        }
        if (!player.getItemInHand(hand).is(Items.BUCKET)) {
            return; // not a milking: feeding and everything else is none of our business
        }
        MobStacker.milkExtraMembers(self, player);
    }
}
