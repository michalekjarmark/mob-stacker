package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.StackLabels;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//? if <1.20.2 {
import org.spongepowered.asm.mixin.Shadow;
//?}

/**
 * Every packet a player is sent passes here, and an entity-data packet that carries a stacked mob's
 * name is made right for that player: the label in the name for a game without the mod, the label on
 * its own channel for one with it ({@link StackLabels#personalise}).
 *
 * <p>From 1.20.2 sending is the listener every protocol phase shares
 * ({@code ServerCommonPacketListenerImpl}); only the play phase's has a player and mobs to show.
 */
//? if >=1.20.2 {
/*@Mixin(net.minecraft.server.network.ServerCommonPacketListenerImpl.class)
*///?} else {
@Mixin(ServerGamePacketListenerImpl.class)
//?}
public abstract class ServerGamePacketListenerImplMixin {

    //? if <1.20.2 {
    @Shadow public ServerPlayer player;
    //?}

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> mobstacker$personaliseStackLabel(Packet<?> packet) {
        //? if >=1.20.2 {
        /*ServerPlayer player = (Object) this instanceof ServerGamePacketListenerImpl game ? game.player : null;
        *///?}
        return player == null ? packet : StackLabels.personalise(player, packet);
    }
}
