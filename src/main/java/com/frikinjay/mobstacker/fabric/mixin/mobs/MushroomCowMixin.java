package com.frikinjay.mobstacker.fabric.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.frikinjay.mobstacker.compat.Compat;

/**
 * When a player's shears harvest the members of a mooshroom stack on Fabric, and the cow the top one
 * turns into taking the stack along. What a member gives is {@code MushroomCowShearMixin}'s, in common.
 */
@Mixin(value = MushroomCow.class)
public class MushroomCowMixin {

    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = Compat.HURT_AND_BREAK, shift = At.Shift.AFTER))
    private void mobstacker$onShearAllMoo(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        MushroomCow self = (MushroomCow) (Object) this;
        ItemStack itemStack = player.getItemInHand(interactionHand);
        // As in SheepMixin: vanilla's own shear already dropped five mushrooms for the top mob.
        int extra = MobStacker.extraHarvests(self);
        if(!self.level().isClientSide) {
            ((StackShearable) self).mobstacker$shearMembers(SoundSource.PLAYERS, extra);
            for (int i = 0; i < extra; i++) {
                Compat.hurtItem(itemStack, 1, player, mobstacker$getSlotForHand(interactionHand));
            }
        }
    }

    /**
     * The cow a sheared mooshroom turns into takes the whole stack with it. Caught on its way into the
     * world, which is after vanilla has copied the mooshroom's name onto it: that name is the stack's
     * "Mooshroom x10" label, and left there it named a stack of cows that then took no other cow.
     * Every entity {@code shear} adds comes through here; only the cow is touched.
     */
    @ModifyArg(method = "shear", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobstacker$carryStackIntoCow(Entity entity) {
        if (entity instanceof Cow cow && !(entity instanceof MushroomCow)) {
            MobStacker.carryStackInto((MushroomCow) (Object) this, cow);
        }
        return entity;
    }

    private static EquipmentSlot mobstacker$getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
