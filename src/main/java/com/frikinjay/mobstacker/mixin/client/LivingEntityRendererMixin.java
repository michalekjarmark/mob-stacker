package com.frikinjay.mobstacker.mixin.client;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a stack named "Dinnerbone" or "Grumm" hang upside down like a single mob does. Vanilla
 * compares the whole name to the literal, which "Dinnerbone x16" never equals.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Redirect(method = "isEntityUpsideDown",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getName()Lnet/minecraft/network/chat/Component;"))
    private static Component mobstacker$nameWithoutStackCount(LivingEntity entity) {
        return MobStacker.easterEggName(entity);
    }
}
