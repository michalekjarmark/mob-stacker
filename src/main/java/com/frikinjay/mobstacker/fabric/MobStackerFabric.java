package com.frikinjay.mobstacker.fabric;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
//? if >=1.20.5 {
/*import com.frikinjay.mobstacker.StackLabels;
import com.frikinjay.mobstacker.network.RawPayload;
*///?}
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class MobStackerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        MobStacker.init();

        // Server side of the config-sync protocol (safe on a dedicated server: no client code).
        // The channels are optional: Fabric only delivers our packets to clients that registered
        // these receivers, so vanilla clients are never affected.
        //? if >=1.20.5 {
        /*// From 1.20.5 every channel's payload type is registered, on both sides, before anything is sent.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                RawPayload.type(MobStackerNetworking.REQUEST), RawPayload.codec(MobStackerNetworking.REQUEST));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                RawPayload.type(MobStackerNetworking.EDIT), RawPayload.codec(MobStackerNetworking.EDIT));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
                RawPayload.type(MobStackerNetworking.SYNC), RawPayload.codec(MobStackerNetworking.SYNC));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
                RawPayload.type(StackLabels.CHANNEL), RawPayload.codec(StackLabels.CHANNEL));
        *///?}
        MobStackerNetworking.init(new MobStackerNetworking.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, ResourceLocation channel) {
                return ServerPlayNetworking.canSend(player, channel);
            }

            @Override
            public void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
                //? if >=1.20.5 {
                /*ServerPlayNetworking.send(player, RawPayload.of(channel, buf));
                *///?} else {
                ServerPlayNetworking.send(player, channel, buf);
                //?}
            }
        });
        //? if >=1.20.5 {
        /*ServerPlayNetworking.registerGlobalReceiver(RawPayload.type(MobStackerNetworking.REQUEST),
                (payload, context) -> MobStackerNetworking.onRequest(context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(RawPayload.type(MobStackerNetworking.EDIT),
                (payload, context) -> MobStackerNetworking.onEdit(context.server(), context.player(), payload.buf()));
        *///?} else {
        ServerPlayNetworking.registerGlobalReceiver(MobStackerNetworking.REQUEST,
                (server, player, handler, buf, responseSender) -> MobStackerNetworking.onRequest(server, player));
        ServerPlayNetworking.registerGlobalReceiver(MobStackerNetworking.EDIT,
                (server, player, handler, buf, responseSender) -> MobStackerNetworking.onEdit(server, player, buf));
        //?}
    }
}
