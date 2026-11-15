package com.frikinjay.mobstacker.neoforge.legacy;

//? if <1.20.3 {
import com.frikinjay.mobstacker.StackLabels;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.event.EventNetworkChannel;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * NeoForge 20.2 (Minecraft 1.20.2) only: the three config-sync channels and the stack-label one as
 * Forge's event channels, registered once, by name. Optional both ways: a client or server without the
 * mod - vanilla, or NeoForge without it - is let in, and is simply never sent anything on them.
 *
 * <p>NeoForge 20.2 reads a payload it does not know as its own {@code SimplePayload}, which takes a
 * leading VarInt off it as a packet index: that would garble what a Fabric peer sends and fail on an
 * empty payload (the config request). The two {@code CustomPayloadPacket} mixins of this loader read
 * the mod's channels as {@link LegacyPayload} instead, which NeoForge then hands to these channels with
 * its bytes untouched.
 */
public final class LegacyChannels {

    private static final Map<ResourceLocation, EventNetworkChannel> ALL = Map.of(
            MobStackerNetworking.REQUEST, channel(MobStackerNetworking.REQUEST),
            MobStackerNetworking.EDIT, channel(MobStackerNetworking.EDIT),
            MobStackerNetworking.SYNC, channel(MobStackerNetworking.SYNC),
            StackLabels.CHANNEL, channel(StackLabels.CHANNEL));

    private LegacyChannels() {
    }

    /** Whether {@code name} is one of the mod's channels (the mixins ask before they read a payload). */
    public static boolean isOurs(ResourceLocation name) {
        return ALL.containsKey(name);
    }

    /** Whether the other end of {@code connection} listens on {@code name}. */
    public static boolean isRemotePresent(ResourceLocation name, Connection connection) {
        EventNetworkChannel known = ALL.get(name);
        return known != null && known.isRemotePresent(connection);
    }

    /** What the server does with a payload a player sent on {@code name}. */
    public static void onServer(ResourceLocation name, BiConsumer<ServerPlayer, FriendlyByteBuf> handler) {
        ALL.get(name).addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context context = event.getSource();
            ServerPlayer player = context.getSender();
            if (player != null) {
                handler.accept(player, event.getPayload());
            }
            context.setPacketHandled(true);
        });
    }

    /** What the client does with a payload the server sent on {@code name}. */
    public static void onClient(ResourceLocation name, Consumer<FriendlyByteBuf> handler) {
        ALL.get(name).addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            handler.accept(event.getPayload());
            event.getSource().setPacketHandled(true);
        });
    }

    private static EventNetworkChannel channel(ResourceLocation name) {
        return NetworkRegistry.newEventChannel(name, () -> MobStackerNeoForgeLegacy.PROTOCOL,
                NetworkRegistry.acceptMissingOr(MobStackerNeoForgeLegacy.PROTOCOL),
                NetworkRegistry.acceptMissingOr(MobStackerNeoForgeLegacy.PROTOCOL));
    }
}
//?}
