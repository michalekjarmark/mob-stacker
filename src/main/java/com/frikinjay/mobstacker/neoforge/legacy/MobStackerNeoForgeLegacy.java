package com.frikinjay.mobstacker.neoforge.legacy;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.neoforge.legacy.client.MobStackerNeoForgeLegacyClient;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import com.frikinjay.mobstacker.network.RawPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge's entrypoint on Minecraft 1.20.2 to 1.20.4 (NeoForge 20.2 to 20.4), where NeoForge was
 * still half Forge: the build compiles this package instead of {@code MobStackerNeoForge} and its
 * client, which are for NeoForge 20.5 and newer. Like every loader's, it only hooks the common code up.
 *
 * <p>The two versions talk to the network differently. 20.2 has Forge's event channels
 * ({@link LegacyChannels}); 20.4 has the payload registrar, with handlers that take a context. Either
 * way what goes over the wire is the channel's bytes ({@link RawPayload}), as on every other loader.
 */
@Mod(MobStacker.MOD_ID)
public final class MobStackerNeoForgeLegacy {

    /** The protocol has had one shape on the wire since it began; the version is only NeoForge's handshake. */
    static final String PROTOCOL = "1";

    public MobStackerNeoForgeLegacy(IEventBus modBus) {
        MobStacker.init();
        // NeoForge's shears shear through IShearable.onSheared, not in mobInteract; see NeoForgeShearing.
        MobStacker.shearingOutsideMobInteract = mob -> true;

        MobStackerNetworking.init(new MobStackerNetworking.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, ResourceLocation channel) {
                //? if >=1.20.3 {
                /*return player.connection != null && player.connection.isConnected(channel);
                *///?} else {
                return player.connection != null && LegacyChannels.isRemotePresent(channel, player.connection.connection);
                //?}
            }

            @Override
            public void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
                //? if >=1.20.3 {
                /*net.neoforged.neoforge.network.PacketDistributor.PLAYER.with(player).send(RawPayload.of(channel, buf));
                *///?} else {
                player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                        RawPayload.of(channel, buf)));
                //?}
            }
        });
        //? if >=1.20.3 {
        /*modBus.addListener(MobStackerNeoForgeLegacy::registerPayloads);
        *///?} else {
        LegacyChannels.onServer(MobStackerNetworking.REQUEST, (player, buf) ->
                MobStackerNetworking.onRequest(player.getServer(), player));
        LegacyChannels.onServer(MobStackerNetworking.EDIT, (player, buf) ->
                MobStackerNetworking.onEdit(player.getServer(), player, buf));
        //?}

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MobStackerNeoForgeLegacyClient.init(modBus);
        }
    }

    //? if >=1.20.3 {
    /*/^*
     * The three config-sync channels and the stack-label one. Optional both ways: a client or server
     * without the mod - vanilla, or NeoForge without it - is let in, and is simply never sent anything
     * on them. The client's handlers are only ever called on a client.
     ^/
    private static void registerPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent event) {
        net.neoforged.neoforge.network.registration.IPayloadRegistrar registrar =
                event.registrar(MobStacker.MOD_ID).versioned(PROTOCOL).optional();
        registrar.play(MobStackerNetworking.REQUEST, buf -> RawPayload.read(MobStackerNetworking.REQUEST, buf),
                handlers -> handlers.server((payload, context) -> {
                    if (context.player().orElse(null) instanceof ServerPlayer player) {
                        MobStackerNetworking.onRequest(player.getServer(), player);
                    }
                }));
        registrar.play(MobStackerNetworking.EDIT, buf -> RawPayload.read(MobStackerNetworking.EDIT, buf),
                handlers -> handlers.server((payload, context) -> {
                    if (context.player().orElse(null) instanceof ServerPlayer player) {
                        MobStackerNetworking.onEdit(player.getServer(), player, payload.buf());
                    }
                }));
        registrar.play(MobStackerNetworking.SYNC, buf -> RawPayload.read(MobStackerNetworking.SYNC, buf),
                handlers -> handlers.client((payload, context) -> MobStackerNeoForgeLegacyClient.onSync(payload)));
        registrar.play(com.frikinjay.mobstacker.StackLabels.CHANNEL,
                buf -> RawPayload.read(com.frikinjay.mobstacker.StackLabels.CHANNEL, buf),
                handlers -> handlers.client((payload, context) -> MobStackerNeoForgeLegacyClient.onLabel(payload)));
    }
    *///?}
}
