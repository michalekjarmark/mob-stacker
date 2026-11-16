package com.frikinjay.mobstacker.neoforge.mixin;

//? if <1.20.3 {
import com.frikinjay.mobstacker.neoforge.legacy.LegacyPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.neoforged.neoforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 20.2 only: a payload a player sent on one of the mod's channels goes to that channel. NeoForge
 * hands its event channels only its own {@code SimplePayload}, and the mod's payloads are
 * {@link LegacyPayload}s: read so by {@code ServerboundCustomPayloadPacketMixin}, or, from the client of
 * an integrated server, the object that client sent.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class LegacyServerPayloadMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void mobstacker$toLegacyChannel(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof LegacyPayload payload
                && NetworkHooks.onCustomPayload(packet, payload, ((ServerCommonPacketListenerImpl) (Object) this).connection)) {
            ci.cancel();
        }
    }
}
//?}
