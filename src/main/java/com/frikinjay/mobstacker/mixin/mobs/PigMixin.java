package com.frikinjay.mobstacker.mixin.mobs;

//? if <26.1 {
import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Pig.class)
public class PigMixin {

    @Inject(method = "thunderHit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/ZombifiedPiglin;setPersistenceRequired()V"),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void mobstacker$thunderHit(ServerLevel serverLevel, LightningBolt lightningBolt, CallbackInfo ci, ZombifiedPiglin zombifiedPiglin) {
        if (zombifiedPiglin != null) {
            MobStacker.carryStackInto((Pig) (Object) this, zombifiedPiglin);
        }
    }

}
//?}
