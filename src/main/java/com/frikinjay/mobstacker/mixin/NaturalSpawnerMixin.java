package com.frikinjay.mobstacker.mixin;

//? if >=26.1 {
/*import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/^*
 * countStackMembers from 26.1, where {@code NaturalSpawner.createState} takes the level and walks its
 * entities itself: the same walk, with a stack repeated once per member (see ServerChunkCacheMixin).
 ^/
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Redirect(method = "createState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getAllEntities()Ljava/lang/Iterable;"))
    private static Iterable<Entity> mobstacker$countStackMembers(ServerLevel level) {
        return MobStacker.countedForSpawning(level.getAllEntities());
    }
}
*///?}
