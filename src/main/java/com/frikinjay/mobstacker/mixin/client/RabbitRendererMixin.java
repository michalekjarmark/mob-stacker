package com.frikinjay.mobstacker.mixin.client;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Rabbit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a stack of rabbits named "Toast" keep the memorial skin.
 */
@Mixin(RabbitRenderer.class)
public class RabbitRendererMixin {

    @Redirect(method = "getTextureLocation(Lnet/minecraft/world/entity/animal/Rabbit;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/Rabbit;getName()Lnet/minecraft/network/chat/Component;"))
    private Component mobstacker$nameWithoutStackCount(Rabbit rabbit) {
        return MobStacker.easterEggName(rabbit);
    }
}
