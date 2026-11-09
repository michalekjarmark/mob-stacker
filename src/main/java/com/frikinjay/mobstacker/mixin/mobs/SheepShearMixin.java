package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.compat.Compat;
import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * What one sheep under the top of a stack gives when the stack is sheared - the part that is the same
 * on every loader. When it happens is each loader's: Fabric shears in {@code mobInteract} as vanilla
 * does, Forge in {@code IForgeShearable.onSheared}, and a dispenser in {@code Shearable.shear} on both
 * ({@code ShearsDispenseItemBehaviorMixin}).
 */
@Mixin(Sheep.class)
public class SheepShearMixin implements StackShearable {
    /**
     * True while the loop below shears the members under the top sheep. {@code Sheep.shear} plays
     * the snip every time it runs, and all of them landed on the same tick at the same spot - so a
     * stack of sixty-four was sixty-four snips at once, which the last test round found at the cost
     * of their ears. Vanilla's own shear of the top sheep still makes the one sound a click should.
     */
    @Unique
    private boolean mobstacker$shearingQuietly;

    /**
     * Shears the members under the top sheep, quietly. A player's shears and a dispenser's both. Each
     * member is the top sheep sheared once more, so it is made woolly again first: from 1.21.5 the
     * wool comes from the shearing loot table, which gives nothing for a sheep already sheared (and
     * vanilla has just sheared the top one).
     */
    @Override
    public void mobstacker$shearMembers(SoundSource source, int count) {
        Sheep self = (Sheep) (Object) this;
        mobstacker$shearingQuietly = true;
        try {
            for (int i = 0; i < count; i++) {
                self.setSheared(false);
                Compat.shear(self, source);
            }
        } finally {
            mobstacker$shearingQuietly = false;
        }
    }

    //? if >=26.1 {
    /*@Redirect(method = "shear", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void mobstacker$oneSnipPerClick(net.minecraft.server.level.ServerLevel level, Entity player, Entity entity, SoundEvent sound,
                                            SoundSource source, float volume, float pitch) {
        if (!mobstacker$shearingQuietly) {
            level.playSound(player, entity, sound, source, volume, pitch);
        }
    }
    *///?} else {
    @Redirect(method = "shear", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void mobstacker$oneSnipPerClick(Level level, Player player, Entity entity, SoundEvent sound,
                                            SoundSource source, float volume, float pitch) {
        if (!mobstacker$shearingQuietly) {
            level.playSound(player, entity, sound, source, volume, pitch);
        }
    }
    //?}
}
