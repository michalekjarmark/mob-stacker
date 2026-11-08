package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link ArmorStand}'s private {@code setMarker} so the kill-feedback hologram can be a
 * true marker armor stand (no hitbox, no collision) instead of a solid invisible one.
 */
@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {
    @Invoker("setMarker")
    void mobstacker$setMarker(boolean marker);
}
