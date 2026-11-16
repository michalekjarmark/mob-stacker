package com.frikinjay.mobstacker.neoforge.mixin.client;

//? if <1.20.3 {
import com.frikinjay.mobstacker.neoforge.legacy.LegacyPayload;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 20.2 only: a payload the server sent on one of the mod's channels goes to that channel.
 * NeoForge hands its event channels only its own {@code SimplePayload}, and the mod's payloads are
 * {@link LegacyPayload}s: read so by {@code ClientboundCustomPayloadPacketMixin}, or, from an integrated
 * server, the object it sent. {@code handleUnknownCustomPayload} is NeoForge's, so it is not remapped.
 */
@Mixin(ClientPacketListener.class)
public abstract class LegacyClientPayloadMixin {
    @Inject(method = "handleUnknownCustomPayload", at = @At("HEAD"), cancellable = true, remap = false)
    private void mobstacker$toLegacyChannel(ClientboundCustomPayloadPacket packet, CustomPacketPayload payload,
                                            CallbackInfo ci) {
        if (payload instanceof LegacyPayload legacy
                && NetworkHooks.onCustomPayload(packet, legacy, ((ClientPacketListener) (Object) this).getConnection())) {
            ci.cancel();
        }
    }
}
//?}
