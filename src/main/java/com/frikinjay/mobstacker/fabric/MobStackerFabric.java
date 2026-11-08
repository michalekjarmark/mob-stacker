package com.frikinjay.mobstacker.fabric;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
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
        MobStackerNetworking.init(new MobStackerNetworking.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, ResourceLocation channel) {
                return ServerPlayNetworking.canSend(player, channel);
            }

            @Override
            public void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
                ServerPlayNetworking.send(player, channel, buf);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MobStackerNetworking.REQUEST,
                (server, player, handler, buf, responseSender) -> MobStackerNetworking.onRequest(server, player));
        ServerPlayNetworking.registerGlobalReceiver(MobStackerNetworking.EDIT,
                (server, player, handler, buf, responseSender) -> MobStackerNetworking.onEdit(server, player, buf));
    }
}
