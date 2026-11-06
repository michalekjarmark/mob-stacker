package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * countStackMembers (1.12.0): the entities vanilla counts against the spawn caps every tick are
 * handed over with each stack repeated once per member, so the caps count mobs, not entities. A
 * stack used to count as one, which is why the original mod lowered the caps to about a third.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @ModifyArg(method = "tickChunks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"),
            index = 1)
    private Iterable<Entity> mobstacker$countStackMembers(Iterable<Entity> entities) {
        return MobStacker.countedForSpawning(entities);
    }
}
