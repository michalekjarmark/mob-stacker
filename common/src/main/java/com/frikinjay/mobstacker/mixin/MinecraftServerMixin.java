package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loads MobStacker's config from the world's save folder when the server is created, so
 * each world/server keeps its own settings instead of sharing one global config file.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mobstacker$loadWorldConfig(CallbackInfo ci) {
        MobStacker.loadWorldConfig((MinecraftServer) (Object) this);
    }
}
