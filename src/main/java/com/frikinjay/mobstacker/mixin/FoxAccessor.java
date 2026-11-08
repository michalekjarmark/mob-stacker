package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.UUID;

/**
 * Reaches {@code Fox.getTrustedUUIDs}, which vanilla leaves package-private.
 *
 * <p>A fox bred in captivity trusts the players who bred it, by UUID. Like an ocelot's trust that is
 * real player investment with no owner field to notice it by.
 */
@Mixin(Fox.class)
public interface FoxAccessor {

    @Invoker("getTrustedUUIDs")
    List<UUID> mobstacker$trustedUUIDs();
}
