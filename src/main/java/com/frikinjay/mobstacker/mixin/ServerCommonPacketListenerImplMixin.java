package com.frikinjay.mobstacker.mixin;

//? if >=1.21.6 {
/*import com.frikinjay.mobstacker.command.MobStackerCommands;
import com.frikinjay.mobstacker.compat.Compat;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * A command clicked in one of the mod's chat panels, from 1.21.6 (unconfirmed), where the client asks
 * before it runs a clicked command that needs a permission - every command of the panels - so the
 * panels send a custom click action instead ({@link Compat#runCommand}). Taken here on the server
 * thread (after the packet moved there), for players only.
 ^/
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "handleCustomClickAction", cancellable = true, at = @At(value = "INVOKE",
            target = Compat.HANDLE_CUSTOM_CLICK_ACTION))
    private void mobstacker$clickedCommand(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game && packet.id().equals(Compat.CLICKED_COMMAND)) {
            packet.payload().flatMap(Tag::asString).ifPresent(command -> MobStackerCommands.runClicked(game.player, command));
            ci.cancel();
        }
    }
}
*///?}
