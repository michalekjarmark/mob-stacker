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

    // The owner in the descriptor is the class where it lives: its own package from 1.21.11.
    //? if >=1.21.11 {
    /*@org.spongepowered.asm.mixin.injection.Redirect(method = "ageBoundaryReached", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/turtle/Turtle;dropFromGiftLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Ljava/util/function/BiConsumer;)Z"))
    *///?} else if >=1.21.9 {
    /*@org.spongepowered.asm.mixin.injection.Redirect(method = "ageBoundaryReached", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/Turtle;dropFromGiftLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Ljava/util/function/BiConsumer;)Z"))
    *///?}
    //? if >=1.21.9 {
    /*private boolean mobstacker$scuteForEveryMember(Turtle self, net.minecraft.server.level.ServerLevel level,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table,
            java.util.function.BiConsumer<net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack> drop) {
        // From 1.21.9 the gift is a loot table, as the chicken's egg from 1.21.5 (1.21.8 still drops the scute
        // itself): it is rolled once more for every member, so each gets its own, as one item per member did before.
        boolean dropped = self.dropFromGiftLootTable(level, table, drop);
        for (int i = MobStacker.giftMembers(self); dropped && i > 0; i--) {
            self.dropFromGiftLootTable(level, table, drop);
        }
        return dropped;
    }
    *///?} else {
    // From 1.21.2 an entity drops into a ServerLevel it is given.
    //? if >=1.21.2 {
    /*@Inject(method = "ageBoundaryReached", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/animal/Turtle;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/entity/item/ItemEntity;"))
    *///?} else {
    @Inject(method = "ageBoundaryReached", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/animal/Turtle;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/entity/item/ItemEntity;"))
    //?}
    private void mobstacker$scuteForEveryMember(CallbackInfo ci) {
        //? if >=1.20.5 {
        /*MobStacker.dropGiftForMembers((Turtle) (Object) this, Items.TURTLE_SCUTE, 1);
        *///?} else {
        MobStacker.dropGiftForMembers((Turtle) (Object) this, Items.SCUTE, 1);
        //?}
    }
    //?}
}
