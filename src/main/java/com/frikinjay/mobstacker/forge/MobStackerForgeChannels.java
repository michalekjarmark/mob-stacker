package com.frikinjay.mobstacker.forge;

import com.frikinjay.mobstacker.network.MobStackerNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

import java.util.Map;

/**
 * The three config-sync channels and the stack-label one, registered with Forge once, by name. Optional both ways: a client or
 * server without the mod - vanilla, or Forge without it - is let in, and is simply never sent anything
 * on them. What goes over them is plain {@code FriendlyByteBuf}s in the same shape as on Fabric.
 */
public final class MobStackerForgeChannels {

    /** The protocol has had one shape on the wire since it began; the version is only Forge's handshake. */
    private static final String PROTOCOL = "1";

    private static final Map<ResourceLocation, EventNetworkChannel> ALL = Map.of(
            MobStackerNetworking.REQUEST, channel(MobStackerNetworking.REQUEST),
            MobStackerNetworking.EDIT, channel(MobStackerNetworking.EDIT),
            MobStackerNetworking.SYNC, channel(MobStackerNetworking.SYNC),
            com.frikinjay.mobstacker.StackLabels.CHANNEL, channel(com.frikinjay.mobstacker.StackLabels.CHANNEL));

    private MobStackerForgeChannels() {
    }

    public static Map<ResourceLocation, EventNetworkChannel> all() {
        return ALL;
    }

    private static EventNetworkChannel channel(ResourceLocation name) {
        return NetworkRegistry.ChannelBuilder.named(name)
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .eventNetworkChannel();
    }
}
