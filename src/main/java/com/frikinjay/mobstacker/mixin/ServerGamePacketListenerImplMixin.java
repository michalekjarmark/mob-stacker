package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.StackLabels;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every packet a player is sent passes here, and an entity-data packet that carries a stacked mob's
 * name is made right for that player: the label in the name for a game without the mod, the label on
 * its own channel for one with it ({@link StackLabels#personalise}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow public ServerPlayer player;

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> mobstacker$personaliseStackLabel(Packet<?> packet) {
        return player == null ? packet : StackLabels.personalise(player, packet);
    }
}
