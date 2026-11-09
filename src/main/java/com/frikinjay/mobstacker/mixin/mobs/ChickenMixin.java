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

    //? if >=26.1 {
    /*@org.spongepowered.asm.mixin.injection.Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/chicken/Chicken;dropFromGiftLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Ljava/util/function/BiConsumer;)Z"))
    private boolean mobstacker$eggForEveryMember(Chicken self, net.minecraft.server.level.ServerLevel level,
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
    @Inject(method = "aiStep", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/animal/Chicken;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private void mobstacker$eggForEveryMember(CallbackInfo ci) {
        MobStacker.dropGiftForMembers((Chicken) (Object) this, Items.EGG, 1);
    }
    //?}
}
