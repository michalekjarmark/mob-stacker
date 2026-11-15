package com.frikinjay.mobstacker.neoforge.legacy;

//? if <1.20.3 {
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.ICustomPacketPayloadWithBuffer;

/**
 * NeoForge 20.2 only: a payload received on one of the mod's channels, as its bare bytes. It is the
 * kind of payload NeoForge hands to an event channel ({@code ICustomPacketPayloadWithBuffer}), read by
 * the mod instead of NeoForge so that nothing is taken off its front (see {@link LegacyChannels}).
 * Sending needs none of this: {@code RawPayload} writes the same bytes.
 */
public record LegacyPayload(ResourceLocation id, byte[] bytes) implements ICustomPacketPayloadWithBuffer {

    /** Everything left in {@code buf}, as a payload on {@code channel}. */
    public static LegacyPayload read(ResourceLocation channel, FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new LegacyPayload(channel, bytes);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(bytes);
    }

    /** The bytes, for the channel's listener to read. */
    @Override
    public FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }

    /** Forge's index of a message within a channel: the mod's channels carry one kind each. */
    @Override
    public int packetIndex() {
        return 0;
    }
}
//?}
