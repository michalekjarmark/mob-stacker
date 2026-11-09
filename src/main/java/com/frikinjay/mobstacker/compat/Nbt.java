package com.frikinjay.mobstacker.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

/**
 * Reading NBT the way 1.20.1 does it, on every version: a missing key reads as 0, false, "" or an empty
 * tag. From 1.21.5 {@code CompoundTag}'s getters answer an {@code Optional} instead, {@code getAllKeys}
 * is {@code keySet}, and {@code contains} and {@code getList} no longer take a tag type; every read of
 * the mod's own data goes through here, so the call sites stay the same on every version.
 */
public final class Nbt {
    /** {@code Tag.TAG_ANY_NUMERIC}: any of byte, short, int, long, float, double. */
    private static final int ANY_NUMERIC = 99;

    private Nbt() {
    }

    /** Whether {@code key} holds a tag of this type ({@code Tag.TAG_*}, or 99 for any number). */
    public static boolean has(CompoundTag tag, String key, int type) {
        Tag value = tag.get(key);
        if (value == null) {
            return false;
        }
        byte id = value.getId();
        return id == type || type == ANY_NUMERIC && id >= Tag.TAG_BYTE && id <= Tag.TAG_DOUBLE;
    }

    public static Set<String> keys(CompoundTag tag) {
        //? if >=1.21.5 {
        /*return tag.keySet();
        *///?} else {
        return tag.getAllKeys();
        //?}
    }

    public static int getInt(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getIntOr(key, 0);
        *///?} else {
        return tag.getInt(key);
        //?}
    }

    public static long getLong(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getLongOr(key, 0L);
        *///?} else {
        return tag.getLong(key);
        //?}
    }

    public static float getFloat(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getFloatOr(key, 0.0F);
        *///?} else {
        return tag.getFloat(key);
        //?}
    }

    public static double getDouble(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getDoubleOr(key, 0.0);
        *///?} else {
        return tag.getDouble(key);
        //?}
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getBooleanOr(key, false);
        *///?} else {
        return tag.getBoolean(key);
        //?}
    }

    public static String getString(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getStringOr(key, "");
        *///?} else {
        return tag.getString(key);
        //?}
    }

    /** The compound under {@code key}, or a new empty one (not stored) when there is none. */
    public static CompoundTag getCompound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getCompoundOrEmpty(key);
        *///?} else {
        return tag.getCompound(key);
        //?}
    }

    /** The list under {@code key} when its elements are of {@code type}, or a new empty one. */
    public static ListTag getList(CompoundTag tag, String key, int type) {
        //? if >=1.21.5 {
        /*ListTag list = tag.getListOrEmpty(key);
        return list.isEmpty() || list.get(0).getId() == type ? list : new ListTag();
        *///?} else {
        return tag.getList(key, type);
        //?}
    }

    /** Element {@code index} of a list of compounds, or a new empty one. */
    public static CompoundTag getCompound(ListTag list, int index) {
        //? if >=1.21.5 {
        /*return list.getCompoundOrEmpty(index);
        *///?} else {
        return list.getCompound(index);
        //?}
    }

    /** Element {@code index} of a list of strings, or "". */
    public static String getString(ListTag list, int index) {
        //? if >=1.21.5 {
        /*return list.getStringOr(index, "");
        *///?} else {
        return list.getString(index);
        //?}
    }
}
