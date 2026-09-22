package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MobBucketItem.class)
public class MobBucketItemMixin {

    /**
     * Holds a mob poured out of a bucket out of the stacks for a while - see
     * {@link MobStacker#markPouredFromBucket}. Marked here, between being added to the level and its
     * first tick, which is the tick {@code stackOnSpawn} would otherwise have merged it on.
     */
    @Redirect(method = "spawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;"))
    private Entity mobstacker$holdPouredMobOut(EntityType<?> type, ServerLevel level, ItemStack stack,
                                               Player player, BlockPos pos, MobSpawnType spawnType,
                                               boolean alignPosition, boolean invertY) {
        Entity entity = type.spawn(level, stack, player, pos, spawnType, alignPosition, invertY);
        if (entity instanceof Mob mob) {
            MobStacker.markPouredFromBucket(mob);
        }
        return entity;
    }
}
