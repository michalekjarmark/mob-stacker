package com.frikinjay.mobstacker.neoforge.mixin;

//? if <1.20.3 {
import com.frikinjay.mobstacker.neoforge.legacy.LegacyChannels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge 20.2 only: a payload on one of the mod's channels, or a peer's list of its channels
 * ({@code minecraft:register}), is read as its bare bytes, before
 * NeoForge reads it as a {@code SimplePayload} and takes a packet index off its front (see
 * {@link LegacyChannels}). What a client sends the server.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadPacketMixin {

    @Inject(method = "readPayload", at = @At("HEAD"), cancellable = true)
    private static void mobstacker$readBareBytes(ResourceLocation id, FriendlyByteBuf buf,
                                                 CallbackInfoReturnable<CustomPacketPayload> cir) {
        if (LegacyChannels.readsBare(id)) {
            cir.setReturnValue(LegacyChannels.read(id, buf));
        }
    }
}
//?}
