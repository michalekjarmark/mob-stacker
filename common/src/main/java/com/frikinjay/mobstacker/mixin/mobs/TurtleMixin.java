package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** multiplyGiftDrops: a stack of baby turtles leaves a scute for every turtle in it when it grows up. */
@Mixin(Turtle.class)
public abstract class TurtleMixin {

    @Inject(method = "ageBoundaryReached", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/animal/Turtle;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private void mobstacker$scuteForEveryMember(CallbackInfo ci) {
        MobStacker.dropGiftForMembers((Turtle) (Object) this, Items.SCUTE, 1);
    }
}
