package com.frikinjay.mobstacker.config;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A region's biome filter (1.12.0): biome ids ({@code minecraft:plains}) and biome tags
 * ({@code #minecraft:is_forest}). A region with a filter covers only the blocks of its box - or of
 * its whole dimension - that stand in one of those biomes; without one it covers every biome.
 *
 * <p>The same matching runs on the server (the rules) and on the client (the line at the top of the
 * screen), from one place, so the two cannot disagree on what "in a forest" means. Entries are parsed
 * once and kept, because the server asks for every mob inside a filtered region, every scan.
 */
public final class RegionBiomes {
    public static final String TAG_PREFIX = "#";
    /** The longest filter a region keeps, which is also the size of the GUI's text box. */
    public static final int MAX_TEXT_LENGTH = 512;

    /** entry -> a TagKey or a ResourceKey; an entry that does not parse is not here and never matches. */
    private static final Map<String, Object> PARSED = new ConcurrentHashMap<>();

    private RegionBiomes() {
    }

    /** Trimmed, lower case, and {@code minecraft:} added where no namespace was given. */
    public static String normalise(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        boolean tag = text.startsWith(TAG_PREFIX);
        String id = tag ? text.substring(TAG_PREFIX.length()) : text;
        if (id.isEmpty()) {
            return "";
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        return (tag ? TAG_PREFIX : "") + id;
    }

    /** Why this entry cannot be a biome or a biome tag, or null when it is well-formed. */
    public static String entryProblem(String entry) {
        String value = normalise(entry);
        if (value.isEmpty()) {
            return "A biome entry is empty";
        }
        String id = value.startsWith(TAG_PREFIX) ? value.substring(TAG_PREFIX.length()) : value;
        if (ResourceLocation.tryParse(id) == null) {
            return "'" + entry.trim() + "' is not a biome id or a #tag";
        }
        return null;
    }

    /**
     * A note for an entry this server does not know - a biome from a mod that is not installed, a tag
     * nothing defines - which is accepted all the same, as a mob list accepts another mod's mob: a
     * region has to survive its mod being away for a week. Null when the entry is known.
     */
    public static String entryNote(String entry, Registry<Biome> biomes) {
        if (biomes == null) {
            return null;
        }
        String value = normalise(entry);
        Object key = parsed(value);
        boolean known = key instanceof TagKey<?> tag
                ? biomes.getTag(TagKey.create(Registries.BIOME, tag.location())).isPresent()
                : key instanceof ResourceKey<?> id && biomes.containsKey(id.location());
        return known ? null : "'" + value + "' is not a biome " + (value.startsWith(TAG_PREFIX) ? "tag " : "")
                + "this game knows - kept, and it matches nothing until it exists";
    }

    /**
     * The entries in a line of text, split on commas and spaces, normalised, without repeats -
     * what the GUI's biome box holds.
     */
    public static List<String> parseList(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[,\\s]+")) {
            String value = normalise(part);
            if (!value.isEmpty() && !out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }

    /** The first malformed entry's problem, or null when every entry is fine. */
    public static String listProblem(Collection<String> entries) {
        if (entries == null) {
            return null;
        }
        if (String.join(", ", entries).length() > MAX_TEXT_LENGTH) {
            return "That is too many biomes for one region (" + MAX_TEXT_LENGTH + " characters at most)";
        }
        for (String entry : entries) {
            String problem = entryProblem(entry);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    /** The list as the GUI's box and the commands show it. */
    public static String join(List<String> entries) {
        return entries == null ? "" : String.join(", ", entries);
    }

    /** Whether the biome is one of {@code entries}, or in one of its tags. An empty filter matches all. */
    public static boolean matches(Holder<Biome> biome, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return true;
        }
        if (biome == null) {
            return false;
        }
        for (String entry : entries) {
            Object key = parsed(entry);
            if (key instanceof TagKey<?> tag) {
                @SuppressWarnings("unchecked")
                TagKey<Biome> biomeTag = (TagKey<Biome>) tag;
                if (biome.is(biomeTag)) {
                    return true;
                }
            } else if (key instanceof ResourceKey<?> id) {
                @SuppressWarnings("unchecked")
                ResourceKey<Biome> biomeKey = (ResourceKey<Biome>) id;
                if (biome.is(biomeKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object parsed(String entry) {
        if (entry == null) {
            return null;
        }
        Object known = PARSED.get(entry);
        if (known != null) {
            return known;
        }
        boolean tag = entry.startsWith(TAG_PREFIX);
        ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(TAG_PREFIX.length()) : entry);
        if (id == null) {
            return null;
        }
        Object key = tag ? TagKey.create(Registries.BIOME, id) : ResourceKey.create(Registries.BIOME, id);
        PARSED.put(entry, key);
        return key;
    }
}
