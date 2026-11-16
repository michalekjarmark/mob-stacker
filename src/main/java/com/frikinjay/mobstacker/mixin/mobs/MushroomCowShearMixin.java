package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackShearable;
import com.frikinjay.mobstacker.compat.Compat;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What one mooshroom under the top of a stack gives when the stack is sheared, on every loader; see
 * {@link SheepShearMixin} for who decides when.
 */
@Mixin(MushroomCow.class)
public class MushroomCowShearMixin implements StackShearable {

    /**
     * Five mushrooms for each member under the top mooshroom. By now vanilla has already turned the
     * top one into a cow (the stack went with it) and dropped its own five; this mooshroom is
     * discarded but still knows its variant, which is all a member's harvest needs.
     */
    @Override
    public void mobstacker$shearMembers(SoundSource source, int count) {
        MushroomCow self = (MushroomCow) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 5; ++j) {
                self.level().addFreshEntity(new ItemEntity(self.level(), self.getX(), self.getY(1.0), self.getZ(), new ItemStack(self.getVariant().getBlockState().getBlock())));
            }
        }
    }

    //? if fabric || >=26.1 {
    /**
     * A player's shears harvesting the members of a mooshroom stack, where they shear in the
     * mooshroom's own {@code mobInteract}: on Fabric, and on NeoForge from 26.1, which leaves vanilla's
     * shears there for the mooshroom alone (its sheep and snow golems shear through
     * {@code IShearable}, see {@code NeoForgeShearing}). With {@code stackedHarvest} off a stack hands
     * one mooshroom over first ({@code MobStacker.isShearingOneInteraction}), so this sees a single mob.
     */
    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = Compat.HURT_AND_BREAK, shift = At.Shift.AFTER))
    private void mobstacker$onShearAllMoo(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        MushroomCow self = (MushroomCow) (Object) this;
        ItemStack itemStack = player.getItemInHand(interactionHand);
        // As in SheepMixin: vanilla's own shear already dropped five mushrooms for the top mob.
        int extra = MobStacker.extraHarvests(self);
        if (!self.level().isClientSide()) {
            mobstacker$shearMembers(SoundSource.PLAYERS, extra);
            for (int i = 0; i < extra; i++) {
                Compat.hurtItem(itemStack, 1, player, mobstacker$getSlotForHand(interactionHand));
            }
        }
    }

    private static EquipmentSlot mobstacker$getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
    //?}

    // The cow a sheared mooshroom turns into takes the whole stack with it, on NeoForge below 26.1
    // (Fabric's MushroomCowMixin and Forge's do the same; from 26.1 the conversion is convertTo, which
    // MobMixin handles). Without it a stack of mooshrooms sheared on NeoForge became one cow. Caught on
    // its way into the world, after vanilla has copied the mooshroom's name ("Mooshroom x10") onto it.
    //? if neoforge && >=1.20.3 && <26.1 {
    /*@ModifyArg(method = "shear", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobstacker$carryStackIntoCow(Entity entity) {
        if (entity instanceof Cow cow && !(entity instanceof MushroomCow)) {
            MobStacker.carryStackInto((MushroomCow) (Object) this, cow);
        }
        return entity;
    }
    *///?}
    //? if neoforge && <1.20.3 {
    /*// NeoForge 20.2 shears in Forge's private shearInternal, as Forge 1.20.1 does (its own mixin).
    @ModifyArg(method = "shearInternal", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity mobstacker$carryStackIntoCow(Entity entity) {
        if (entity instanceof Cow cow && !(entity instanceof MushroomCow)) {
            MobStacker.carryStackInto((MushroomCow) (Object) this, cow);
        }
        return entity;
    }
    *///?}
}
