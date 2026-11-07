package com.frikinjay.mobstacker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;

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
 * from stacking. So the mob's saved data is taken minus every key vanilla 1.20.1 writes on an entity
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

    /** Every NBT key vanilla 1.20.1 reads or writes on an entity (see the class comment). */
    static final Set<String> VANILLA_KEYS = Set.of(
            "AX", "AY", "AZ", "AbsorptionAmount", "Active", "ActiveEffects", "Age", "Air", "Amount",
            "AngerTime", "AngryAt", "ArmorDropChances", "ArmorItem", "ArmorItems",
            "AssignProfessionWhenSpawned", "AttachFace", "AttackTick", "Attributes", "BadOmenLevel", "Base",
            "BatFlags", "BeamTarget", "BlockState", "Body", "BoundX", "BoundY", "BoundZ", "Brain", "Bred",
            "BucketVariantTag", "CX", "CY", "CZ", "CanBreakDoors", "CanDuplicate", "CanJoinRaid",
            "CanPickUpLoot", "CancelDrop", "CannotBeHunted", "CannotEnterHiveTicks", "CannotHunt",
            "ChestedHorse", "CollarColor", "Color", "ConversionPlayer", "ConversionTime", "Count",
            "CropsGrownSincePollination", "Crouching", "CustomDisplayTile", "CustomName",
            "CustomNameVisible", "CustomPotionEffects", "DarkTicksRemaining", "DealtDamage",
            "DeathLootTable", "DeathLootTableSeed", "DeathTime", "DecorItem", "DespawnDelay", "Dir",
            "DisabledSlots", "DisplayOffset", "DisplayState", "DragonDeathTime", "DragonPhase", "DropItem",
            "DrownedConversionTime", "DuplicationCooldown", "Duration", "DurationOnUse", "EatingHaystack",
            "EffectDuration", "EffectId", "Effects", "EggLayTime", "Enabled", "EnderItems", "EntityTag",
            "ExplosionPower", "ExplosionRadius", "Facing", "FallDistance", "FallFlying", "FallHurtAmount",
            "FallHurtMax", "Fire", "FireworksItem", "Fixed", "FlowerPos", "FoodLevel", "ForcedAge",
            "FromBucket", "Fuel", "Fuse", "Glowing", "Gossips", "GotFish", "GroupsSpawned",
            "HandDropChances", "HandItems", "HasBeenShot", "HasEgg", "HasLeftHorn", "HasNectar",
            "HasRightHorn", "HasStung", "HasVisualFire", "Head", "Health", "HeroesOfTheVillage",
            "HiddenGene", "HivePos", "HomePosX", "HomePosY", "HomePosZ", "HuntingCooldown",
            "HurtByTimestamp", "HurtEntities", "HurtTime", "Id", "InLove", "InWaterTime", "Inventory",
            "Invisible", "Invul", "Invulnerable", "IsBaby", "IsChickenJockey", "IsImmuneToZombification",
            "IsScreamingGoat", "Item", "ItemDropChance", "ItemRotation", "Items", "Johnny",
            "LastDeathLocation", "LastGossipDecay", "LastPoseTick", "LastRestock", "Leash", "LeftArm",
            "LeftHanded", "LeftLeg", "LeftOwner", "Life", "LifeTicks", "LifeTime", "Lifetime", "LootTable",
            "LootTableSeed", "LoveCause", "MainGene", "Marker", "Modifiers", "Moistness", "MoreCarrotTicks",
            "Motion", "Name", "NextAvailableID", "NoAI", "NoBasePlate", "NoGravity", "NumGroups", "Offers",
            "OnGround", "Operation", "Owner", "Particle", "Passengers", "PatrolLeader", "PatrolTarget",
            "Patrolling", "Peek", "PersistenceRequired", "PickupDelay", "PierceLevel", "PlayerCreated",
            "PortalCooldown", "Pos", "Pose", "PostRaidTicks", "Potion", "PreRaidTicks", "PuffState",
            "Pumpkin", "PushX", "PushZ", "RabbitType", "Radius", "RadiusOnUse", "RadiusPerTick", "RaidId",
            "Raids", "ReapplicationDelay", "RestocksToday", "RightArm", "RightLeg", "RoarTick", "Rotation",
            "Saddle", "SaddleItem", "Score", "SelectedItemSlot", "Sheared", "ShotAtAngle",
            "ShotFromCrossbow", "ShoulderEntityLeft", "ShoulderEntityRight", "ShowArms", "ShowBottom",
            "Silent", "Sitting", "Size", "SkeletonTrap", "SkeletonTrapTime", "SleepTimer", "Sleeping",
            "SleepingX", "SleepingY", "SleepingZ", "Slot", "Small", "SoundEvent", "SpellTicks", "Started",
            "Status", "Steps", "StrayConversionTime", "Strength", "StunTick", "TNTFuse", "TXD", "TYD", "TZD",
            "Tags", "Tame", "Target", "Team", "Temper", "Thrower", "Tick", "TicksActive", "TicksFrozen",
            "TicksSincePollination", "TileEntityData", "TileX", "TileY", "TileZ", "Time", "TimeInOverworld",
            "TotalHealth", "TravelPosX", "TravelPosY", "TravelPosZ", "TreasurePosX", "TreasurePosY",
            "TreasurePosZ", "Trident", "Trusted", "Trusting", "Type", "UUID", "Value", "Variant",
            "VillagerData", "WaitTime", "WanderTarget", "Warmup", "Wave", "X", "Xp", "XpLevel", "XpP",
            "XpSeed", "XpTotal", "Y", "Z", "abilities", "alignment", "anger", "attack", "background",
            "billboard", "block_state", "brightness", "carriedBlockState", "crit", "damage", "data",
            "default_background", "facing", "flySpeed", "flying", "glow_color_override", "height", "id",
            "ignited", "inBlockState", "inGround", "instabuild", "interaction", "interpolation_duration",
            "invulnerable", "item", "item_display", "life", "line_width", "listener", "mayBuild", "mayfly",
            "pickup", "power", "powered", "response", "see_through", "shadow", "shadow_radius",
            "shadow_strength", "shake", "start_interpolation", "text", "text_opacity", "transformation",
            "variant", "view_range", "walkSpeed", "wasOnGround", "width");

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
        for (String key : all.getAllKeys()) {
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
        Set<String> keys = new TreeSet<>(da.getAllKeys());
        keys.addAll(db.getAllKeys());
        for (String key : keys) {
            if (ignored.contains(key)) {
                continue;
            }
            Tag ta = da.get(key);
            Tag tb = db.get(key);
            if (ta instanceof CompoundTag ca && tb instanceof CompoundTag cb) {
                // One level in, so a container such as ForgeCaps can name the mod's own entry and
                // that one entry can be ignored without ignoring the whole container.
                Set<String> inner = new TreeSet<>(ca.getAllKeys());
                inner.addAll(cb.getAllKeys());
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
