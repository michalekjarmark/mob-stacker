package com.frikinjay.mobstacker.neoforge.legacy.client;

import com.frikinjay.mobstacker.client.MobStackerClient;
import com.frikinjay.mobstacker.client.MobStackerClientLabels;
import com.frikinjay.mobstacker.client.MobStackerClientNetworking;
import com.frikinjay.mobstacker.client.MobStackerRegionHud;
import com.frikinjay.mobstacker.client.MobStackerRegionPicker;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import com.frikinjay.mobstacker.network.RawPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/**
 * NeoForge's client side on Minecraft 1.20.2 to 1.20.4: hooks {@link MobStackerClient} and the pieces
 * it drives up to NeoForge's events, which there still have Forge's names (one tick event with a
 * phase, GUI overlays). Nothing here decides anything; it only says when. Loaded only on a client.
 */
public final class MobStackerNeoForgeLegacyClient {

    private MobStackerNeoForgeLegacyClient() {
    }

    public static void init(IEventBus modBus) {
        MobStackerClient.init(new MobStackerClientNetworking.Transport() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                //? if >=1.20.3 {
                /*return connection != null && connection.isConnected(channel);
                *///?} else {
                return connection != null && com.frikinjay.mobstacker.neoforge.legacy.LegacyChannels
                        .isRemotePresent(channel, connection.getConnection());
                //?}
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    //? if >=1.20.3 {
                    /*net.neoforged.neoforge.network.PacketDistributor.SERVER.noArg().send(RawPayload.of(channel, buf));
                    *///?} else {
                    connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                            com.frikinjay.mobstacker.neoforge.legacy.LegacyPayload.of(channel, buf)));
                    //?}
                }
            }
        }, net.neoforged.fml.loading.FMLPaths.GAMEDIR.get());
        //? if <1.20.3 {
        com.frikinjay.mobstacker.neoforge.legacy.LegacyChannels.onClient(MobStackerNetworking.SYNC,
                MobStackerClientNetworking::onSync);
        com.frikinjay.mobstacker.neoforge.legacy.LegacyChannels.onClient(com.frikinjay.mobstacker.StackLabels.CHANNEL,
                MobStackerClientLabels::onLabel);
        //?}

        modBus.addListener((RegisterKeyMappingsEvent event) -> MobStackerClient.keys().forEach(event::register));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("region_hud",
                (gui, graphics, partialTick, width, height) -> MobStackerRegionHud.render(graphics)));

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                MobStackerClient.onEndClientTick(Minecraft.getInstance());
            }
        });
        //? if >=1.20.3 {
        /*// A Fabric server sees the mod's channels only when they are named to it; see MobStackerNeoForgeClient.nameChannels.
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            ClientPacketListener connection = event.getPlayer().connection;
            if (connection.getConnectionType() != net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE) {
                connection.send(new net.neoforged.neoforge.network.payload.MinecraftRegisterPayload(java.util.Set.of(
                        com.frikinjay.mobstacker.network.MobStackerNetworking.SYNC, com.frikinjay.mobstacker.StackLabels.CHANNEL)));
            }
        });
        *///?}
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            MobStackerClientNetworking.onDisconnect();
            MobStackerClientLabels.clear();
        });
        gameBus.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal(MobStackerClient.CONFIG_COMMAND).executes(context -> MobStackerClient.requestConfigScreen())));
        // Picking a region's corner takes the click before anything else sees it. The right-click
        // events come too late for that: by the time one is cancelled the game has already told the
        // server to use the block, and the corner would get a torch put on it (as on Forge).
        gameBus.addListener(MobStackerNeoForgeLegacyClient::onUseKey);
    }

    /** The server's config snapshot (20.4: MobStackerNeoForgeLegacy registers the channel). */
    public static void onSync(RawPayload payload) {
        MobStackerClientNetworking.onSync(payload.buf());
    }

    /** One mob's stack label. */
    public static void onLabel(RawPayload payload) {
        MobStackerClientLabels.onLabel(payload.buf());
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
