package com.frikinjay.mobstacker.neoforge;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackLabels;
import com.frikinjay.mobstacker.neoforge.client.MobStackerNeoForgeClient;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import com.frikinjay.mobstacker.network.RawPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge's entrypoint. Like the other loaders', it only hooks the common code up: the stacking
 * itself, the commands and the per-world config all come in through mixins, which are the same on
 * every loader.
 */
@Mod(MobStacker.MOD_ID)
public final class MobStackerNeoForge {

    /** The protocol has had one shape on the wire since it began; the version is only NeoForge's handshake. */
    private static final String PROTOCOL = "1";

    public MobStackerNeoForge(IEventBus modBus) {
        MobStacker.init();
        // NeoForge's shears shear through IShearable.onSheared (the shears item and the dispenser),
        // not in mobInteract; see NeoForgeShearing.
        MobStacker.shearingOutsideMobInteract = true;

        MobStackerNetworking.init(new MobStackerNetworking.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, ResourceLocation channel) {
                return player.connection != null && player.connection.hasChannel(channel);
            }

            @Override
            public void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
                PacketDistributor.sendToPlayer(player, RawPayload.of(channel, buf));
            }
        });
        modBus.addListener(MobStackerNeoForge::registerPayloads);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MobStackerNeoForgeClient.init(modBus);
        }
    }

    /**
     * The three config-sync channels and the stack-label one, each carrying its bytes as on every other
     * loader ({@link RawPayload}). Optional both ways: a client or server without the mod - vanilla, or
     * NeoForge without it - is let in, and is simply never sent anything on them. The client's
     * handlers are only ever called on a client.
     */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional();
        registrar.playToServer(RawPayload.type(MobStackerNetworking.REQUEST), RawPayload.codec(MobStackerNetworking.REQUEST),
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MobStackerNetworking.onRequest(player.getServer(), player);
                    }
                });
        registrar.playToServer(RawPayload.type(MobStackerNetworking.EDIT), RawPayload.codec(MobStackerNetworking.EDIT),
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MobStackerNetworking.onEdit(player.getServer(), player, payload.buf());
                    }
                });
        registrar.playToClient(RawPayload.type(MobStackerNetworking.SYNC), RawPayload.codec(MobStackerNetworking.SYNC),
                (payload, context) -> MobStackerNeoForgeClient.onSync(payload));
        registrar.playToClient(RawPayload.type(StackLabels.CHANNEL), RawPayload.codec(StackLabels.CHANNEL),
                (payload, context) -> MobStackerNeoForgeClient.onLabel(payload));
    }
}
