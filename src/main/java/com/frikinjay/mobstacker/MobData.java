package com.frikinjay.mobstacker;

import com.frikinjay.mobstacker.compat.Nbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

/**
 * strictDataComparison (1.12.0, ROADMAP #42): two mobs merge only if the data <em>other mods</em>
 * saved on them is the same - a level, an affix, a champion's rank, an owner kept by some mod. A
 * merge keeps the survivor's data and drops the other's, so mobs that differ there must stay apart.
 *
 * <p>Vanilla's own data is not compared here. Whatever of it describes a mob (colour, markings,
 * genes) is matched by {@link MobVariants}; the rest differs between any two mobs and means nothing
 * to a stack (position, health, timers, memories, gossip), and comparing it would stop everything
 * from stacking. So the mob's saved data is taken minus every key vanilla writes on an entity
 * ({@link #VANILLA_KEYS}, read off vanilla's bytecode: every string constant in a method of an entity
 * class that reads or writes a CompoundTag) and minus this mod's own {@code StackData}. Whatever is
 * left was written by another mod: {@code ForgeData}, {@code ForgeCaps}, a modded mob's own fields,
 * a key some mod mixed into vanilla mobs.
 *
 * <p>A mod that keeps a timer or a counter on every mob would stop its mobs from stacking at all.
 * {@code /mobstacker inspect} names the key that differs, and {@code ignoredDataKeys} lets it
 * through ({@code Key}, or {@code Container.key} for one entry inside a compound such as ForgeCaps).
 */
public final class MobData {

    private MobData() {
    }

    /** Keys some mods are known to write on every mob for their own bookkeeping, never a difference. */
    private static final Set<String> ALWAYS_IGNORED = Set.of(
            MobStacker.STACK_DATA_KEY,
            // Let Me Despawn's "came from a spawner" marks.
            "letmedespawn_spawner", "letmedespawn_trial_spawner");

    /**
     * Every NBT key vanilla reads or writes on an entity in the Minecraft this jar is built for (see the
     * class comment). Generated per version from vanilla's bytecode by {@code tools/VanillaKeys.java}
     * into {@code src/main/vanilla-keys/<version>.txt}, which the build packs as
     * {@code mobstacker_vanilla_keys.txt}; the keys change between versions (1.21: attributes, leash,
     * body_armor_item...), and a key missing here would make vanilla's own data look like another mod's.
     */
    static final Set<String> VANILLA_KEYS = loadVanillaKeys();

    private static Set<String> loadVanillaKeys() {
        try (InputStream in = MobData.class.getResourceAsStream("/mobstacker_vanilla_keys.txt")) {
            if (in == null) {
                throw new IllegalStateException("mobstacker_vanilla_keys.txt is missing from the jar");
            }
            Set<String> keys = new HashSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isBlank()) {
                    keys.add(line.trim());
                }
            }
            return Set.copyOf(keys);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** How long (ticks) a mob's filtered data is reused before it is serialised again. */
    private static final int CACHE_TICKS = 20;

    private record Cached(long gameTime, CompoundTag data) {}

    /**
     * The scan asks this for every candidate next to a mob, and serialising a mob is the expensive
     * part, so each mob's result is kept for a second. Server thread only.
     */
    private static final Map<Mob, Cached> CACHE = new WeakHashMap<>();

    /** The part of the mob's saved data that other mods wrote. */
    static CompoundTag moddedData(Mob mob) {
        long now = mob.level().getGameTime();
        Cached cached = CACHE.get(mob);
        if (cached != null && now - cached.gameTime() < CACHE_TICKS && now >= cached.gameTime()) {
            return cached.data();
        }
        CompoundTag all = mob.saveWithoutId(new CompoundTag());
        CompoundTag modded = new CompoundTag();
        for (String key : Nbt.keys(all)) {
            if (!VANILLA_KEYS.contains(key) && !ALWAYS_IGNORED.contains(key)) {
                modded.put(key, all.get(key));
            }
        }
        CACHE.put(mob, new Cached(now, modded));
        return modded;
    }

    /**
     * Where the data other mods saved on these two differs, as {@code Key} or {@code Key.entry}, or
     * null when it does not. {@code ignored} holds the entries of {@code ignoredDataKeys}.
     */
    public static String difference(Mob a, Mob b, Set<String> ignored) {
        CompoundTag da = moddedData(a);
        CompoundTag db = moddedData(b);
        if (da.isEmpty() && db.isEmpty()) {
            return null;
        }
        Set<String> keys = new TreeSet<>(Nbt.keys(da));
        keys.addAll(Nbt.keys(db));
        for (String key : keys) {
            if (ignored.contains(key)) {
                continue;
            }
            Tag ta = da.get(key);
            Tag tb = db.get(key);
            if (ta instanceof CompoundTag ca && tb instanceof CompoundTag cb) {
                // One level in, so a container such as ForgeCaps can name the mod's own entry and
                // that one entry can be ignored without ignoring the whole container.
                Set<String> inner = new TreeSet<>(Nbt.keys(ca));
                inner.addAll(Nbt.keys(cb));
                for (String sub : inner) {
                    String path = key + "." + sub;
                    if (!ignored.contains(path) && !Objects.equals(ca.get(sub), cb.get(sub))) {
                        return path;
                    }
                }
            } else if (!Objects.equals(ta, tb)) {
                return key;
            }
        }
        return null;
    }

    private static String lastIgnoredText;
    private static Set<String> lastIgnored = Set.of();

    /** {@link #parseIgnored}, remembering the last answer: the setting changes rarely, merges are many. */
    public static Set<String> ignored(String value) {
        String text = value == null ? "" : value;
        if (!text.equals(lastIgnoredText)) {
            lastIgnored = parseIgnored(text);
            lastIgnoredText = text;
        }
        return lastIgnored;
    }

    /** {@code ignoredDataKeys} as a set: comma-separated, spaces around the commas allowed. */
    public static Set<String> parseIgnored(String value) {
        Set<String> out = new TreeSet<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split(",")) {
            String key = part.trim();
            if (!key.isEmpty()) {
                out.add(key);
            }
        }
        return out;
    }

    /** Why {@code value} is not a usable ignoredDataKeys list, or null. */
    public static String ignoredProblem(String value) {
        for (String key : parseIgnored(value)) {
            if (!key.matches("[A-Za-z0-9_:.\\-/]+")) {
                return "'" + key + "' is not an NBT key (letters, digits, _ : . - /; separate keys with commas).";
            }
        }
        return null;
    }
}
