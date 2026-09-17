package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cleans up stray kill holograms.
 * <p>
 * Holograms are no longer saved to disk (see {@code EntityMixin#shouldBeSaved}), but worlds that
 * ran an earlier version can still contain ones that were written out before that fix and now have
 * nothing to remove them. Any armor stand carrying our tag that is not in the live tracking list
 * therefore belongs to a session that is already over, and is discarded the moment it ticks — which
 * happens as soon as its chunk loads, so old worlds clean themselves up without a manual command.
 * <p>
 * The versions that actually leaked holograms did not tag them at all, though, so the tag alone
 * never reached the ones already stuck in a world. Untagged stands are therefore matched on their
 * shape instead ({@link MobStacker#isLegacyKillHologram}), and only on their very first tick, so
 * the check costs a boolean for every other armor stand in the game.
 */
@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void mobstacker$discardStrayKillHologram(CallbackInfo ci) {
        ArmorStand self = (ArmorStand) (Object) this;
        if (self.level().isClientSide() || self.isRemoved()) {
            return;
        }
        if (self.getTags().contains(MobStacker.KILL_HOLOGRAM_TAG)) {
            if (!MobStacker.isTrackedKillHologram(self)) {
                MobStacker.discardStrayKillHologram(self);
            }
            return;
        }
        // tickCount is still 0 only on the tick a stand enters the world, i.e. when its chunk loads.
        if (self.tickCount == 0 && MobStacker.isLegacyKillHologram(self)) {
            MobStacker.discardStrayKillHologram(self);
        }
    }
}
