package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.animal.Ocelot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches {@code Ocelot.isTrusting}, which vanilla leaves package-private.
 *
 * <p>An ocelot is never tamed and has no owner, so nothing in {@code OwnableEntity} or
 * {@code TamableAnimal} sees the fish a player spent earning its trust.
 */
@Mixin(Ocelot.class)
public interface OcelotAccessor {

    @Invoker("isTrusting")
    boolean mobstacker$isTrusting();
}
