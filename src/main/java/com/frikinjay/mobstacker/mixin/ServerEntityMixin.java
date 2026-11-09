package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.compat.Nbt;
import com.frikinjay.mobstacker.ICustomDataHolder;
import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackLabels;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {

    @Shadow @Final private Entity entity;

    /** The label a player is told about when the mob comes into view (StackLabels#addPairing). */
    @Inject(method = "sendPairingData", at = @At("TAIL"))
    private void mobstacker$stackLabelOnPairing(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> out,
                                                CallbackInfo ci) {
        if (entity instanceof Mob mob) {
            StackLabels.addPairing(player, mob, out);
        }
    }

    @Inject(
            method = "sendChanges",
            at = @At("HEAD")
    )
    private void mobstacker$updateStackDisplayBeforeSendingChanges(CallbackInfo ci) {
        if (entity instanceof Mob livingEntity && livingEntity instanceof ICustomDataHolder) {
            CompoundTag customData = ((ICustomDataHolder) livingEntity).mobstacker$getCustomData();
            int stackSize = Nbt.getInt(customData, MobStacker.STACK_SIZE_KEY);
            // Once a second: the label is sent only when it changed (StackLabels#refresh), and what can
            // change it without a size change - a setting, a region boundary - is not in a hurry.
            if (stackSize > 1 && (livingEntity.tickCount + livingEntity.getId()) % 20 == 0) {
                MobStacker.updateStackDisplay(livingEntity);
            }
        }
    }
}