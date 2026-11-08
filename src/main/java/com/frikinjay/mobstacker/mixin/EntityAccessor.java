package com.frikinjay.mobstacker.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/** The two pieces of entity data a mob's name travels in, for {@code StackLabels}. */
@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("DATA_CUSTOM_NAME")
    static EntityDataAccessor<Optional<Component>> mobstacker$customNameData() {
        throw new AssertionError();
    }

    @Accessor("DATA_CUSTOM_NAME_VISIBLE")
    static EntityDataAccessor<Boolean> mobstacker$customNameVisibleData() {
        throw new AssertionError();
    }
}
