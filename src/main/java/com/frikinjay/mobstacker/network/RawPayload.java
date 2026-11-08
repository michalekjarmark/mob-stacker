package com.frikinjay.mobstacker.network;

//? if >=1.20.5 {
/*import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/^*
 * From 1.20.5 a mod's packet is a typed payload with a codec, not a channel name and a buffer. This
 * one payload carries the bytes a channel always carried, unchanged, so {@link MobStackerNetworking},
 * the client and the label packets keep writing and reading {@link FriendlyByteBuf}s as on 1.20.1,
 * and what goes over the wire is the same: the payload's body is exactly those bytes.
 *
 * <p>A loader registers {@link #codec} for each channel and wraps / unwraps with {@link #of} and
 * {@link #buf}.
 ^/
public record RawPayload(Type<RawPayload> type, byte[] bytes) implements CustomPacketPayload {
    private static final Map<ResourceLocation, Type<RawPayload>> TYPES = new ConcurrentHashMap<>();

    /^* The payload type of a channel; the same object every time. ^/
    public static Type<RawPayload> type(ResourceLocation channel) {
        return TYPES.computeIfAbsent(channel, Type::new);
    }

    /^* Reads and writes a channel's payload as its bare bytes. ^/
    public static StreamCodec<FriendlyByteBuf, RawPayload> codec(ResourceLocation channel) {
        Type<RawPayload> type = type(channel);
        return StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.bytes()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new RawPayload(type, bytes);
                });
    }

    /^* What is left to read in {@code buf}, as a payload on {@code channel}. ^/
    public static RawPayload of(ResourceLocation channel, FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return new RawPayload(type(channel), bytes);
    }

    /^* The bytes, to read as before. ^/
    public FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
    }
}
*///?}
