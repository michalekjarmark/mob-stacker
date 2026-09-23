package com.frikinjay.mobstacker.fabric.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = MushroomCow.class)
public class MushroomCowMixin implements StackShearable {

    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
    private void mobstacker$onShearAllMoo(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        MushroomCow self = (MushroomCow) (Object) this;
        ItemStack itemStack = player.getItemInHand(interactionHand);
        // As in SheepMixin: vanilla's own shear already dropped five mushrooms for the top mob.
        int extra = MobStacker.extraHarvests(self);
        if(!self.level().isClientSide) {
            mobstacker$shearMembers(SoundSource.PLAYERS, extra);
            for (int i = 0; i < extra; i++) {
                itemStack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(mobstacker$getSlotForHand(interactionHand)));
            }
        }
    }

    /**
     * Five mushrooms for each member under the top mooshroom. By now vanilla has already turned the
     * top one into a cow (the stack went with it, below) and dropped its own five; this mooshroom is
     * discarded but still knows its variant, which is all a member's harvest needs.
     */
    @Override
    public void mobstacker$shearMembers(SoundSource source, int count) {
        MushroomCow self = (MushroomCow) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 5; ++j) {
                self.level().addFreshEntity(new ItemEntity(self.level(), self.getX(), self.getY(1.0), self.getZ(), new ItemStack(self.getVariant().getBlockState().getBlock())));
            }
        }
    }

    @Inject(method = "shear", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Cow;moveTo(DDDFF)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void mobstacker$onInternalShearAllMoo(SoundSource soundSource, CallbackInfo ci, Cow cow) {
        MushroomCow self = (MushroomCow) (Object) this;
        if (cow != null) {
            MobStacker.setStackSize(cow, MobStacker.getStackSize(self));
        }
    }

    private static EquipmentSlot mobstacker$getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
