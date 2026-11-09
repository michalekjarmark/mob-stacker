package com.frikinjay.mobstacker.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * What an entity's render state carries for its stack label (from 26.1, where entities are drawn from
 * a render state): the label, and where to draw it when vanilla has no name tag spot for the mob.
 */
public interface StackLabelState {
    Component mobstacker$label();

    void mobstacker$setLabel(Component label);

    Vec3 mobstacker$labelAt();

    void mobstacker$setLabelAt(Vec3 at);
}
