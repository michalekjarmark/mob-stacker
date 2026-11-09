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

    //? if >=26.1 {
    /*@org.spongepowered.asm.mixin.injection.Redirect(method = "ageBoundaryReached", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/turtle/Turtle;dropFromGiftLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Ljava/util/function/BiConsumer;)Z"))
    private boolean mobstacker$scuteForEveryMember(Turtle self, net.minecraft.server.level.ServerLevel level,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table,
            java.util.function.BiConsumer<net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack> drop) {
        // From 26.1 the gift is a loot table (a cold chicken lays a blue egg): it is rolled once more
        // for every member, so each gets its own, as one item per member did before.
        boolean dropped = self.dropFromGiftLootTable(level, table, drop);
        for (int i = MobStacker.giftMembers(self); dropped && i > 0; i--) {
            self.dropFromGiftLootTable(level, table, drop);
        }
        return dropped;
    }
    *///?} else {
    @Inject(method = "ageBoundaryReached", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/animal/Turtle;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private void mobstacker$scuteForEveryMember(CallbackInfo ci) {
        //? if >=1.20.5 {
        /*MobStacker.dropGiftForMembers((Turtle) (Object) this, Items.TURTLE_SCUTE, 1);
        *///?} else {
        MobStacker.dropGiftForMembers((Turtle) (Object) this, Items.SCUTE, 1);
        //?}
    }
    //?}
}
