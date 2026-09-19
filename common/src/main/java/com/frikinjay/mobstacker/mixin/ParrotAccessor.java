package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Reaches vanilla's private list of what tames a parrot.
 *
 * <p>{@code Parrot.isFood} answers false for everything — parrots do not breed — so the seeds that
 * tame one are only named in a private static set. Asking vanilla rather than repeating the four
 * items here means a mod that adds to that set is followed for free.
 */
@Mixin(Parrot.class)
public interface ParrotAccessor {

    @Accessor("TAME_FOOD")
    static Set<Item> mobstacker$tameFood() {
        throw new AssertionError("mixin accessor was not applied");
    }
}
