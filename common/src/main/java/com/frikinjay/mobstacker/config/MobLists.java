package com.frikinjay.mobstacker.config;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The one place that answers "is this kind of mob allowed to stack here".
 *
 * <p>It replaces the pair of {@code contains} calls that used to sit inline in
 * {@link MobStacker#canStack}, which could only ever see the global blacklist. The verdict now
 * depends on three things resolved together: the {@code mobListMode} in force at the mob (a region
 * may run a whitelist while the world runs a blacklist), the lists that mode consults, and whether
 * the region the mob is standing in overrides them.
 *
 * <p><b>A region's list replaces the global one rather than adding to it</b>, exactly like every
 * other per-region setting since 1.6.0 — a region that sets nothing inherits, a region that sets a
 * list means that list and no other. The two flavours resolve independently, so a region can take
 * over the entity list and still inherit the mod list. Keeping this the same shape as the scalar
 * settings is deliberate: a second, subtly different inheritance rule is exactly what the per-mob
 * variant tables taught us to avoid.
 */
public final class MobLists {

    private MobLists() {
    }

    /**
     * Anything that carries the four lists: the global config, and each region.
     *
     * <p>A region answers {@code false} from {@link #hasList} for a list it does not override, which
     * is how "inherit" is told apart from "override with an empty list" — the difference between a
     * region that says nothing and one that says "nothing stacks here".
     */
    public interface Holder {
        /** The entries of this list, never null; empty both when unset and when genuinely empty. */
        List<String> getList(MobListKind kind);

        /** Whether this holder has a list of its own, as opposed to inheriting one. */
        boolean hasList(MobListKind kind);

        /** @return true when the entry was not already there */
        boolean addToList(MobListKind kind, String entry);

        /** @return true when the entry was there to remove */
        boolean removeFromList(MobListKind kind, String entry);

        /** Drops the list entirely. For a region that means going back to inheriting the global one. */
        void clearList(MobListKind kind);
    }

    /** Whether the mob's <em>type</em> is allowed to stack where it stands. */
    public static boolean allows(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        // The region is found once and then used for both halves of the answer. Going through
        // MobStacker.setting for the mode would search the region list a second time, and this runs
        // for every mob of every scan.
        StackRegion region = MobStacker.regionAt(entity);
        if (modeIn(region) == MobListMode.WHITELIST) {
            return listed(MobListKind.Half.ALLOW, region, id);
        }
        return !listed(MobListKind.Half.DENY, region, id);
    }

    /** The list mode in force inside {@code region}: its own override, or the global one. */
    public static MobListMode modeIn(StackRegion region) {
        String override = region == null ? null : region.getSetting("mobListMode");
        if (override != null) {
            for (MobListMode mode : MobListMode.values()) {
                if (mode.name().equalsIgnoreCase(override.trim())) {
                    return mode;
                }
            }
        }
        return MobStacker.config.getMobListMode();
    }

    /** Whether {@code id} appears on either list of this half, by its own name or by its mod. */
    public static boolean listed(MobListKind.Half half, StackRegion region, ResourceLocation id) {
        List<String> entities = effective(MobListKind.of(half, MobListKind.Flavour.ENTITY), region);
        List<String> mods = effective(MobListKind.of(half, MobListKind.Flavour.MOD), region);
        return contains(entities, id.toString()) || contains(mods, id.getNamespace());
    }

    /**
     * The list that actually applies inside {@code region}: its own when it has one, the global one
     * otherwise. A null region simply means "outside every region", i.e. the global list.
     */
    public static List<String> effective(MobListKind kind, StackRegion region) {
        if (region != null && region.hasList(kind)) {
            return region.getList(kind);
        }
        return MobStacker.config.getList(kind);
    }

    private static boolean contains(List<String> list, String value) {
        for (String entry : list) {
            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tidies an entry on its way into a list, so the same thing typed two ways is stored once.
     *
     * <p>An entity entry without a namespace is assumed to be vanilla's, because {@code cow} is what
     * people type and {@code minecraft:cow} is what the registry calls it — and a list holding both
     * spellings of one mob is a bug report waiting to happen.
     */
    public static String normalise(MobListKind kind, String entry) {
        String trimmed = entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT);
        if (kind.flavour() == MobListKind.Flavour.ENTITY && !trimmed.isEmpty() && !trimmed.contains(":")) {
            return "minecraft:" + trimmed;
        }
        return trimmed;
    }

    /** An unmodifiable view of {@code list}, treating null as empty. */
    public static List<String> view(List<String> list) {
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }
}
