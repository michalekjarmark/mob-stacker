package com.frikinjay.mobstacker.config;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.StackRefusal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.frikinjay.mobstacker.compat.Compat;

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

    /** What an entity entry starts with when it names a tag of mobs rather than one mob (1.12.0). */
    public static final String TAG_PREFIX = "#";

    /**
     * Parsed tag entries, keyed by the text stored in a list. {@link #listed} runs for every mob of
     * every scan, and a list is a handful of strings that change a few times a server's lifetime,
     * so parsing each entry once is enough. An entry that does not parse maps to {@link #NO_TAG}.
     */
    private static final Map<String, TagKey<EntityType<?>>> TAGS = new ConcurrentHashMap<>();
    private static final TagKey<EntityType<?>> NO_TAG =
            TagKey.create(Registries.ENTITY_TYPE, Compat.id("mobstacker", "not_a_tag"));

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

        /**
         * Replaces this holder's list wholesale, and for a region starts the override if there was
         * none. Needed because a region cannot begin overriding by having entries added one at a
         * time: the first add would produce a one-entry list where the inherited one had ten, and
         * an override of an empty global list could not be expressed at all.
         */
        void setList(MobListKind kind, List<String> entries);
    }

    /** Whether the mob's <em>type</em> is allowed to stack where it stands. */
    public static boolean allows(Entity entity) {
        return refusal(entity) == null;
    }

    /** Which list keeps the mob's type out of stacks where it stands, or null when none does. */
    public static StackRefusal refusal(Entity entity) {
        EntityType<?> type = entity.getType();
        // The region is found once and then used for both halves of the answer. Going through
        // MobStacker.setting for the mode would search the region list a second time, and this runs
        // for every mob of every scan.
        StackRegion region = MobStacker.regionAt(entity);
        if (modeIn(region) == MobListMode.WHITELIST) {
            return listed(MobListKind.Half.ALLOW, region, type) ? null : StackRefusal.NOT_ALLOW_LISTED;
        }
        return listed(MobListKind.Half.DENY, region, type) ? StackRefusal.DENY_LISTED : null;
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

    /**
     * Whether {@code type} appears on either list of this half: by its own id, by a tag it is in
     * ({@code #minecraft:raiders}), or by its mod.
     */
    public static boolean listed(MobListKind.Half half, StackRegion region, EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        List<String> entities = effective(MobListKind.of(half, MobListKind.Flavour.ENTITY), region);
        List<String> mods = effective(MobListKind.of(half, MobListKind.Flavour.MOD), region);
        return contains(entities, id.toString()) || inListedTag(entities, type) || contains(mods, id.getNamespace());
    }

    private static boolean inListedTag(List<String> entities, EntityType<?> type) {
        for (String entry : entities) {
            if (entry.startsWith(TAG_PREFIX)) {
                TagKey<EntityType<?>> tag = tagOf(entry);
                if (tag != NO_TAG && type.builtInRegistryHolder().is(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The tag a {@code #namespace:path} entry names, or {@link #NO_TAG} when it names none. */
    private static TagKey<EntityType<?>> tagOf(String entry) {
        return TAGS.computeIfAbsent(entry.toLowerCase(Locale.ROOT), text -> {
            ResourceLocation id = ResourceLocation.tryParse(text.substring(TAG_PREFIX.length()));
            return id == null ? NO_TAG : TagKey.create(Registries.ENTITY_TYPE, id);
        });
    }

    /** Whether a stored entity entry names a tag rather than one mob. */
    public static boolean isTag(String entry) {
        return entry != null && entry.startsWith(TAG_PREFIX);
    }

    /** Every entity tag the running game knows, as list entries ({@code #minecraft:raiders}), sorted. */
    public static List<String> tagEntries() {
        List<String> out = new ArrayList<>();
        Compat.tagKeys(BuiltInRegistries.ENTITY_TYPE).forEach(tag -> out.add(TAG_PREFIX + tag.location()));
        out.sort(String::compareTo);
        return out;
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
            // "#raiders" is vanilla's tag the same way "cow" is vanilla's mob.
            return trimmed.startsWith(TAG_PREFIX)
                    ? TAG_PREFIX + "minecraft:" + trimmed.substring(TAG_PREFIX.length())
                    : "minecraft:" + trimmed;
        }
        return trimmed;
    }

    /**
     * The canonical form of an entity id, for the per-type stack ceilings.
     *
     * <p>The same rule the entity lists use — {@code cow} means {@code minecraft:cow} — kept as its
     * own method so a ceiling lookup does not have to name a mob <em>list</em> to normalise a key.
     */
    public static String normaliseEntityId(String entityId) {
        return normalise(MobListKind.DENY_ENTITIES, entityId);
    }

    /**
     * Why {@code normalised} cannot go on a list of this kind, or null when it may.
     *
     * <p>The rule is not "does this mob exist right now", because a server that lists a mod's mob
     * and then takes the mod out for a week has to get its list back when it comes home. It is
     * <em>"could this ever have meant anything"</em>: a {@code minecraft:} id no entity type answers
     * to can only be a typo, since vanilla is always fully loaded, while any other namespace is
     * taken on trust. {@link #entryNote} then says out loud when a trusted entry is not loaded, so
     * accepting one is never silent.
     *
     * <p>One method, called by the commands, the GUI and the network handler alike — a packet is
     * whatever the other end put in it, and a second copy of this rule is how the two would drift.
     */
    public static String entryProblem(MobListKind kind, String normalised) {
        if (normalised == null || normalised.isEmpty()) {
            return "An entry cannot be empty.";
        }
        if (kind.flavour() == MobListKind.Flavour.MOD) {
            return normalised.indexOf(':') >= 0
                    ? "'" + normalised + "' names one mob, not a mod. Use just the part before the colon."
                    : null;
        }
        if (isTag(normalised)) {
            // Any well-formed tag is taken on trust, a minecraft: one included: tags come from data
            // packs, which can be added and removed while the list stays. entryNote says so out loud.
            return tagOf(normalised) == NO_TAG ? "'" + normalised + "' is not a usable tag." : null;
        }
        return entityProblem(normalised);
    }

    /**
     * The same judgement for a bare entity id, which the per-type stack ceilings also need and
     * which names no list to ask about.
     */
    public static String entityProblem(String normalised) {
        if (normalised == null || normalised.isEmpty()) {
            return "An entry cannot be empty.";
        }
        ResourceLocation id = ResourceLocation.tryParse(normalised);
        if (id == null) {
            return "'" + normalised + "' is not a usable entity id.";
        }
        if ("minecraft".equals(id.getNamespace()) && !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return "There is no mob called '" + normalised + "'.";
        }
        return null;
    }

    /**
     * A remark worth printing beside an entry that was accepted but means nothing in this game, or
     * null when it names something that is loaded. Not a refusal: see {@link #entryProblem}.
     */
    public static String entryNote(MobListKind kind, String normalised) {
        if (isLoaded(kind, normalised)) {
            return null;
        }
        if (kind.flavour() == MobListKind.Flavour.ENTITY && isTag(normalised)) {
            return "No tag called '" + normalised + "' is loaded — kept, in case the data pack or mod that adds it comes back.";
        }
        return kind.flavour() == MobListKind.Flavour.ENTITY
                ? "Nothing in this game is called '" + normalised + "' — kept, in case the mod that adds it comes back."
                : "No mob in this game comes from '" + normalised + "' — kept, in case the mod that adds them comes back.";
    }

    /** Whether this entry names something the running game actually has. */
    public static boolean isLoaded(MobListKind kind, String normalised) {
        if (normalised == null || normalised.isEmpty()) {
            return false;
        }
        if (kind.flavour() == MobListKind.Flavour.MOD) {
            for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                if (id.getNamespace().equalsIgnoreCase(normalised)) {
                    return true;
                }
            }
            return false;
        }
        if (isTag(normalised)) {
            TagKey<EntityType<?>> tag = tagOf(normalised);
            return tag != NO_TAG && Compat.hasTag(BuiltInRegistries.ENTITY_TYPE, tag);
        }
        ResourceLocation id = ResourceLocation.tryParse(normalised);
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    /** An unmodifiable view of {@code list}, treating null as empty. */
    public static List<String> view(List<String> list) {
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }
}
