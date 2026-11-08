package com.frikinjay.mobstacker.mixin.client;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.client.renderer.entity.layers.SheepFurLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a stack of sheep named "jeb_" cycle through the dye colours. Same story as Dinnerbone: the
 * vanilla check is an exact string comparison that the stack's count breaks.
 */
@Mixin(SheepFurLayer.class)
public class SheepFurLayerMixin {

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/Sheep;getName()Lnet/minecraft/network/chat/Component;"))
    private Component mobstacker$nameWithoutStackCount(Sheep sheep) {
        return MobStacker.easterEggName(sheep);
    }
}
