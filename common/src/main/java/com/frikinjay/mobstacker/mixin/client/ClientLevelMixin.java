package com.frikinjay.mobstacker.mixin.client;

import com.frikinjay.mobstacker.client.MobStackerClientLabels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob that leaves this game's world takes its stack label with it. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void mobstacker$forgetStackLabel(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        MobStackerClientLabels.forget(id);
    }
}
