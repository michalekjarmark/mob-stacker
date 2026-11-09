package com.frikinjay.mobstacker.neoforge.client;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.client.MobStackerClient;
import com.frikinjay.mobstacker.client.MobStackerClientLabels;
import com.frikinjay.mobstacker.client.MobStackerClientNetworking;
import com.frikinjay.mobstacker.client.MobStackerRegionHud;
import com.frikinjay.mobstacker.client.MobStackerRegionPicker;
import com.frikinjay.mobstacker.compat.Compat;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge's client side: hooks {@link MobStackerClient} and the pieces it drives up to NeoForge's
 * events. Nothing here decides anything; it only says when. Loaded only on a client.
 */
public final class MobStackerNeoForgeClient {

    private MobStackerNeoForgeClient() {
    }

    public static void init(IEventBus modBus) {
        MobStackerClient.init(new MobStackerClientNetworking.Transport() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                return connection != null && connection.hasChannel(channel);
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                if (Minecraft.getInstance().getConnection() != null) {
                    PacketDistributor.sendToServer(RawPayload.of(channel, buf));
                }
            }
        });

        modBus.addListener((RegisterKeyMappingsEvent event) -> MobStackerClient.keys().forEach(event::register));
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Compat.id(MobStacker.MOD_ID, "region_hud"), (graphics, deltaTracker) -> MobStackerRegionHud.render(graphics)));

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener((ClientTickEvent.Post event) -> MobStackerClient.onEndClientTick(Minecraft.getInstance()));
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            MobStackerClientNetworking.onDisconnect();
            MobStackerClientLabels.clear();
        });
        gameBus.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal(MobStackerClient.CONFIG_COMMAND).executes(context -> MobStackerClient.requestConfigScreen())));
        // Picking a region's corner takes the click before anything else sees it. The right-click
        // events come too late for that: by the time one is cancelled the game has already told the
        // server to use the block, and the corner would get a torch put on it (as on Forge).
        gameBus.addListener(MobStackerNeoForgeClient::onUseKey);
    }

    /** The server's config snapshot (MobStackerNeoForge registers the channel). */
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
