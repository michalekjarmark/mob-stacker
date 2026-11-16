package com.frikinjay.mobstacker.client;

/**
 * A frame's prepared features (26.2 on), able to draw the game's translucent gizmos - the region
 * boxes among them - late: after the water, the clouds and the weather, as the boxes were drawn below
 * 26.1. The game draws them before its translucent terrain, and as they write no depth the water and
 * clouds drawn after them covered boxes standing in front of them. {@code FeatureFrameGizmosMixin}
 * skips them in their usual place and implements this; {@code LevelRendererGizmosMixin} calls it.
 */
public interface LateGizmos {
    //? if >=26.3 {
    /*void mobstacker$executeGizmos(com.mojang.renderpearl.api.commands.RenderPass pass);
    *///?} else if >=26.2 {
    /*void mobstacker$executeGizmos();

    /^* Whether this frame has gizmos for {@link #mobstacker$executeGizmos} to draw. ^/
    boolean mobstacker$hasGizmos();
    *///?}
}
