package com.frikinjay.mobstacker.forge.client;

import com.frikinjay.mobstacker.client.MobStackerClient;
import com.frikinjay.mobstacker.client.MobStackerClientNetworking;
import com.frikinjay.mobstacker.client.MobStackerRegionHud;
import com.frikinjay.mobstacker.client.MobStackerRegionPicker;
import com.frikinjay.mobstacker.forge.MobStackerForgeChannels;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.event.EventNetworkChannel;

import java.util.Map;

/**
 * Forge's client side: hooks {@link MobStackerClient} and the pieces it drives up to Forge's events.
 * Nothing here decides anything; it only says when. Loaded only on a client.
 */
public final class MobStackerForgeClient {

    private MobStackerForgeClient() {
    }

    public static void init(IEventBus modBus) {
        Map<ResourceLocation, EventNetworkChannel> channels = MobStackerForgeChannels.all();
        MobStackerClient.init(new MobStackerClientNetworking.Transport() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                EventNetworkChannel known = channels.get(channel);
                return connection != null && known != null && known.isRemotePresent(connection.getConnection());
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.send(new ServerboundCustomPayloadPacket(channel, buf));
                }
            }
        });
        channels.get(MobStackerNetworking.SYNC).addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            MobStackerClientNetworking.onSync(event.getPayload());
            event.getSource().get().setPacketHandled(true);
        });

        modBus.addListener((RegisterKeyMappingsEvent event) -> MobStackerClient.keys().forEach(event::register));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("region_hud",
                (gui, graphics, partialTick, width, height) -> MobStackerRegionHud.render(graphics)));

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                MobStackerClient.onEndClientTick(Minecraft.getInstance());
            }
        });
        forgeBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> MobStackerClientNetworking.onDisconnect());
        forgeBus.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal(MobStackerClient.CONFIG_COMMAND).executes(context -> MobStackerClient.requestConfigScreen())));
        // Picking a region's corner takes the click before anything else sees it. Forge's own
        // right-click events come too late for that: by the time one is cancelled the game has already
        // told the server to use the block, and the corner would get a torch put on it.
        forgeBus.addListener(MobStackerForgeClient::onUseKey);
    }

    private static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        HitResult hit = client.hitResult;
        if (client.player == null || client.level == null || hit == null) {
            return;
        }
        InteractionResult result = InteractionResult.PASS;
        if (hit.getType() == HitResult.Type.BLOCK) {
            result = MobStackerRegionPicker.onUseBlock(client.player, client.level, (BlockHitResult) hit);
        } else if (hit.getType() == HitResult.Type.ENTITY) {
            result = MobStackerRegionPicker.onUseEntity(client.player, client.level);
        }
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
