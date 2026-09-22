package com.frikinjay.mobstacker.fabric.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sheep.class)
public class SheepMixin {
    /**
     * True while the loop below shears the members under the top sheep. {@code Sheep.shear} plays
     * the snip every time it runs, and all of them landed on the same tick at the same spot - so a
     * stack of sixty-four was sixty-four snips at once, which the last test round found at the cost
     * of their ears. Vanilla's own shear of the top sheep still makes the one sound a click should.
     */
    @Unique
    private boolean mobstacker$shearingQuietly;
    @Inject(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
    private void mobstacker$onShearAllSheep(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        Sheep self = (Sheep) (Object) this;
        ItemStack itemStack = player.getItemInHand(interactionHand);
        // Vanilla has already sheared one sheep's worth by the time this runs, so only the members
        // under it are owed anything. Looping the whole stack size gave a lone sheep double wool.
        int extra = MobStacker.extraHarvests(self);
        mobstacker$shearingQuietly = true;
        try {
            for (int i = 0; i < extra; i++) {
                self.shear(SoundSource.PLAYERS);
                if(!self.level().isClientSide) {
                    itemStack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(mobstacker$getSlotForHand(interactionHand)));
                }
            }
        } finally {
            mobstacker$shearingQuietly = false;
        }
    }

    @Redirect(method = "shear", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void mobstacker$oneSnipPerClick(Level level, Player player, Entity entity, SoundEvent sound,
                                            SoundSource source, float volume, float pitch) {
        if (!mobstacker$shearingQuietly) {
            level.playSound(player, entity, sound, source, volume, pitch);
        }
    }

    private static EquipmentSlot mobstacker$getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
