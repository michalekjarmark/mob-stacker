package com.frikinjay.mobstacker;

import com.frikinjay.mobstacker.compat.Nbt;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

/**
 * Brings a stack's own data up to the Minecraft version it is loaded in.
 *
 * <p>Minecraft upgrades a world's saved entities when the world is opened in a newer version, but only
 * what it knows: a stack's {@code StackData} rides along unchanged. Most of it is numbers, which do
 * not change, but the gear each member wears ({@code MemberEquipment}) is saved items, in the shape of
 * the version that saved them - {@code {id, Count, tag}} up to 1.20.4, {@code {id, count, components}}
 * from 1.20.5 - and a newer game cannot read an older shape: a 1.20.1 world opened on 1.21.1 would
 * lose every member's armour and weapons. So the stack data carries the data version it was saved in
 * ({@link #DATA_VERSION_KEY}), and on load every member's item is run through vanilla's own data
 * fixers from that version to the current one, the same way the game fixes the items it knows about.
 *
 * <p>Stack data from before this existed (every release up to 1.12.0) has no version: it was written
 * by the 1.20.1 build, the only one there was, so it is taken as 1.20.1's.
 *
 * <p>The version lives only in the save: {@link #stamp} puts it on the copy that is written, and
 * {@link #upgrade} takes it off again, so the live stack data never carries it.
 *
 * <p>A horse stack's member rolls ({@code MemberRolls}) are keyed by attribute id, and those ids were
 * renamed too ({@code horse.jump_strength} in 1.20.5, the {@code generic.} prefix in 1.21.2):
 * {@link MobVariants#upgradeRolls} moves every old name to this version's.
 */
public final class StackDataUpgrade {

    /** Key in the stack data: the data version (Minecraft's world format number) it was saved in. */
    public static final String DATA_VERSION_KEY = "DataVersion";

    /** Minecraft 1.20.1's data version: what stack data without a version was written in. */
    private static final int UNVERSIONED = 3465;

    private StackDataUpgrade() {
    }

    /** The data version of the running game. */
    public static int currentVersion() {
        //? if >=1.21.6 {
        /*return SharedConstants.getCurrentVersion().dataVersion().version();
        *///?} else {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        //?}
    }

    /** Marks stack data as written by this game: called on the copy that goes into every save. */
    public static void stamp(CompoundTag stackData) {
        if (!stackData.isEmpty()) {
            stackData.putInt(DATA_VERSION_KEY, currentVersion());
        }
    }

    /**
     * Upgrades stack data loaded from a save, in place, if it was written by an older game, and takes
     * the version off. Leaves data of the current version, or of a newer one (a world opened in an
     * older version is not something Minecraft supports either), as it is.
     */
    public static void upgrade(CompoundTag stackData) {
        if (stackData.isEmpty()) {
            return;
        }
        int from = Nbt.has(stackData, DATA_VERSION_KEY, Tag.TAG_INT) ? Nbt.getInt(stackData, DATA_VERSION_KEY) : UNVERSIONED;
        stackData.remove(DATA_VERSION_KEY);
        int to = currentVersion();
        if (from >= to) {
            return;
        }
        if (Nbt.has(stackData, MobStacker.MEMBER_EQUIPMENT_KEY, Tag.TAG_LIST)) {
            ListTag loadouts = Nbt.getList(stackData, MobStacker.MEMBER_EQUIPMENT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < loadouts.size(); i++) {
                upgradeLoadout(Nbt.getCompound(loadouts, i), from, to);
            }
        }
        if (Nbt.has(stackData, MobVariants.MEMBER_ROLLS_KEY, Tag.TAG_LIST)) {
            ListTag rolls = Nbt.getList(stackData, MobVariants.MEMBER_ROLLS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < rolls.size(); i++) {
                MobVariants.upgradeRolls(Nbt.getCompound(rolls, i));
            }
        }
    }

    /** One member's loadout: every slot that holds an item (the drop chances beside them are numbers). */
    private static void upgradeLoadout(CompoundTag loadout, int from, int to) {
        for (String key : Nbt.keys(loadout)) {
            if (!Nbt.has(loadout, key, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag item = Nbt.getCompound(loadout, key);
            Tag fixed = DataFixers.getDataFixer()
                    .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), from, to)
                    .getValue();
            if (fixed instanceof CompoundTag compound) {
                loadout.put(key, compound);
            }
        }
    }
}
