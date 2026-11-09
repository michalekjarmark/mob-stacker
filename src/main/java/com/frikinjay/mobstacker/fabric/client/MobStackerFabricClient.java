package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.client.MobStackerClient;
import com.frikinjay.mobstacker.client.MobStackerClientNetworking;
import com.frikinjay.mobstacker.client.MobStackerRegionHud;
import com.frikinjay.mobstacker.client.MobStackerRegionPicker;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//? if <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?}
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric's client entrypoint: hooks {@link MobStackerClient} and the pieces it drives up to Fabric
 * API. Nothing here decides anything; it only says when.
 */
public final class MobStackerFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MobStackerClient.init(new MobStackerClientNetworking.Transport() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                return ClientPlayNetworking.canSend(channel);
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                //? if >=1.20.5 {
                /*ClientPlayNetworking.send(com.frikinjay.mobstacker.network.RawPayload.of(channel, buf));
                *///?} else {
                ClientPlayNetworking.send(channel, buf);
                //?}
            }
        }, net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
        //? if >=1.20.5 {
        /*// The payload types are registered by the common entrypoint (MobStackerFabric).
        ClientPlayNetworking.registerGlobalReceiver(com.frikinjay.mobstacker.network.RawPayload.type(MobStackerNetworking.SYNC),
                (payload, context) -> MobStackerClientNetworking.onSync(payload.buf()));
        ClientPlayNetworking.registerGlobalReceiver(com.frikinjay.mobstacker.network.RawPayload.type(com.frikinjay.mobstacker.StackLabels.CHANNEL),
                (payload, context) -> com.frikinjay.mobstacker.client.MobStackerClientLabels.onLabel(payload.buf()));
        *///?} else {
        ClientPlayNetworking.registerGlobalReceiver(MobStackerNetworking.SYNC,
                (client, handler, buf, responseSender) -> MobStackerClientNetworking.onSync(buf));
        ClientPlayNetworking.registerGlobalReceiver(com.frikinjay.mobstacker.StackLabels.CHANNEL,
                (client, handler, buf, responseSender) -> com.frikinjay.mobstacker.client.MobStackerClientLabels.onLabel(buf));
        //?}
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MobStackerClientNetworking.onDisconnect();
            com.frikinjay.mobstacker.client.MobStackerClientLabels.clear();
        });

        MobStackerClient.keys().forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(MobStackerClient::onEndClientTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal(MobStackerClient.CONFIG_COMMAND)
                        .executes(context -> MobStackerClient.requestConfigScreen())));

        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                com.frikinjay.mobstacker.compat.Compat.id("mobstacker", "region_line"),
                (graphics, tickDelta) -> MobStackerRegionHud.render(graphics));
        *///?} else {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> MobStackerRegionHud.render(graphics));
        //?}
        // Both fire before the block or the held item is used, so a click can be taken for picking a
        // region's corner instead.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
                MobStackerRegionPicker.onUseBlock(player, level, hitResult));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
                MobStackerRegionPicker.onUseEntity(player, level));
    }
}
