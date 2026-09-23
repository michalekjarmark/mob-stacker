package com.frikinjay.mobstacker;

import net.minecraft.sounds.SoundSource;

/**
 * A mob whose stack can be sheared as a whole: sheep and mooshrooms, where {@code stackedHarvest}
 * pays out for every member.
 *
 * <p>Implemented by each mob's own shear mixin, because what one member's harvest is differs per
 * mob - more wool from a sheep, five mushrooms from a mooshroom - and both ways of shearing (a
 * player's shears and a dispenser's) call this one method, so the two cannot come to pay out
 * differently.
 */
public interface StackShearable {

    /**
     * Harvests {@code count} more members' worth, <b>after</b> vanilla has sheared the top one.
     * Server side only. Makes no sound: the top one's shear already made the one a click should.
     */
    void mobstacker$shearMembers(SoundSource source, int count);
}
