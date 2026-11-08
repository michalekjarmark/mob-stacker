package com.frikinjay.mobstacker.forge;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.forge.client.MobStackerForgeClient;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.event.EventNetworkChannel;

import java.util.Map;

/**
 * Forge's entrypoint. Like Fabric's, it only hooks the common code up: the stacking itself, the
 * commands and the per-world config all come in through mixins, which are the same on both loaders.
 */
@Mod(MobStacker.MOD_ID)
public final class MobStackerForge {

    public MobStackerForge() {
        MobStacker.init();
        // Forge's shears shear in IForgeShearable.onSheared, not in mobInteract; see ForgeShearing.
        MobStacker.shearingOutsideMobInteract = true;

        Map<ResourceLocation, EventNetworkChannel> channels = MobStackerForgeChannels.all();
        MobStackerNetworking.init(new MobStackerNetworking.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, ResourceLocation channel) {
                EventNetworkChannel known = channels.get(channel);
                return known != null && known.isRemotePresent(player.connection.connection);
            }

            @Override
            public void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
                player.connection.send(new ClientboundCustomPayloadPacket(channel, buf));
            }
        });
        channels.get(MobStackerNetworking.REQUEST).addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                MobStackerNetworking.onRequest(player.getServer(), player);
            }
            context.setPacketHandled(true);
        });
        channels.get(MobStackerNetworking.EDIT).addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource().get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                MobStackerNetworking.onEdit(player.getServer(), player, event.getPayload());
            }
            context.setPacketHandled(true);
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MobStackerForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
