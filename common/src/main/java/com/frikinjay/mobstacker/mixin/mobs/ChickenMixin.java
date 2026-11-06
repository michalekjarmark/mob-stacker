package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** multiplyGiftDrops: a stack of chickens lays an egg for every chicken in it. */
@Mixin(Chicken.class)
public abstract class ChickenMixin {

    @Inject(method = "aiStep", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/animal/Chicken;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private void mobstacker$eggForEveryMember(CallbackInfo ci) {
        MobStacker.dropGiftForMembers((Chicken) (Object) this, Items.EGG, 1);
    }
}
