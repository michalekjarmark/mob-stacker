package com.frikinjay.mobstacker;

import com.frikinjay.mobstacker.api.MobStackerAPI;
import com.frikinjay.mobstacker.config.MobLists;
import com.frikinjay.mobstacker.config.MobStackerConfig;
import com.frikinjay.mobstacker.config.StackAi;
import com.frikinjay.mobstacker.config.StackMode;
import com.frikinjay.mobstacker.config.StackRegion;
import com.frikinjay.mobstacker.mixin.AbstractVillagerAccessor;
import com.frikinjay.mobstacker.mixin.ArmorStandAccessor;
import com.frikinjay.mobstacker.mixin.MobEquipmentAccessor;
import com.frikinjay.mobstacker.mixin.ParrotAccessor;
import com.frikinjay.mobstacker.mixin.OcelotAccessor;
import com.frikinjay.mobstacker.mixin.FoxAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import net.minecraft.world.entity.decoration.ArmorStand;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class MobStacker {
    public static final String MOD_ID = "mobstacker";

    /**
     * The JVM flag that lets players draw region boxes through walls: {@code -Dmobstacker.xray=true}
     * on the <b>server</b>. Off unless it is given, like {@code -Dmobstacker.selftest}.
     *
     * <p>A box seen through a mountain is a small x-ray of the world, so it is the server's to allow,
     * never the player's: the server says whether it is allowed in the config-sync snapshot, and the
     * client obeys that and ignores its own flag. In singleplayer, and for whoever hosts a LAN game,
     * the server is the game itself, so the flag goes on the game.
     */
    public static final String XRAY_PROPERTY = "mobstacker.xray";

    /** Whether this server lets its players draw region boxes through walls. */
    public static boolean regionXrayAllowed() {
        return Boolean.getBoolean(XRAY_PROPERTY);
    }

    public static final Logger logger = LogUtils.getLogger();
    public static final String STACK_DATA_KEY = "StackData";
    public static final String STACK_SIZE_KEY = "StackSize";
    public static final String CAN_STACK_KEY = "CanStack";
    // Damage already carried by every member below the top one (per-mob Sweeping Edge). All members
    // of a stack share one set of stats, so a single number describes all of them.
    public static final String MEMBER_DAMAGE_KEY = "MemberDamage";
    /** What each mob below the top one wears and holds, one entry per member. */
    public static final String MEMBER_EQUIPMENT_KEY = "MemberEquipment";
    /** Set once a player has put a name tag on this mob, so its name is never guessed at again. */
    public static final String PLAYER_NAMED_KEY = "PlayerNamed";
    /** Set when that name tag was put on a stack, i.e. the name is a label and not a pet's name. */
    public static final String NAMED_STACK_KEY = "NamedStack";
    /** The name the player typed, as JSON, so the " xN" suffix is appended and never parsed back off. */
    public static final String STACK_NAME_KEY = "StackName";
    /** How the mob came into the world: vanilla's MobSpawnType name, recorded once. See {@link SpawnOrigin}. */
    public static final String ORIGIN_KEY = "Origin";

    // --- Stack breeding (feeding a stacked adult its food breeds its members in pairs) ---
    // Members currently "in love" waiting for a partner; kept so partial feeding never wastes food.
    // Set on a mob the mod itself has just taken out of a stack, so the stack-on-spawn pass leaves
    // it alone. Without it a mob would be handed to the player and walk straight back in on the same
    // tick, which is exactly what separating was asked to undo.
    private static final String JUST_SEPARATED_KEY = "JustSeparated";
    /**
     * Ticks left before a mob the mod handed to a player may walk back into a stack. How long that
     * is comes from {@code separationCooldown} (seconds, default 0), refreshed by every further
     * interaction with the mob.
     *
     * <p>It was a fixed fifteen seconds, put in when taming a horse out of a herd never landed: the
     * horse bucked the player off, went straight back in on the next scan, and the next click peeled
     * a fresh one out with its temper back at zero. That is covered for good by a different rule now -
     * a horse with any temper is {@link #isPlayerBound}, so it cannot merge at all - and nothing else
     * handed over keeps progress a merge could lose: a bone or a fish is a fresh roll every time, a
     * sheared sheep cannot merge into a woolly stack anyway, a bucketed fish is in the bucket. The
     * last test round found the wait itself was the only thing left to notice, so it is a setting,
     * off by default.
     */
    private static final String SEPARATION_GRACE_KEY = "SeparationGrace";
    public static final String BREED_LOVE_KEY = "BreedLove";
    // How many members recently bred and are on breeding cooldown, and until when (game time).
    public static final String BREED_COOLDOWN_COUNT_KEY = "BreedCooldownCount";
    public static final String BREED_COOLDOWN_END_KEY = "BreedCooldownEnd";
    // Vanilla breeding cooldown is 5 minutes (6000 ticks).
    private static final int BREED_COOLDOWN_TICKS = 6000;
    public static MobStackerConfig config;
    // Resolved to the running world's save folder on server start (see loadWorldConfig).
    // The global path is only a pre-server fallback so the field is never null.
    public static File configFile = new File("config/mobstacker.json");

    private static final WeakHashMap<Class<?>, Boolean> bossEntityCache = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Field> bossFieldCache = new WeakHashMap<>();

    /**
     * Kinds that never stack, whatever the settings say (1.12.0): a mob whose whole point is being
     * one of a kind - a wandering trader and its trades, an allay and what it holds, a vex and its
     * evoker, a bee and its hive. Shipped as a data pack tag so a server can change it without a
     * new jar; see data/mobstacker/tags/entity_types/unstackable.json.
     */
    public static final TagKey<EntityType<?>> UNSTACKABLE_TAG =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(MOD_ID, "unstackable"));
    /**
     * Bosses, which stack only while {@code stackBosses} is on. Also pulls in the loaders' own
     * {@code #c:bosses} (Fabric) and {@code #forge:bosses} when they exist.
     */
    public static final TagKey<EntityType<?>> BOSSES_TAG =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(MOD_ID, "bosses"));
    /**
     * A kind's base max health as registered, per type. Read off the type and not the mob, because
     * stackHealth raises a stack's own max health with every member: twenty cows would read as a
     * 200-health boss.
     */
    private static final Map<EntityType<?>, Double> BASE_MAX_HEALTH = new ConcurrentHashMap<>();
    /** How often (ticks) a stack checks whether one of its members has to leave it (shedClaims). */
    private static final int SHED_INTERVAL = 20;

    private static final Pattern STACKED_NAME_PATTERN = Pattern.compile(" x\\d+$");

    // Short-lived floating "-N killed" holograms (invisible marker armor stands), spawned on a
    // stack kill and cleaned up ~1s later from the server tick (see MinecraftServerMixin).
    private static final int KILL_HOLOGRAM_LIFETIME_TICKS = 24;
    private static final List<KillHologram> killHolograms = new ArrayList<>();
    // Every hologram carries this scoreboard tag. It marks the stand as ours so it is never saved
    // to disk (EntityMixin#shouldBeSaved) and so a stray one left in an old world by a previous
    // version can be identified and cleaned up when it ticks (ArmorStandMixin).
    public static final String KILL_HOLOGRAM_TAG = "mobstacker_kill_hologram";

    private record KillHologram(ArmorStand entity, long expireGameTime) {}

    public static void init() {
        // Load a default config up front so MobStacker.config is never null. The real,
        // per-world config is loaded from the world's save folder when a server starts
        // (see loadWorldConfig / MinecraftServerMixin), so each world/server keeps its
        // own settings instead of sharing one global file. We intentionally do not save
        // here, to avoid leaving a stray global file that would never actually be used.
        config = MobStackerConfig.load();
        // Commands are registered by CommandsMixin when the server's Commands are built.
    }

    /**
     * Points the config at the running world's save folder and (re)loads it, so every
     * world/server has a single config file of its own, loaded on start. Called from
     * {@code MinecraftServerMixin} when the server is created.
     */
    public static void loadWorldConfig(MinecraftServer server) {
        // A previous world's holograms must never be ticked against this one.
        clearKillHolograms();
        OWNER_COMMANDS_SENT.clear();
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
            Files.createDirectories(dir);
            configFile = dir.resolve("mobstacker.json").toFile();
        } catch (Exception e) {
            logger.error("MobStacker: could not resolve the per-world config path, using the global file", e);
            configFile = new File("config/mobstacker.json");
        }
        // Capture first-run state before save() creates the file, so we only nag once per world.
        boolean firstRun = !configFile.exists();
        config = MobStackerConfig.load();
        config.save();
        logger.info("MobStacker: per-world config loaded (stackMode={})", config.getStackMode());
        if (firstRun) {
            logFirstRunNotice();
        }
    }

    /**
     * Logged once, the first time a world loads without an existing config. Stacking now ships
     * {@link StackMode#OFF}, so this tells an operator how to turn it on rather than silently
     * doing nothing.
     */
    private static void logFirstRunNotice() {
        logger.info("============================================================");
        logger.info("  MobStacker is installed, but stacking is currently OFF.");
        logger.info("  Mobs will NOT stack until you pick a mode:");
        logger.info("    /mobstacker set stackMode everywhere   - stack everywhere");
        logger.info("    /mobstacker set stackMode players      - stack only near players");
        logger.info("    /mobstacker set stackMode regions      - stack only in ALLOW regions");
        logger.info("  Tip: adding an ALLOW region auto-enables REGIONS mode.");
        logger.info("============================================================");
    }

    /** Re-reads the config from disk (used by {@code /mobstacker reload} after a manual edit). */
    public static void reloadConfig() {
        config = MobStackerConfig.load();
        configChanged();
        logger.info("MobStacker config reloaded");
    }

    public static boolean canStack(Mob entity) {
        return stackRefusal(entity) == null;
    }

    /**
     * Why this mob may not take part in stacking where it stands, or null when it may. The one place
     * the rules are written down: {@link #canStack} is this compared with null, and
     * {@code /mobstacker inspect} shows a player the answer.
     *
     * <p>Its {@code CanStack} flag is not asked here, because the callers always asked it
     * separately; {@code inspect} asks it first.
     */
    public static StackRefusal stackRefusal(Mob entity) {
        return stackRefusal(entity, true);
    }

    /**
     * As {@link #stackRefusal(Mob)}, but with {@code checkWhere} false the one rule about the place -
     * stackMode and the regions - is not asked, for {@code /mobstacker stack}, which stacks mobs where
     * the scan would not. Everything about the mob itself is still asked.
     */
    public static StackRefusal stackRefusal(Mob entity, boolean checkWhere) {
        // Babies stack only when their category's flag allows it. Farm animals (Animal) and
        // hostile/other mobs (e.g. baby zombies) are controlled independently. Baby-vs-adult
        // mixing is prevented separately in canMerge.
        if (entity.isBaby()) {
            if (entity instanceof Animal) {
                if (!getEnableAnimalBabyStacking(entity)) {
                    return StackRefusal.BABY_ANIMAL;
                }
            } else if (!getEnableHostileBabyStacking(entity)) {
                return StackRefusal.BABY_HOSTILE;
            }
        }

        // A mob that is dead or playing its death animation (health <= 0) must not take part
        // in stacking. Merging into a dying mob restores its health mid-animation, which makes
        // the death animation loop. Excluding it lets the death finish cleanly.
        if (entity.isDeadOrDying() || entity.isRemoved()) {
            return StackRefusal.DYING;
        }

        // Kinds that never stack (1.12.0). The tag is the server's to change, through a data pack.
        if (entity.getType().is(UNSTACKABLE_TAG)) {
            return StackRefusal.UNSTACKABLE;
        }
        if (!getStackBosses(entity) && isBoss(entity)) {
            return StackRefusal.BOSS;
        }
        // stackAi never sets this flag (it would outlive the mod), so a mob with it was frozen by a
        // player or a map on purpose: a display, a statue, a puzzle piece.
        if (entity.isNoAi()) {
            return StackRefusal.NO_AI;
        }

        // Mobs that hold or wear something (e.g. an armed/armored zombie) carry per-mob data the
        // stack cannot represent, and merging would drop their gear. Keep them unstacked unless
        // explicitly allowed.
        if (!getStackEquippedMobs(entity) && hasEquipment(entity)) {
            return StackRefusal.EQUIPPED;
        }

        if (isPlayerBound(entity)) {
            return StackRefusal.PLAYER_BOUND;
        }

        StackRefusal busy = busyRefusal(entity);
        if (busy != null) {
            return busy;
        }

        // Handed to a player a moment ago: leave it with them. Checked here rather than only at the
        // merge attempt so nothing merges INTO it either - a stack absorbing the animal somebody is
        // halfway through taming is the same bug seen from the other side.
        if (separationGrace(entity) > 0) {
            return StackRefusal.SEPARATION_GRACE;
        }

        StackRefusal location = checkWhere ? locationRefusal(entity) : null;
        if (location != null) {
            return location;
        }

        // Whether this kind of mob may stack at all here: a blacklist or a whitelist, global or
        // the region's own. MobLists is the single place that decides, because the answer now
        // depends on three things (the mode in force, which lists it consults, and whether the
        // region overrides them) and working any of them out twice is how tables drift apart.
        StackRefusal listed = MobLists.refusal(entity);
        if (listed != null) {
            return listed;
        }

        if (!originStacks(entity)) {
            return StackRefusal.ORIGIN;
        }

        if (!hasValidCustomNameForStacking(entity)) {
            return StackRefusal.PLAYER_NAMED;
        }
        return getStackSize(entity) < getMaxMobStackSize(entity) ? null : StackRefusal.FULL;
    }

    /**
     * Decides whether a mob is allowed to form stacks at its current location,
     * based on the global {@link StackMode} and the configured regions.
     * Note: this only gates the formation of new stacks. Mobs that are already
     * stacked keep behaving correctly anywhere (death splitting, separation, ...).
     */
    public static boolean isStackingAllowedAt(Mob entity) {
        return locationRefusal(entity) == null;
    }

    /** Why stacking is not allowed where this mob stands, or null when it is. See {@link #isStackingAllowedAt}. */
    public static StackRefusal locationRefusal(Mob entity) {
        StackMode mode = config.getStackMode();
        if (mode == StackMode.OFF) {
            return StackRefusal.STACKING_OFF;
        }

        BlockPos pos = entity.blockPosition();

        boolean insideAllowRegion = false;
        for (StackRegion region : config.getRegions()) {
            // With the level, so a region's biome filter (1.12.0) is part of the answer.
            if (region.contains(entity.level(), pos)) {
                if (region.isDeny()) {
                    return StackRefusal.DENY_REGION; // DENY overrides everything, in every mode.
                }
                insideAllowRegion = true;
            }
        }

        if (mode == StackMode.EVERYWHERE) {
            return null;
        }

        if (mode == StackMode.PLAYERS) {
            // Only stack near a player. getNearestPlayer applies the distance limit itself and
            // excludes spectators; a radius <= 0 would disable stacking entirely (guarded below).
            double radius = config.getPlayerStackRadius();
            return radius > 0 && entity.level().getNearestPlayer(entity, radius) != null
                    ? null : StackRefusal.NO_PLAYER_NEAR;
        }

        // StackMode.REGIONS: only stack when inside at least one ALLOW region.
        return insideAllowRegion ? null : StackRefusal.OUTSIDE_ALLOW_REGION;
    }

    /**
     * Mobs a player has invested something in: tamed or owned, saddled, wearing horse armor,
     * carrying a chest, leashed, or currently carrying or being carried by someone.
     *
     * <p>None of that survives a merge. A horse's saddle, armor and chest live in an inventory of
     * its own rather than in the equipment slots, so {@link #hasEquipment} never saw them and
     * {@code stackEquippedMobs} never protected them; taming, ownership and temper are plain save
     * keys that the mob being merged away would copy straight over the survivor. A wild horse
     * wandering into your saddled one used to wipe it.
     *
     * <p>This is a hard rule with no setting behind it, because there is no version of "stack these
     * anyway" that does not throw something of the player's away. Nothing is lost in performance
     * either: the crowds worth stacking are wild ones, and foals are born untamed, so a breeding
     * pen still stacks everything it produces.
     *
     * @return true if the mob must be left alone by stacking entirely.
     */
    public static boolean isPlayerBound(Mob entity) {
        if (entity.isVehicle() || entity.isPassenger() || entity.isLeashed()) {
            return true;
        }
        if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return true;
        }
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            return true;
        }
        if (entity instanceof Saddleable saddleable && saddleable.isSaddled()) {
            return true;
        }
        // Temper is taming progress, and it is per horse: a half-tamed horse that merges back into
        // the herd takes every attempt spent on it with it, and the next one out starts at zero.
        if (entity instanceof AbstractHorse horse
                && (horse.isTamed() || horse.isWearingArmor() || horse.getTemper() > 0)) {
            return true;
        }
        if (entity instanceof AbstractChestedHorse chested && chested.hasChest()) {
            return true;
        }
        // A golem somebody built to guard their base. The ones an iron farm makes are the village's,
        // not a player's, so the farm still stacks.
        if (entity instanceof IronGolem golem && golem.isPlayerCreated()) {
            return true;
        }
        // Trust is the other way a player owns an animal without owning it. An ocelot is never tamed
        // and a fox remembers who bred it by UUID, so neither has an owner field for the checks above
        // to notice - and a merge would hand one wild mob's indifference to the whole stack.
        if (entity instanceof Ocelot ocelot) {
            return ((OcelotAccessor) ocelot).mobstacker$isTrusting();
        }
        return entity instanceof Fox fox && !((FoxAccessor) fox).mobstacker$trustedUUIDs().isEmpty();
    }

    /**
     * Something the mob is in the middle of that a merge would cut short (1.12.0): an animal a
     * player is breeding, a raider counted by its raid, a villager holding a claim, an enderman
     * holding a block. Each would lose the thing to the merge - the love, the raid's tally, the bed
     * or workstation, the block itself.
     */
    public static StackRefusal busyRefusal(Mob entity) {
        if (entity instanceof Animal animal && animal.isInLove()) {
            return StackRefusal.IN_LOVE;
        }
        // A raid counts its raiders one entity at a time: one merged away is one the raid thinks is
        // gone, so a wave could end early, and the stack left behind would no longer belong to it.
        if (entity instanceof Raider raider && raider.hasActiveRaid()) {
            return StackRefusal.RAID;
        }
        if (entity instanceof PatrollingMonster patrol && patrol.isPatrolLeader()) {
            return StackRefusal.RAID;
        }
        if (entity instanceof Villager villager && hasVillagerClaim(villager)) {
            return StackRefusal.VILLAGER_CLAIM;
        }
        if (entity instanceof EnderMan enderMan && enderMan.getCarriedBlock() != null) {
            return StackRefusal.CARRIES_BLOCK;
        }
        return null;
    }

    /** The claims a villager holds on the world: a bed, a workstation (or one it is walking to), a bell. */
    private static final List<MemoryModuleType<GlobalPos>> VILLAGER_CLAIMS = List.of(
            MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE,
            MemoryModuleType.MEETING_POINT);

    private static boolean hasVillagerClaim(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        for (MemoryModuleType<GlobalPos> claim : VILLAGER_CLAIMS) {
            if (brain.hasMemoryValue(claim)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A boss, for {@code stackBosses}: anything with a boss bar of its own, anything in
     * {@code #mobstacker:bosses}, and any kind whose base max health reaches
     * {@code bossHealthThreshold} (0 turns that last test off).
     */
    public static boolean isBoss(Mob mob) {
        if (mob.getType().is(BOSSES_TAG) || isBossEntity(mob)) {
            return true;
        }
        int threshold = getBossHealthThreshold(mob);
        return threshold > 0 && baseMaxHealth(mob.getType()) >= threshold;
    }

    @SuppressWarnings("unchecked")
    private static double baseMaxHealth(EntityType<?> type) {
        return BASE_MAX_HEALTH.computeIfAbsent(type, t -> {
            try {
                if (DefaultAttributes.hasSupplier(t)) {
                    AttributeSupplier attributes = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) t);
                    if (attributes.hasAttribute(Attributes.MAX_HEALTH)) {
                        return attributes.getBaseValue(Attributes.MAX_HEALTH);
                    }
                }
            } catch (RuntimeException e) {
                logger.debug("MobStacker: no default attributes for {}", t, e);
            }
            return 0.0;
        });
    }

    /**
     * A stacked villager that has claimed a bed, a workstation or a bell, or a stacked enderman
     * that has picked up a block, hands that over to one member, which leaves the stack with it
     * (1.12.0). The stack is one entity, so it is the stack that claims or picks up - and every
     * member would then share the one bed, the one block, or one workstation's trades. Taking it
     * out at once keeps trading halls, breeders and iron farms counting villagers one by one, and
     * stops a block being carried by twenty endermen.
     *
     * <p>On a timer of its own rather than the stack scan's, which may be switched off.
     */
    private static void shedClaims(Mob mob) {
        if (!(mob instanceof Villager) && !(mob instanceof EnderMan)) {
            return;
        }
        if ((mob.tickCount + mob.getId()) % SHED_INTERVAL != 0 || getStackSize(mob) <= 1) {
            return;
        }
        if (mob instanceof EnderMan stack) {
            BlockState carried = stack.getCarriedBlock();
            if (carried == null) {
                return;
            }
            if (separateOne(stack, false) instanceof EnderMan leaving) {
                leaving.setCarriedBlock(carried);
                stack.setCarriedBlock(null);
            }
            return;
        }
        Villager stack = (Villager) mob;
        if (!hasVillagerClaim(stack)) {
            return;
        }
        if (!(separateOne(stack, false) instanceof Villager leaving)) {
            return;
        }
        Brain<Villager> from = stack.getBrain();
        Brain<Villager> to = leaving.getBrain();
        for (MemoryModuleType<GlobalPos> claim : VILLAGER_CLAIMS) {
            from.getMemory(claim).ifPresent(pos -> to.setMemory(claim, pos));
            from.eraseMemory(claim);
        }
        // Caught after the job was already taken (a workstation right next to the stack): the
        // profession, its trades and its experience go with the claim, and the rest of the stack is
        // jobless again, as it was before.
        if (stack.getVillagerData().getProfession() != VillagerProfession.NONE) {
            leaving.setVillagerData(stack.getVillagerData());
            AbstractVillagerAccessor stackTrades = (AbstractVillagerAccessor) stack;
            ((AbstractVillagerAccessor) leaving).mobstacker$setOffers(stackTrades.mobstacker$offers());
            leaving.setVillagerXp(stack.getVillagerXp());
            stack.setVillagerData(stack.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
            stackTrades.mobstacker$setOffers(null);
            stack.setVillagerXp(0);
        }
    }

    /**
     * @return true if the mob holds an item or wears any armor (any non-empty equipment slot).
     */
    public static boolean hasEquipment(Mob entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!entity.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code source} may be merged away into {@code target}.
     *
     * <p>The two mobs are interchangeable in nearly everything this asks, but not quite: the ceiling
     * that counts is the one where the <em>survivor</em> stands, and a player-given name only has to
     * be protected on the mob being merged away, because the survivor keeps its own either way.
     * Which of the two is which is decided by {@link #tryMergeIntoNearbyStack}, not by which one
     * happened to move.
     */
    public static boolean canMerge(Mob source, Mob target) {
        return mergeRefusal(source, target) == null;
    }

    /** Why {@code source} may not be merged away into {@code target}, or null when it may. See {@link #canMerge}. */
    public static StackRefusal mergeRefusal(Mob source, Mob target) {
        if (source.getClass() != target.getClass()) {
            return StackRefusal.DIFFERENT_KIND;
        }
        if (!getCanStack(source) || !getCanStack(target)) {
            return StackRefusal.CAN_STACK_OFF;
        }

        // Never merge into / from a dying or removed mob (see canStack) — guards the
        // destructive mergeEntities call against the death-animation loop.
        if (source.isDeadOrDying() || target.isDeadOrDying() || source.isRemoved() || target.isRemoved()) {
            return StackRefusal.DYING;
        }

        // In long: a ceiling may be as large as an int goes, and two stacks near it would overflow
        // to a negative and read as "plenty of room".
        if (((long) getStackSize(source) + getStackSize(target)) > getMaxMobStackSize(target)) {
            return StackRefusal.TOO_BIG_TOGETHER;
        }

        // Never mix a baby with an adult (they carry different growth state). Two babies may
        // merge freely; mergeEntities keeps the youngest age so none grows up early.
        if (source.isBaby() != target.isBaby()) {
            return StackRefusal.BABY_AND_ADULT;
        }

        // The stack shows one name, and the survivor keeps its own, so a mob carrying a different
        // player-given name must not be merged away into it - that name would simply vanish.
        Component ownName = playerGivenName(source);
        if (ownName != null && !ownName.equals(playerGivenName(target))) {
            return StackRefusal.DIFFERENT_NAME;
        }

        if (!MobVariants.sameVariant(source, target)) {
            return StackRefusal.DIFFERENT_VARIANT;
        }

        // Last of the mod's own rules, being the one that has to serialise both mobs.
        if (getStrictDataComparison(target) && dataDifference(source, target) != null) {
            return StackRefusal.DIFFERENT_DATA;
        }

        return MobStackerAPI.checkCustomMergingConditions(source, target) ? null : StackRefusal.OTHER_MOD;
    }

    public static void spawnNewEntity(ServerLevel serverLevel, Mob self, int stackSize) {
        // Backwards-compatible overload: spawn the remainder of the stack at full health.
        spawnNewEntity(serverLevel, self, stackSize - 1, -1.0F);
    }

    /**
     * Spawns the surviving remainder of a stack after some mobs were killed.
     *
     * @param newStackSize   the stack size the spawned mob should carry (number of survivors)
     * @param survivorHealth health to apply to the spawned mob, or a non-positive value to keep it
     *                       at full health. Used by damage overflow so a partially-damaging hit
     *                       leaves the next mob wounded instead of fully healed.
     */
    public static void spawnNewEntity(ServerLevel serverLevel, Mob self, int newStackSize, float survivorHealth) {
        if (newStackSize < 1) return;

        Mob newEntity = memberOf(self, serverLevel, true);
        if (newEntity == null) return;

        MobStackerAPI.applyEntityDataModifiers(self, newEntity);
        copyStackNaming(self, newEntity);
        // The member that takes over the top wears its own stored gear, not a copy of the top's.
        handOverMemberEquipment(self, newEntity, newStackSize);
        // The same for a horse's own numbers.
        MobVariants.handOverMemberRolls(self, newEntity, newStackSize);
        MobStacker.setStackSize(newEntity, newStackSize);
        handOverMemberOrigins(self, newEntity, newStackSize);
        if (newStackSize > 1) {
            // The mobs under the new top one are the same wounded members as before the kill.
            MobStacker.setStackMemberDamage(newEntity, getStackMemberDamage(self));
        }
        copyBreedData(self, newEntity, newStackSize);
        if (survivorHealth > 0.0F) {
            // What carries over is the next member's wound. Overflow worked it out against the top
            // mob's max health, and the copy can have less: the top's leader bonus stays with it.
            float wound = self.getMaxHealth() - survivorHealth;
            newEntity.setHealth(Math.max(0.5F, Math.min(newEntity.getMaxHealth(), newEntity.getMaxHealth() - wound)));
        }
        serverLevel.addFreshEntity(newEntity);
    }

    // --- A mob made out of a stack ---------------------------------------------------------------
    // Since 1.12.0 (ROADMAP #35) a mob the mod makes out of a stack is a copy of the stack's own saved
    // data. Up to 1.11 it was a new mob put through vanilla's finalizeSpawn and patched afterwards,
    // and finalizeSpawn rolls a new mob: a variant, an age, gear, a zombie's leader bonus, a turtle's
    // home where it stands, a skeleton on a spider's back, a zombified piglin and a saddle on a
    // strider, a chicken for a baby zombie. Each of those had to be found and taken back one at a
    // time (1.7.0 variants, 1.9.0 horse stats, 1.9.3 riders and effects), and the rest went on being
    // re-rolled - so a stack of zombies on Hard could lose its top and come back in armour, which
    // then stopped it taking in any more zombies. A copy rolls nothing: the member that comes out
    // is what the stack is.

    /**
     * Save keys that belong to the one mob a stack shows and never to the members under it, so they
     * stay out of a copy. Everything else the stack has, every member has: that was already true of
     * a merge, where the survivor takes the data of the mob it swallows.
     */
    private static final Set<String> TOP_ONLY_KEYS = Set.of(
            // Who it is: its own id, whatever rides it, and the mod's stack data, which the callers
            // hand over member by member (size, gear, horse stats, origins, wounds, names).
            "UUID", "Passengers", STACK_DATA_KEY,
            // How it is doing: a member comes out whole, not hurt or dying.
            "Health", "AbsorptionAmount", "HurtTime", "HurtByTimestamp", "DeathTime",
            // What it remembers - a villager's bed and job site are claims on the world, released
            // when it dies (see shedClaims) - where it sleeps, and what it is tied to.
            "Brain", "SleepingX", "SleepingY", "SleepingZ", "Leash",
            // What it carries: one inventory, one saddle, one chest, one block. A copy would be a
            // second one, dropped again when the copy dies.
            "Inventory", "Items", "ChestedHorse", "SaddleItem", "ArmorItem", "DecorItem", "Saddle",
            "carriedBlockState",
            // What it is in the middle of: love (the stack's breeding is counted in its stack data),
            // a turtle's egg, a creeper's lit fuse - a copy of that one would blow the stack up one
            // creeper at a time.
            "InLove", "LoveCause", "HasEgg", "ignited");

    /**
     * Attribute modifiers vanilla gives the odd mob when it spawns, which a copy would give every
     * member: a leader zombie (at most one in twenty, on Hard) has two to five times the health and
     * calls in reinforcements. The members' own rolls were never kept, and nearly all of them were
     * plain zombies.
     */
    private static final Set<String> TOP_ONLY_MODIFIERS = Set.of("Leader zombie bonus");

    /** What a slot's drop chance is on a mob that never picked anything up. */
    private static final float NATURAL_DROP_CHANCE = 0.085F;

    /**
     * A new mob made out of a stack: the remainder that takes over when the top mob dies
     * ({@code remainder}), or one member taken out of it. It is the stack's own saved data loaded into
     * a fresh entity of the same kind, less what is the top mob's alone ({@link #TOP_ONLY_KEYS}) - so
     * it keeps the stack's variant, age, effects, fire, scoreboard tags and team, a turtle's home
     * beach, a zombie's conversion and other mods' data. Not yet in the world; the caller hands over
     * the member's own stored state and adds it.
     *
     * <p>Gear: what the top mob picked up (guaranteed to drop) is its own and was dropped when it
     * died, so a copy leaves it out; what the kind spawns with comes along. With {@code
     * keepMemberEquipment} the caller then puts the member's own loadout on instead.
     *
     * @param remainder true for the remainder, which keeps the stack's name; a member taken out of
     *                  a stack is not the stack the player named
     * @return the new mob, or null when its kind cannot be created
     */
    public static Mob memberOf(Mob stack, ServerLevel level, boolean remainder) {
        if (!(stack.getType().create(level) instanceof Mob member)) {
            return null;
        }
        AttributeInstance freshMaxHealth = member.getAttribute(Attributes.MAX_HEALTH);
        double oneMobsMaxHealth = freshMaxHealth == null ? 0.0 : freshMaxHealth.getBaseValue();

        CompoundTag data = stack.saveWithoutId(new CompoundTag());
        // Before the copy: the save holds the stack's live stack data, not a copy of it.
        data.remove(STACK_DATA_KEY);
        data = data.copy();
        TOP_ONLY_KEYS.forEach(data::remove);
        if (!remainder) {
            data.remove("CustomName");
            data.remove("CustomNameVisible");
        }
        ListTag attributes = data.getList("Attributes", Tag.TAG_COMPOUND);
        for (int i = 0; i < attributes.size(); i++) {
            attributes.getCompound(i).getList("Modifiers", Tag.TAG_COMPOUND).removeIf(modifier ->
                    modifier instanceof CompoundTag tag && TOP_ONLY_MODIFIERS.contains(tag.getString("Name")));
        }
        try {
            member.load(data);
        } catch (RuntimeException e) {
            // Another mod's data that does not load back: a plain mob of the kind, rather than the
            // stack losing its members or the server its tick.
            logger.warn("Could not copy a stacked {} from its saved data, making a plain one: {}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(stack.getType()), e.toString());
            if (!(stack.getType().create(level) instanceof Mob plain)) {
                return null;
            }
            member = plain;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (dropChance(member, slot) > 1.0F) {
                member.setItemSlot(slot, ItemStack.EMPTY);
                setDropChance(member, slot, NATURAL_DROP_CHANCE);
            }
        }
        AttributeInstance maxHealth = member.getAttribute(Attributes.MAX_HEALTH);
        if (getStackHealth(stack) && maxHealth != null) {
            // stackHealth raised the stack's max health by every mob it took in; a member has one mob's.
            maxHealth.setBaseValue(oneMobsMaxHealth);
        }
        member.setHealth(member.getMaxHealth());
        if (member instanceof Villager villager && villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
            // Only jobless villagers stack, so a job the stack took on is the top villager's (shedClaims
            // hands it to the one it takes out). A new profession also clears the trades.
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
            villager.setVillagerXp(0);
        }

        copyOrigin(stack, member);
        member.moveTo(stack.getX(), stack.getY(), stack.getZ(), stack.getYRot(), stack.getXRot());
        member.yBodyRot = stack.yBodyRot;
        // The head too: load() sets it to the saved facing, but a stack without AI (stackAi) keeps
        // whatever it is given, and its body slowly turned after a head that faced elsewhere.
        member.setYHeadRot(stack.getYHeadRot());
        return member;
    }

    public static void separateEntity(Mob entity) {
        separateOne(entity, true);
    }

    /**
     * How many extra times a harvest (shearing, milking) has to be repeated for a stack.
     *
     * <p>Vanilla has already given the player one mob's worth by the time the mod gets involved, so
     * this is the stack size <em>minus one</em>. Getting that wrong is not theoretical: both shear
     * mixins looped the full stack size on top of vanilla's own, which quietly gave a lone sheep
     * double wool and a stack of sixteen seventeen mobs' worth.
     *
     * @return 0 when nothing extra is owed, so a caller can skip the loop entirely.
     */
    public static int extraHarvests(Mob mob) {
        if (!getStackedHarvest(mob)) {
            return 0;
        }
        return Math.max(0, getStackSize(mob) - 1);
    }

    /**
     * Fills a bucket for every member of a stack below the top one, as far as the player's buckets
     * go. Called before vanilla milks the top mob, which is why one bucket is always left behind:
     * taking the last one would leave vanilla with nothing to work with and swallow a milking.
     *
     * <p>Creative mode consumes nothing, matching what vanilla's own bucket handling does there.
     */
    public static void milkExtraMembers(Mob self, Player player) {
        int extra = extraHarvests(self);
        if (extra <= 0) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        int take = creative ? extra : Math.min(extra, countItem(player, Items.BUCKET) - 1);
        if (take <= 0) {
            return;
        }
        if (!creative) {
            takeItem(player, Items.BUCKET, take);
        }
        for (int i = 0; i < take; i++) {
            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
            if (!player.getInventory().add(milk)) {
                player.drop(milk, false);
            }
        }
    }

    private static int countItem(Player player, Item item) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static void takeItem(Player player, Item item, int count) {
        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                return;
            }
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (left <= 0) {
                return;
            }
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /**
     * Whether this item, offered to this mob, is an attempt to tame it.
     *
     * <p>Asked so that a stacked pack can hand over one animal instead of being tamed sixteen at a
     * time. It has to be this specific: unlike a horse, which answers every right-click with
     * something, an untamed wolf ignores an empty hand entirely — peeling a wolf off the pack for a
     * click that would have done nothing would be worse than the bug being fixed.
     *
     * <p>Only the three vanilla can tame. A wolf that is already angry cannot be tamed at all, and a
     * tamed one is no longer in a stack to begin with (see {@link #isPlayerBound}).
     */
    public static boolean isTamingInteraction(Mob mob, ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        if (mob instanceof Wolf wolf) {
            return !wolf.isTame() && !wolf.isAngry() && held.is(Items.BONE);
        }
        if (mob instanceof Cat cat) {
            return !cat.isTame() && cat.isFood(held);
        }
        if (mob instanceof Parrot parrot) {
            return !parrot.isTame() && ParrotAccessor.mobstacker$tameFood().contains(held.getItem());
        }
        return false;
    }

    /**
     * Whether this is somebody scooping one mob into a bucket.
     *
     * <p>Bucketing destroys the entity and hands back an item holding it, which on a stack meant
     * sixteen fish going into one bucket and only the label coming out the other side. So it joins
     * the "hand one animal over" rule: the stack gives up a single fish, vanilla buckets that one,
     * and the rest stay where they are.
     */
    public static boolean isBucketingInteraction(Mob mob, ItemStack held) {
        return mob instanceof Bucketable && held.is(Items.WATER_BUCKET);
    }

    /**
     * Whether this is shears on a stack that is harvested one mob at a time.
     *
     * <p>With {@code stackedHarvest} off a stack gives what a single mob would, and for milking that
     * is the whole story. Shearing is different: it changes the mob, and the stack is one mob - so
     * after one click all sixteen sheep stood there sheared, having given one sheep's wool between
     * them. A stacked mooshroom was worse, turning into sixteen cows. So with the setting off the
     * stack hands one animal over and that one is sheared, like taming and bucketing; the rest keep
     * their wool for the next click. A sheared sheep never merges back into an unsheared stack
     * (see {@code MobVariants}), so the two stay apart until the wool grows back.
     */
    public static boolean isShearingOneInteraction(Mob mob, ItemStack held) {
        return !shearingOutsideMobInteract
                && held.is(Items.SHEARS) && mob instanceof Shearable shearable && shearable.readyForShearing()
                && !getStackedHarvest(mob);
    }

    /**
     * Set by a loader whose shears do not shear in {@code mobInteract}. Forge moves it into
     * {@code IForgeShearable.onSheared}, which {@code ShearsItem} calls after the mob's own interaction
     * has passed - so handing a mob over there would leave the shears to shear the stack anyway. Its
     * own mixin applies the same one-at-a-time rule inside {@code onSheared} instead.
     */
    public static boolean shearingOutsideMobInteract;

    /**
     * Has every client that can see this mob forget it and be sent it afresh, the way the server
     * sends a mob to a player walking into range.
     *
     * <p>For the one interaction a client acts out before the server has answered and cannot take
     * back: filling a bucket. Vanilla discards the mob on the client the moment it is clicked,
     * trusting the server to do the same. On a stack the server does not - it hands one fish over and
     * keeps the shoal - and nothing ever told the client the shoal was still there. It went on
     * existing out of sight: it merged, swallowed the fish poured back next to it, and reappeared
     * only when something sent it again - a relog, the chunk reloading, or a {@code /kill} that
     * left survivors to respawn.
     */
    public static void resendToClients(Mob mob) {
        if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        level.getChunkSource().removeEntity(mob);
        level.getChunkSource().addEntity(mob);
    }

    /**
     * Whether this mob could actually breed if it were fed.
     *
     * <p>Vanilla lets an untamed wolf fall in love and then refuses to let it mate, which is a
     * contradiction the stack breeding code could not see: it counts fed members and spawns babies
     * without ever asking {@code canMate}. So a stack of wild wolves fed meat produced puppies that
     * vanilla would never have given.
     */
    public static boolean canEverBreed(Animal animal) {
        return !(animal instanceof TamableAnimal tamable) || tamable.isTame();
    }

    /**
     * Whether this item, used on this mob, is the separator taking one mob out of the stack.
     *
     * <p>Asked by {@code MobMixin} so that a separator click on a stacked mount does not pull out
     * two: {@code PlayerMixin} separates at {@code Player.interactOn} and lets vanilla carry on, so
     * without this the interaction would then reach a stack that is already one smaller and split
     * it again.
     */
    public static boolean isSeparatorInteraction(Mob entity, ItemStack held) {
        if (held.isEmpty() || !getEnableSeparator(entity)) {
            return false;
        }
        ResourceLocation separator = ResourceLocation.tryParse(getSeparatorItem(entity));
        return separator != null && held.is(BuiltInRegistries.ITEM.get(separator));
    }

    /**
     * Takes a single mob out of a stack and returns it.
     *
     * @param markAsSeparated whether to give the mob the "Lone ..." name. The separator item wants
     *                        it: the player pulled that mob out deliberately and it would otherwise
     *                        walk straight back into the stack on the next scan. A mob pulled out to
     *                        receive an interaction (see {@code MobMixin}) does not — it is only
     *                        standing in for the stack, and rejoining it afterwards is the point.
     * @return the separated mob, or null if it could not be created.
     */
    public static Mob separateOne(Mob entity, boolean markAsSeparated) {
        if (entity.level().isClientSide()) return null;

        try {
            ServerLevel serverLevel = (ServerLevel) entity.level();
            Mob newEntity = memberOf(entity, serverLevel, false);
            if (newEntity == null) return null;

            // Read while the stack still has the member that is about to leave it.
            Map<String, Integer> origins = memberOrigins(entity);
            setStackSize(entity, getStackSize(entity) - 1);

            if (markAsSeparated) {
                newEntity.setCustomName(Component.literal("Lone " + getLocalizedEntityName(entity.getType()).getString()));
            }
            handleHealthOnSeparation(entity, newEntity);
            // The separated mob is one of the stored members, so it leaves wearing that member's
            // gear; the stack keeps the rest.
            takeMemberEquipmentFor(entity, newEntity);
            // ...and, if it is a horse, with that member's own speed, jump and health.
            MobVariants.takeMemberRollsFor(entity, newEntity);
            // ...and with the origin that member came into the world with.
            takeMemberOriginFor(entity, newEntity, origins);

            // Apply custom entity data
            MobStackerAPI.applyEntityDataModifiersOnSeparation(entity, newEntity);
            markJustSeparated(newEntity);
            entity.level().addFreshEntity(newEntity);
            return newEntity;

        } catch (Exception e) {
            setStackSize(entity, getStackSize(entity) + 1);
            logger.error("Error occurred while separating entity: {}", e.getMessage());
            return null;
        }
    }

    private static void handleHealthOnSeparation(Mob source, Mob target) {
        if (getStackHealth(source)) {
            // A pooled health bar gives up one mob's worth, and so does its ceiling: the merge raised
            // the stack's max health by every mob it took in, and a stack split back down to its last
            // mob kept all of it - one cow with 640 max health.
            AttributeInstance maxHealth = source.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null && maxHealth.getBaseValue() - target.getMaxHealth() >= target.getMaxHealth()) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() - target.getMaxHealth());
            }
            if (source.getHealth() > target.getMaxHealth()) {
                source.setHealth(source.getHealth() - target.getMaxHealth());
            }
            source.setHealth(Math.min(source.getHealth(), source.getMaxHealth()));
            return;
        }
        // Every mob under the top one carries the same wound (sweeps, falls, fire), and the one taken
        // out is one of them, so it leaves with it rather than healed.
        float wound = getStackMemberDamage(source);
        if (wound > 0.0F) {
            target.setHealth(Math.max(0.5F, target.getMaxHealth() - wound));
        }
    }

    /**
     * Looks for a mob standing near this one that the two of them may merge into, and merges them.
     *
     * <p><strong>The bigger stack always survives</strong>, whichever of the two set the merge off.
     * A merge is driven by whichever mob moved or scanned first, and the survivor keeps its own
     * position — so when a stack wandered into a lone cow, the cow won and the stack appeared to
     * teleport a block or two sideways. Equal sizes keep the mob that was found, which is what a mob
     * walking into a stack has always done.
     *
     * @return true when {@code self} was merged away and no longer exists
     */
    public static boolean tryMergeIntoNearbyStack(Mob self) {
        for (Entity entity : self.level().getEntities(self, self.getBoundingBox().inflate(getStackRadius(self)),
                candidate -> candidate instanceof Mob && canStack((Mob) candidate))) {
            Mob nearby = (Mob) entity;
            boolean keepSelf = getStackSize(self) > getStackSize(nearby);
            Mob target = keepSelf ? self : nearby;
            Mob source = keepSelf ? nearby : self;
            if (!canMerge(source, target)) {
                continue;
            }
            mergeEntities(target, source);
            return !keepSelf;
        }
        return false;
    }

    /**
     * Periodic re-check for mobs that are already standing together.
     * <p>
     * Merging is normally driven by a mob crossing a block boundary, which misses every mob that
     * simply never moves: several spawn eggs used on the same block, mobs with no AI, a stuck
     * spawner batch, or anything penned in place. Those used to sit side by side unstacked until
     * something nudged them. Each mob therefore also re-checks on a timer, staggered by entity id so
     * the whole world never scans on the same tick, and skipped entirely for a mob that is already
     * at the maximum stack size (it could not merge with anything anyway).
     * <p>
     * The same pass refreshes an existing stack's name, so display settings such as the name colour
     * take effect on stacks that are already standing in the world rather than only on the next
     * merge.
     */
    public static void tickStackScan(Mob mob) {
        if (mob.level().isClientSide()) {
            return;
        }
        // A mob's very first tick is the earliest moment it can safely be merged: by now finalizeSpawn
        // has run and its variant, age and equipment are settled, which is not true while the entity
        // is still being added to the level. Doing it here rather than on addFreshEntity is what lets
        // a spawner batch, a bred baby and a spawn egg all be covered by one check.
        //
        // tickCount is not saved with the entity, so a mob coming back with its chunk takes this path
        // too and its stack re-forms at once instead of over the next scan interval. That is the same
        // work the scan would have done anyway, just sooner; the flag read stays inside this branch
        // because everything here runs for every mob in the world.
        if (mob.tickCount <= 1) {
            boolean justSeparated = takeJustSeparated(mob);
            if (!justSeparated && getStackOnSpawn(mob) && getCanStack(mob) && canStack(mob)
                    && tryMergeIntoNearbyStack(mob)) {
                return; // merged away: this mob no longer exists to be scanned
            }
        }

        // Cheap: an int out of a tag the mob already carries, and it short-circuits to nothing for
        // every mob that was never separated, which is very nearly all of them.
        tickSeparationGrace(mob);
        // Two instanceof checks for every other mob.
        shedClaims(mob);

        int interval = getStackScanInterval(mob);
        if (interval <= 0) {
            return;
        }
        if ((mob.tickCount + mob.getId()) % interval != 0) {
            return;
        }
        int stackSize = getStackSize(mob);
        if (stackSize > 1) {
            // Also keeps the name in step with the display settings, so a colour change shows up on
            // stacks that already exist. The component only reaches clients when it really differs.
            updateStackDisplay(mob);
        }
        if (stackSize >= getMaxMobStackSize(mob)) {
            return;
        }
        if (!getCanStack(mob) || !canStack(mob)) {
            return;
        }
        tryMergeIntoNearbyStack(mob);
    }

    public static void mergeEntities(Mob target, Mob source) {
        int newStackSize = (int) Math.min((long) getStackSize(target) + getStackSize(source),
                getMaxMobStackSize(target));

        // When two babies merge, keep the youngest (most negative) age so no member ever grows up
        // early — this replaces a strict age-band gate and lets baby-stacks freely consolidate.
        Integer mergedBabyAge = null;
        if (target.isBaby() && source.isBaby()
                && target instanceof AgeableMob targetAge && source instanceof AgeableMob sourceAge) {
            mergedBabyAge = Math.min(targetAge.getAge(), sourceAge.getAge());
        }

        CompoundTag targetNbt = new CompoundTag();
        target.saveWithoutId(targetNbt);

        // With keepMemberEquipment the stack remembers what the mob being merged away was wearing
        // (and everything its own members were wearing), so nothing has to be thrown on the floor.
        // Each horse's own numbers are kept the same way, whatever the equipment settings say:
        // they are the horse, not something it carries.
        ListTag mergedRolls = MobVariants.mergedMemberRolls(target, source);
        // Every member keeps its own origin, or a stack of wild cows with one bred calf in it would
        // hand out nothing but wild cows when split, and a stack would stand for origins it refuses.
        Map<String, Integer> mergedOrigins = memberOrigins(target);
        mergedOrigins.merge(originKey(source), 1, Integer::sum);
        memberOrigins(source).forEach((origin, count) -> mergedOrigins.merge(origin, count, Integer::sum));
        // The survivor stays where it stands and keeps facing the way it faced. load() below sets the
        // head and body to the saved facing, and the source's facing used to come along in its save
        // data, so a stack without AI turned round every time a mob joined it.
        float headRot = target.getYHeadRot();
        float bodyRot = target.yBodyRot;
        ListTag mergedLoadouts = null;
        if (keepsMemberEquipment(target)) {
            mergedLoadouts = new ListTag();
            mergedLoadouts.addAll(getMemberLoadouts(target));
            mergedLoadouts.add(captureLoadout(source));
            mergedLoadouts.addAll(getMemberLoadouts(source));
        } else {
            dropPickedEquipment(source);
            dropPickedEquipment(target);
        }

        copyRelevantNbtData(source, targetNbt);

        updateStackDataInNbt(targetNbt, newStackSize);

        target.load(targetNbt);
        target.setYHeadRot(headRot);
        target.yBodyRot = bodyRot;

        updateHealth(target, source);

        if (mergedBabyAge != null && target instanceof AgeableMob mergedAge) {
            mergedAge.setAge(mergedBabyAge);
        }

        if (mergedLoadouts != null) {
            // After load(), which put the target's own stack data back from the snapshot above.
            setMemberLoadouts(target, mergedLoadouts);
        }
        MobVariants.setMergedMemberRolls(target, mergedRolls, newStackSize);
        storeMemberOrigins(target, mergedOrigins);

        source.discard();

        updateStackDisplay(target);
    }

    private static void copyRelevantNbtData(Mob source, CompoundTag targetNbt) {
        CompoundTag sourceNbt = new CompoundTag();
        source.saveWithoutId(sourceNbt);

        sourceNbt.getAllKeys().stream()
                .filter(key -> !isExcludedNbtKey(key))
                .forEach(key -> targetNbt.put(key, sourceNbt.get(key)));
    }

    private static boolean isExcludedNbtKey(String key) {
        // The merge target is the surviving stack, so its own stack data (StackSize, CanStack and
        // the breeding cooldown) and attributes (e.g. the accumulated max health under stackHealth)
        // must be kept, not overwritten by the discarded source. Stack size and health are then
        // recomputed explicitly (updateStackDataInNbt / updateHealth).
        return key.equals("Pos") || key.equals("UUID") ||
                key.equals("Motion") || key.equals("Rotation") || key.equals("Health") ||
                key.equals("Attributes") || key.equals(STACK_DATA_KEY) ||
                // What the stack wears and what it is called belong to the survivor: the mob being
                // merged away has its gear stored as a member loadout, and copying its name over
                // would rename a stack the player named ("Bella x16" -> "Cow x17").
                key.equals("ArmorItems") || key.equals("HandItems") ||
                key.equals("ArmorDropChances") || key.equals("HandDropChances") ||
                key.equals("CustomName") || key.equals("CustomNameVisible");
    }

    private static void updateStackDataInNbt(CompoundTag nbt, int stackSize) {
        CompoundTag stackData = nbt.contains(STACK_DATA_KEY, 10) ?
                nbt.getCompound(STACK_DATA_KEY) : new CompoundTag();
        stackData.putInt(STACK_SIZE_KEY, stackSize);
        nbt.put(STACK_DATA_KEY, stackData);
    }

    /**
     * The four names vanilla reacts to, and the only strings this mod is ever allowed to look for
     * behind a stack's count: "Dinnerbone" and "Grumm" turn a mob upside down, "jeb_" makes a sheep
     * cycle through the dye colours, "Toast" gives a rabbit the memorial skin.
     */
    private static final Set<String> VANILLA_NAME_EASTER_EGGS = Set.of("Dinnerbone", "Grumm", "jeb_", "Toast");

    /**
     * The name vanilla's easter-egg checks should see.
     *
     * <p>All four of them compare the mob's name to a literal with {@code equals}, so a stack called
     * "Dinnerbone x16" matches none of them and a named herd quietly loses the effect. This hands
     * those checks the bare name back.
     *
     * <p>It is the one place allowed past the rule that a name is never parsed back off the label,
     * and it stays inside it in the way that matters: nothing is derived and nothing is stored. The
     * question asked is only "is this one of four known literals with a count after it", and every
     * other name in the game is returned untouched, character for character. The worst it can do is
     * render a mob somebody deliberately called "Dinnerbone x3" upside down, which is presumably
     * what they were after.
     *
     * <p>Client side, and only useful there — the effects are all rendering.
     */
    public static Component easterEggName(LivingEntity entity) {
        Component label = entity.getName();
        if (!entity.hasCustomName()) {
            return label;
        }
        String raw = ChatFormatting.stripFormatting(label.getString());
        if (raw == null) {
            return label;
        }
        int split = raw.lastIndexOf(" x");
        if (split <= 0 || !VANILLA_NAME_EASTER_EGGS.contains(raw.substring(0, split))) {
            return label;
        }
        String count = raw.substring(split + 2);
        if (count.isEmpty()) {
            return label;
        }
        for (int i = 0; i < count.length(); i++) {
            if (!Character.isDigit(count.charAt(i))) {
                return label;
            }
        }
        return Component.literal(raw.substring(0, split));
    }

    /**
     * Keeps what players see of the stack in step with it: its count, colour and settings. Since
     * 1.12.0 nothing of that is written onto the mob - the label is made per player on its way out
     * ({@link StackLabels}) - so this only takes a label written by an older version back out of the
     * mob's own name, names a boss's bar, and tells the players watching when the label changed.
     */
    public static void updateStackDisplay(Mob entity) {
        if (entity.level().isClientSide) {
            return;
        }
        clearOldLabel(entity);
        if (isBossEntity(entity)) {
            ServerBossEvent bossEvent = getBossField(entity);
            if (bossEvent != null) {
                // A boss bar is one for everybody, so it is named in the server's words.
                bossEvent.setName(StackLabels.shows(entity) ? StackLabels.nameLabel(entity) : entity.getDisplayName());
            }
        }
        StackLabels.refresh(entity);
    }

    /**
     * Before 1.12.0 the label was the mob's own name: "Cow x12", or "Bessie x12" for a stack a player
     * named. This puts the mob's own name back - the player's exactly as they gave it, nothing for the
     * mod's own label - so a world from an older version comes clean as its stacks are loaded. The
     * count on any other name is the old label's too: the old display replaced it every time.
     */
    private static void clearOldLabel(Mob entity) {
        Component given = playerGivenName(entity);
        if (given != null) {
            if (!given.equals(entity.getCustomName())) {
                entity.setCustomName(given);
            }
            return;
        }
        Component current = entity.getCustomName();
        if (current == null) {
            return;
        }
        String text = current.getString();
        if (matchesStackedName(text, entity)) {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
            return;
        }
        java.util.regex.Matcher count = STACKED_NAME_PATTERN.matcher(text);
        if (count.find()) {
            entity.setCustomName(Component.literal(text.substring(0, count.start())).withStyle(current.getStyle()));
        }
    }

    public static void updateHealth(Mob target, Mob source) {
        double maxHealth = target.getMaxHealth();
        float newHealth = target.getHealth() + source.getHealth();

        if (getStackHealth(target) && getKillWholeStackOnDeath(target)) {
            maxHealth += source.getMaxHealth();
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        }

        target.setHealth(Math.min(newHealth, (float) maxHealth));
    }

    public static void delayExecution(ServerLevel serverLevel, int ticksDelay, Runnable task) {
        serverLevel.getServer().execute(new DelayedTask(serverLevel, ticksDelay, task));
    }

    private static class DelayedTask implements Runnable {
        private final ServerLevel serverLevel;
        private final Runnable task;
        private int ticksLeft;

        DelayedTask(ServerLevel serverLevel, int ticksDelay, Runnable task) {
            this.serverLevel = serverLevel;
            this.ticksLeft = ticksDelay;
            this.task = task;
        }

        @Override
        public void run() {
            if (ticksLeft > 0) {
                ticksLeft--;
                serverLevel.getServer().execute(this);
            } else {
                task.run();
            }
        }
    }

    /**
     * Whether this mob's name leaves it free to stack.
     * <p>
     * A name tag records what the player meant (see {@link #onNameTagApplied}) instead of the mod
     * guessing it from the text: a tag put on a stack is a label for the whole stack, so it keeps
     * stacking for good, while a tag put on a single mob is how players protect a pet and keeps it
     * out of stacks unless {@code stackNamedMobs} says otherwise. Any other custom name is only
     * accepted when it is the mod's own "Cow x12" label.
     */
    public static boolean hasValidCustomNameForStacking(Mob entity) {
        if (!entity.hasCustomName()) {
            return true;
        }
        if (isPlayerNamed(entity)) {
            return isNamedStack(entity) || getStackNamedMobs(entity);
        }
        return matchesStackedName(entity.getCustomName().getString(), entity);
    }

    public static boolean matchesStackedName(String customName, Entity entity) {
        return Pattern.compile(Pattern.quote(getLocalizedEntityName(entity.getType()).getString())
                + " x\\d+").matcher(customName).find();
    }

    // --- Helpers formerly provided by the Almanac library, now in-house so the mod has no
    // --- external mod dependency. Behaviour is identical to Almanac 1.0.2.

    /** The mob's localized type name, e.g. "Cow" / "Zombie" (translatable, resolved server-side). */
    public static Component getLocalizedEntityName(EntityType<?> type) {
        return Component.translatable(type.getDescriptionId());
    }

    /**
     * True when the mob has no player-assigned name — i.e. it is either nameless or only shows our
     * injected "Name xN" stack label (as opposed to a real name tag). Used to keep name-tagged mobs
     * out of stacking and to hide the auto stack label.
     */
    public static boolean hasNonCustomName(LivingEntity entity) {
        if (entity instanceof Mob mob && isPlayerNamed(mob)) {
            return false; // a name tag is the player's own name, however much it looks like our label
        }
        return !entity.hasCustomName() || matchesStackedName(entity.getCustomName().getString(), entity);
    }

    /**
     * Drops any items the mob picked up during its life (tagged {@code picked} in NBT, minus
     * binding-cursed ones) and clears those slots, so picked-up loot isn't lost when the mob is
     * discarded on a merge. Naturally-equipped gear (no {@code picked} tag) is left untouched.
     */
    public static void dropPickedEquipment(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("picked") || EnchantmentHelper.hasBindingCurse(stack)) {
                continue;
            }
            tag.remove("picked");
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
            entity.spawnAtLocation(stack);
            entity.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    // --- Per-member equipment -------------------------------------------------------------------
    // A stack shows one mob, so everything the mobs underneath it wear and hold is kept with the
    // stack instead of on an entity. Each member's gear is stored exactly as the game stores it
    // (the item tag plus its drop chance), which is what makes modded items and enchantments work
    // without knowing anything about them, and it is put back on the entity just before that
    // member's death loot is rolled - so Looting, the drop chances and the damage vanilla rolls
    // onto dropped armor all apply by themselves.

    /** Suffix of the drop chance stored beside each piece of a loadout. */
    private static final String DROP_CHANCE_SUFFIX = "Chance";

    /** True when this stack remembers what each of its members wears and holds. */
    public static boolean keepsMemberEquipment(Mob mob) {
        return getStackEquippedMobs(mob) && getKeepMemberEquipment(mob);
    }

    /** Everything a mob currently wears and holds, with the drop chance of each piece. */
    public static CompoundTag captureLoadout(Mob mob) {
        CompoundTag loadout = new CompoundTag();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            loadout.put(slot.getName(), stack.save(new CompoundTag()));
            loadout.putFloat(slot.getName() + DROP_CHANCE_SUFFIX, dropChance(mob, slot));
        }
        return loadout;
    }

    /**
     * Puts a stored loadout back on a mob, emptying every slot the loadout does not mention - so a
     * member with nothing on it really is empty-handed, whatever the mob was carrying before.
     */
    public static void applyLoadout(Mob mob, CompoundTag loadout) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String key = slot.getName();
            boolean has = loadout != null && loadout.contains(key, 10);
            mob.setItemSlot(slot, has ? ItemStack.of(loadout.getCompound(key)) : ItemStack.EMPTY);
            if (loadout != null && loadout.contains(key + DROP_CHANCE_SUFFIX)) {
                setDropChance(mob, slot, loadout.getFloat(key + DROP_CHANCE_SUFFIX));
            }
        }
    }

    private static float dropChance(Mob mob, EquipmentSlot slot) {
        MobEquipmentAccessor chances = (MobEquipmentAccessor) mob;
        return slot.getType() == EquipmentSlot.Type.ARMOR
                ? chances.mobstacker$armorDropChances()[slot.getIndex()]
                : chances.mobstacker$handDropChances()[slot.getIndex()];
    }

    private static void setDropChance(Mob mob, EquipmentSlot slot, float chance) {
        MobEquipmentAccessor chances = (MobEquipmentAccessor) mob;
        float[] array = slot.getType() == EquipmentSlot.Type.ARMOR
                ? chances.mobstacker$armorDropChances()
                : chances.mobstacker$handDropChances();
        array[slot.getIndex()] = chance;
    }

    /** The loadouts of the members below the top mob, oldest first. Never null. */
    public static ListTag getMemberLoadouts(Mob mob) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return new ListTag();
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        return customData.contains(MEMBER_EQUIPMENT_KEY, 9)
                ? customData.getList(MEMBER_EQUIPMENT_KEY, 10) : new ListTag();
    }

    public static void setMemberLoadouts(Mob mob, ListTag loadouts) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return;
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        if (loadouts == null || loadouts.isEmpty()) {
            customData.remove(MEMBER_EQUIPMENT_KEY);
        } else {
            customData.put(MEMBER_EQUIPMENT_KEY, loadouts);
        }
    }

    /**
     * Takes the next member's gear off the stack and returns it, for the caller to put on the mob
     * whose death loot is about to be rolled.
     *
     * @return that member's loadout, or null when the stack has none stored
     */
    public static CompoundTag takeMemberLoadout(Mob mob) {
        if (!keepsMemberEquipment(mob)) {
            return null;
        }
        ListTag loadouts = getMemberLoadouts(mob);
        if (loadouts.isEmpty()) {
            return null;
        }
        CompoundTag first = loadouts.getCompound(0).copy();
        loadouts.remove(0);
        setMemberLoadouts(mob, loadouts);
        return first;
    }

    /**
     * Hands the surviving remainder of a stack the gear of the member that becomes its new top mob,
     * and the rest of the members' gear with it.
     */
    private static void handOverMemberEquipment(Mob source, Mob target, int newStackSize) {
        if (!keepsMemberEquipment(source)) {
            return;
        }
        ListTag loadouts = getMemberLoadouts(source);
        if (loadouts.isEmpty()) {
            return; // nothing was stored (the setting was off while this stack was built)
        }
        applyLoadout(target, loadouts.getCompound(0));
        ListTag rest = new ListTag();
        for (int i = 1; i < loadouts.size() && rest.size() < newStackSize - 1; i++) {
            rest.add(loadouts.getCompound(i).copy());
        }
        setMemberLoadouts(target, rest);
    }

    /** The mob pulled out of a stack by the separator item leaves wearing one member's gear. */
    private static void takeMemberEquipmentFor(Mob stack, Mob separated) {
        CompoundTag loadout = takeMemberLoadout(stack);
        if (loadout != null) {
            applyLoadout(separated, loadout);
        }
    }

    // --- Name tags ------------------------------------------------------------------------------
    // What a custom name means is recorded when the name tag is used, instead of being guessed from
    // the text afterwards. A tag on a stack labels the whole stack and leaves it stacking; a tag on
    // a single mob is how players protect a pet, and keeps it out of stacks.

    /** Called from {@code NameTagItemMixin} once a name tag has actually renamed a mob. */
    public static void onNameTagApplied(Mob mob, Component name) {
        if (!(mob instanceof ICustomDataHolder holder) || name == null) {
            return;
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        int stackSize = getStackSize(mob);
        customData.putBoolean(PLAYER_NAMED_KEY, true);
        if (stackSize > 1) {
            // Naming a stack names the stack, so it stays a stack for good - down to its last mob.
            customData.putBoolean(NAMED_STACK_KEY, true);
        }
        customData.putString(STACK_NAME_KEY, Component.Serializer.toJson(stripLiveCount(name, stackSize)));
        updateStackDisplay(mob);
    }

    /**
     * Drops a trailing " xN" when N is exactly the stack's current size, i.e. the player retyped the
     * label they could see. Any other " xN" is part of the name and is kept, so a cow deliberately
     * called "Cow x5" stays called that.
     */
    private static Component stripLiveCount(Component name, int stackSize) {
        String text = name.getString();
        String suffix = " x" + stackSize;
        if (stackSize > 1 && text.endsWith(suffix) && text.length() > suffix.length()) {
            return Component.literal(text.substring(0, text.length() - suffix.length()))
                    .withStyle(name.getStyle());
        }
        return name;
    }

    /**
     * Remembers that the mod put this mob here, so the stack-on-spawn pass does not merge it away on
     * the tick it appears, and - if {@code separationCooldown} asks for it - the scan leaves it alone
     * for a while after that. See {@link #SEPARATION_GRACE_KEY}.
     */
    private static void markJustSeparated(Mob mob) {
        if (mob instanceof ICustomDataHolder holder) {
            CompoundTag data = holder.mobstacker$getCustomData();
            data.putBoolean(JUST_SEPARATED_KEY, true);
            putSeparationGrace(mob, data);
        }
    }

    /** Starts the grace from the setting where the mob stands, or clears it when that is 0. */
    private static void putSeparationGrace(Mob mob, CompoundTag data) {
        int ticks = getSeparationCooldown(mob) * 20;
        if (ticks > 0) {
            data.putInt(SEPARATION_GRACE_KEY, ticks);
        } else {
            data.remove(SEPARATION_GRACE_KEY);
        }
    }

    /**
     * Whether this mob was just separated out of a stack by the mod, read on its first tick.
     *
     * <p>Kept alongside the grace countdown because they answer different questions: this one stops
     * the stack-on-spawn pass undoing a separation within the same tick, the countdown stops the
     * periodic scan undoing it over the next few seconds.
     */
    private static boolean takeJustSeparated(Mob mob) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return false;
        }
        CompoundTag data = holder.mobstacker$getCustomData();
        if (!data.getBoolean(JUST_SEPARATED_KEY)) {
            return false;
        }
        data.remove(JUST_SEPARATED_KEY);
        return true;
    }

    /**
     * Treats a mob that was just poured out of a bucket the way one the mod handed over is. The
     * player has just put that particular fish somewhere on purpose; with {@code stackOnSpawn} on it
     * vanished into the shoal beside it on its very first tick, which looked exactly like the bucket
     * having eaten it. So it is always seen for a moment, and joins a stack on the next scan - or
     * once {@code separationCooldown} runs out, if that is set.
     */
    public static void markPouredFromBucket(Mob mob) {
        markJustSeparated(mob);
    }

    /** Ticks still owed to the player who was handed this mob, or 0. */
    public static int separationGrace(Mob mob) {
        return mob instanceof ICustomDataHolder holder
                ? holder.mobstacker$getCustomData().getInt(SEPARATION_GRACE_KEY)
                : 0;
    }

    /** Counts the grace down by one tick and clears the key once it runs out. */
    private static void tickSeparationGrace(Mob mob) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return;
        }
        CompoundTag data = holder.mobstacker$getCustomData();
        int left = data.getInt(SEPARATION_GRACE_KEY);
        if (left <= 0) {
            return;
        }
        if (left <= 1) {
            data.remove(SEPARATION_GRACE_KEY);
        } else {
            data.putInt(SEPARATION_GRACE_KEY, left - 1);
        }
    }

    /**
     * Starts the grace again on a mob that already had one, because the player is still working on
     * it. Never starts one on a mob that did not come out of a stack: an animal nobody separated is
     * nobody's business but the scan's.
     */
    public static void refreshSeparationGrace(Mob mob) {
        if (mob instanceof ICustomDataHolder holder) {
            CompoundTag data = holder.mobstacker$getCustomData();
            if (data.getInt(SEPARATION_GRACE_KEY) > 0) {
                putSeparationGrace(mob, data);
            }
        }
    }

    public static boolean isPlayerNamed(Mob mob) {
        return mob instanceof ICustomDataHolder holder
                && holder.mobstacker$getCustomData().getBoolean(PLAYER_NAMED_KEY);
    }

    public static boolean isNamedStack(Mob mob) {
        return mob instanceof ICustomDataHolder holder
                && holder.mobstacker$getCustomData().getBoolean(NAMED_STACK_KEY);
    }

    /** The name the player gave this mob, without the " xN" count, or null when they gave none. */
    public static Component playerGivenName(Mob mob) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return null;
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        if (!customData.getBoolean(PLAYER_NAMED_KEY) || !customData.contains(STACK_NAME_KEY, 8)) {
            return null;
        }
        try {
            return Component.Serializer.fromJson(customData.getString(STACK_NAME_KEY));
        } catch (Exception e) {
            return null; // a name we can no longer read is no worse than no name at all
        }
    }

    /**
     * Carries a whole stack onto the mob that replaces it when it turns into another kind: a zombie
     * drowning, a pig or villager struck by lightning, a mooshroom sheared into a cow. Called once
     * vanilla has built the new mob, and after vanilla has copied the old one's name onto it.
     *
     * <p>That copy is the trap. When the old name was the mod's own label ("Mooshroom x10"), the new
     * mob carried it as an ordinary name - the wrong kind in it, and, not being its own label, read as
     * one a player gave, so the new stack refused every mob of its kind (round 7's server test). A
     * name a player gave stays, whether the name tag recorded it or it predates 1.7.0 and nothing did.
     */
    public static void carryStackInto(Mob source, Mob target) {
        setStackSize(target, getStackSize(source));
        copyOrigin(source, target);
        storeMemberOrigins(target, memberOrigins(source));
        if (target.hasCustomName() && source.hasCustomName() && hasNonCustomName(source)) {
            target.setCustomName(null);
        }
        copyStackNaming(source, target);
        updateStackDisplay(target);
    }

    /** Carries a player-given name onto a mob that replaces this one (a remainder, a conversion). */
    public static void copyStackNaming(Mob source, Mob target) {
        if (!(source instanceof ICustomDataHolder from) || !(target instanceof ICustomDataHolder to)) {
            return;
        }
        CompoundTag sourceData = from.mobstacker$getCustomData();
        if (!sourceData.getBoolean(PLAYER_NAMED_KEY)) {
            return;
        }
        CompoundTag targetData = to.mobstacker$getCustomData();
        targetData.putBoolean(PLAYER_NAMED_KEY, true);
        if (sourceData.getBoolean(NAMED_STACK_KEY)) {
            targetData.putBoolean(NAMED_STACK_KEY, true);
        }
        if (sourceData.contains(STACK_NAME_KEY, 8)) {
            targetData.putString(STACK_NAME_KEY, sourceData.getString(STACK_NAME_KEY));
        }
    }

    // --- Spawn origin ---------------------------------------------------------------------------

    /**
     * Remembers how a mob came into the world, the first time vanilla sets it up
     * ({@code finalizeSpawn}, see {@code MobMixin}). Never overwritten; a mob the mod itself makes out
     * of a stack never goes through {@code finalizeSpawn} and is handed its source's origin.
     */
    public static void recordOrigin(Mob mob, MobSpawnType reason) {
        if (mob instanceof ICustomDataHolder holder && reason != null
                && !holder.mobstacker$getCustomData().contains(ORIGIN_KEY, 8)) {
            holder.mobstacker$getCustomData().putString(ORIGIN_KEY, reason.name());
        }
    }

    /** Records an origin whatever was there, for a mob vanilla creates without {@code finalizeSpawn} (a bred baby). */
    public static void setOrigin(Mob mob, MobSpawnType reason) {
        if (mob instanceof ICustomDataHolder holder) {
            holder.mobstacker$getCustomData().putString(ORIGIN_KEY, reason.name());
        }
    }

    /** The recorded MobSpawnType name, or null when nothing was recorded. */
    public static String originOf(Mob mob) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return null;
        }
        CompoundTag data = holder.mobstacker$getCustomData();
        return data.contains(ORIGIN_KEY, 8) ? data.getString(ORIGIN_KEY) : null;
    }

    /** A mob the mod makes out of another (a remainder, a separated mob, a conversion) came from where that one did. */
    private static void copyOrigin(Mob source, Mob target) {
        if (!(target instanceof ICustomDataHolder holder)) {
            return;
        }
        String origin = originOf(source);
        if (origin == null) {
            holder.mobstacker$getCustomData().remove(ORIGIN_KEY);
        } else {
            holder.mobstacker$getCustomData().putString(ORIGIN_KEY, origin);
        }
    }

    /**
     * Whether this stack may stack where it stands, as far as origins go: the mob on top and every
     * member under it. A stack holding one mob of an origin that is switched off takes nobody else in,
     * the same as that mob standing alone would not.
     */
    public static boolean originStacks(Mob mob) {
        if (!originStacks(mob, originOf(mob))) {
            return false;
        }
        if (getStackSize(mob) > 1 && mob instanceof ICustomDataHolder holder
                && holder.mobstacker$getCustomData().contains(MEMBER_ORIGINS_KEY, Tag.TAG_COMPOUND)) {
            for (String origin : holder.mobstacker$getCustomData().getCompound(MEMBER_ORIGINS_KEY).getAllKeys()) {
                if (!originStacks(mob, NO_ORIGIN.equals(origin) ? null : origin)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether mobs with this recorded origin (null: none) may stack where {@code at} stands. */
    public static boolean originStacks(Mob at, String recorded) {
        return switch (SpawnOrigin.of(recorded)) {
            case NATURAL -> setting("stackNaturalMobs", at, config.getStackNaturalMobs());
            case SPAWNER -> setting("stackSpawnerMobs", at, config.getStackSpawnerMobs());
            case BRED -> setting("stackBredMobs", at, config.getStackBredMobs());
            case PLACED -> setting("stackPlacedMobs", at, config.getStackPlacedMobs());
        };
    }

    // Round 8: the members' origins were lost in a merge - the survivor kept its own and every mob
    // merged into it took that one, so a split stack handed out the wrong origins and a stack of wild
    // cows could quietly hold bred ones. They are counted rather than listed in order like equipment
    // and horse rolls: which member leaves first is something nobody can see, and a stack of a
    // thousand needs a handful of numbers rather than a thousand strings.

    /** Key in a stack's custom data: recorded origin -> how many members under the top came from it. */
    public static final String MEMBER_ORIGINS_KEY = "MemberOrigins";
    /** The {@link #MEMBER_ORIGINS_KEY} entry for members with nothing recorded (not a MobSpawnType name). */
    public static final String NO_ORIGIN = "NONE";

    private static String originKey(Mob mob) {
        String origin = originOf(mob);
        return origin == null ? NO_ORIGIN : origin;
    }

    private static void setOriginKey(Mob mob, String origin) {
        if (mob instanceof ICustomDataHolder holder) {
            if (NO_ORIGIN.equals(origin)) {
                holder.mobstacker$getCustomData().remove(ORIGIN_KEY);
            } else {
                holder.mobstacker$getCustomData().putString(ORIGIN_KEY, origin);
            }
        }
    }

    /**
     * The origins of the members under the top, as counts that add up to the stack size minus one. A
     * stack that recorded none - every member came from where the top did, or it formed before this
     * existed - is filled in with the top's own origin, which is what it always handed out.
     */
    public static Map<String, Integer> memberOrigins(Mob mob) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (mob instanceof ICustomDataHolder holder
                && holder.mobstacker$getCustomData().contains(MEMBER_ORIGINS_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag stored = holder.mobstacker$getCustomData().getCompound(MEMBER_ORIGINS_KEY);
            for (String origin : stored.getAllKeys()) {
                int count = stored.getInt(origin);
                if (count > 0) {
                    counts.merge(origin, count, Integer::sum);
                }
            }
        }
        fitMemberOrigins(counts, Math.max(0, getStackSize(mob) - 1), originKey(mob));
        return counts;
    }

    /** Pads with the top's origin, or takes the most numerous out, until the counts add up to {@code members}. */
    private static void fitMemberOrigins(Map<String, Integer> counts, int members, String top) {
        int total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        if (total < members) {
            counts.merge(top, members - total, Integer::sum);
        }
        for (; total > members; total--) {
            takeMostNumerous(counts, top);
        }
    }

    /** Stores the members' origins on a stack, or nothing at all when every one is the top's. */
    private static void storeMemberOrigins(Mob mob, Map<String, Integer> counts) {
        if (!(mob instanceof ICustomDataHolder holder)) {
            return;
        }
        String top = originKey(mob);
        fitMemberOrigins(counts, Math.max(0, getStackSize(mob) - 1), top);
        CompoundTag data = holder.mobstacker$getCustomData();
        if (counts.keySet().stream().allMatch(top::equals)) {
            data.remove(MEMBER_ORIGINS_KEY);
            return;
        }
        CompoundTag stored = new CompoundTag();
        counts.forEach((origin, count) -> stored.putInt(origin, count));
        data.put(MEMBER_ORIGINS_KEY, stored);
    }

    /** Takes one member of the most numerous origin out of the counts (ties: {@code preferred}). */
    private static String takeMostNumerous(Map<String, Integer> counts, String preferred) {
        String pick = null;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (pick == null || entry.getValue() > counts.get(pick)
                    || (entry.getValue().equals(counts.get(pick)) && entry.getKey().equals(preferred))) {
                pick = entry.getKey();
            }
        }
        if (pick == null) {
            return preferred;
        }
        counts.computeIfPresent(pick, (origin, count) -> count > 1 ? count - 1 : null);
        return pick;
    }

    /**
     * The mob taken out of a stack leaves with a member's origin, the odd ones out first: taking a
     * stack's few bred calves out one by one leaves a stack of nothing but wild cows, which may stack
     * again once {@code stackBredMobs} is off. {@code origins} was read before the stack shrank.
     */
    private static void takeMemberOriginFor(Mob stack, Mob separated, Map<String, Integer> origins) {
        String top = originKey(stack);
        Map<String, Integer> others = new LinkedHashMap<>(origins);
        others.remove(top);
        String taken = others.isEmpty() ? top : takeMostNumerous(others, top);
        origins.computeIfPresent(taken, (origin, count) -> count > 1 ? count - 1 : null);
        setOriginKey(separated, taken);
        storeMemberOrigins(stack, origins);
    }

    /**
     * After the top of a stack was killed (with however many overflow took under it): the new top is
     * one of the members that are left, and the rest keep their origins. Called once the remainder has
     * its stack size.
     */
    private static void handOverMemberOrigins(Mob dead, Mob survivor, int newStackSize) {
        String top = originKey(dead);
        Map<String, Integer> members = memberOrigins(dead);
        fitMemberOrigins(members, newStackSize, top); // the members killed with the top
        setOriginKey(survivor, takeMostNumerous(members, top));
        storeMemberOrigins(survivor, members);
    }

    // --- Stack breeding -------------------------------------------------------------------

    /**
     * Feeds a stacked ADULT animal its breeding food. Each fed member enters love; every pair of
     * in-love members produces one child, gathered into a baby-stack. Feeding costs one food item
     * per member (same as breeding them individually) and puts the bred members on a breeding
     * cooldown, while the rest of the stack can still be bred right away.
     *
     * @return the interaction result to hand back from {@code mobInteract}, or {@code null} to let
     *         vanilla handle it.
     */
    public static InteractionResult handleStackBreeding(Animal self, Player player, InteractionHand hand, ItemStack food) {
        if (!(self.level() instanceof ServerLevel level) || !(self instanceof ICustomDataHolder holder)) {
            return null;
        }
        CompoundTag data = holder.mobstacker$getCustomData();
        long now = level.getGameTime();
        int stackSize = getStackSize(self);

        int love = data.getInt(BREED_LOVE_KEY);
        int cooldownCount = data.getInt(BREED_COOLDOWN_COUNT_KEY);
        long cooldownEnd = data.getLong(BREED_COOLDOWN_END_KEY);
        if (now >= cooldownEnd) {
            cooldownCount = 0; // cooldown expired: every member is free to breed again
        }

        int free = stackSize - love - cooldownCount;
        if (free <= 0) {
            // Whole stack is either in love or still on cooldown; consume nothing and do nothing.
            return InteractionResult.PASS;
        }

        boolean creative = player.getAbilities().instabuild;
        // "One per click" mode feeds a single member per interaction (click once per animal);
        // otherwise a single click feeds as many members as the food in hand allows.
        int limit = getBreedOnePerClick(self) ? Math.min(1, free) : free;
        int toFeed = creative ? limit : Math.min(limit, food.getCount());
        if (toFeed <= 0) {
            return InteractionResult.PASS;
        }
        if (!creative) {
            food.shrink(toFeed);
        }

        love += toFeed;
        int pairs = love / 2;
        love %= 2;

        if (pairs > 0) {
            spawnOrMergeBabyStack(level, self, pairs);
            cooldownCount = Math.min(stackSize, cooldownCount + pairs * 2);
            cooldownEnd = now + BREED_COOLDOWN_TICKS;
            for (int i = 0; i < pairs; i++) {
                level.addFreshEntity(new ExperienceOrb(level, self.getX(), self.getY() + 0.5, self.getZ(),
                        self.getRandom().nextInt(7) + 1));
            }
        }

        data.putInt(BREED_LOVE_KEY, love);
        data.putInt(BREED_COOLDOWN_COUNT_KEY, cooldownCount);
        data.putLong(BREED_COOLDOWN_END_KEY, cooldownEnd);

        level.sendParticles(ParticleTypes.HEART, self.getX(), self.getY() + self.getBbHeight(), self.getZ(),
                Math.max(1, toFeed), self.getBbWidth(), 0.5, self.getBbWidth(), 0.1);
        return InteractionResult.SUCCESS;
    }

    /**
     * Feeds a stacked BABY its food to speed up growth, scaled fairly to the stack size (one food
     * item per baby rather than one item for the whole stack). Since a baby-stack is a single
     * entity with one shared age, the age is advanced once per fed member.
     */
    public static InteractionResult handleBabyStackFeeding(Animal self, Player player, InteractionHand hand, ItemStack food) {
        if (!(self.level() instanceof ServerLevel level)) {
            return null;
        }
        int stackSize = getStackSize(self);
        boolean creative = player.getAbilities().instabuild;
        int limit = getBreedOnePerClick(self) ? 1 : stackSize;
        int toFeed = creative ? limit : Math.min(limit, food.getCount());
        if (toFeed <= 0) {
            return InteractionResult.PASS;
        }
        if (!creative) {
            food.shrink(toFeed);
        }
        // Mirror vanilla's feeding speed-up (10% of the remaining time), applied once per member.
        for (int i = 0; i < toFeed && self.getAge() < 0; i++) {
            self.ageUp((int) ((float) (-self.getAge()) / 20.0F * 0.1F));
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ(),
                Math.max(1, toFeed), self.getBbWidth(), 0.4, self.getBbWidth(), 0.0);
        return InteractionResult.SUCCESS;
    }

    /**
     * Carries the breeding love/cooldown state from a stack onto a freshly-spawned remainder, so
     * killing part of a stack doesn't wipe the cooldown of the survivors (which had already bred).
     * Counts are clamped to the survivor count; the cooldown expiry is copied as-is.
     */
    private static void copyBreedData(Mob source, Mob target, int targetStackSize) {
        if (!(source instanceof ICustomDataHolder sourceHolder) || !(target instanceof ICustomDataHolder targetHolder)) {
            return;
        }
        CompoundTag src = sourceHolder.mobstacker$getCustomData();
        if (!src.contains(BREED_COOLDOWN_END_KEY)) {
            return;
        }
        CompoundTag dst = targetHolder.mobstacker$getCustomData();
        dst.putInt(BREED_LOVE_KEY, Math.min(src.getInt(BREED_LOVE_KEY), targetStackSize));
        dst.putInt(BREED_COOLDOWN_COUNT_KEY, Math.min(src.getInt(BREED_COOLDOWN_COUNT_KEY), targetStackSize));
        dst.putLong(BREED_COOLDOWN_END_KEY, src.getLong(BREED_COOLDOWN_END_KEY));
    }

    /**
     * Adds {@code count} freshly-bred babies to the world, first topping up any nearby baby-stack
     * of the same kind (so repeated breeding consolidates into the existing, slightly older stack),
     * then spawning the overflow as new baby-stack entities capped at the max stack size.
     */
    private static void spawnOrMergeBabyStack(ServerLevel level, Animal parent, int count) {
        int max = getMaxMobStackSize(parent);
        int remaining = count;

        for (Entity nearby : level.getEntities(parent, parent.getBoundingBox().inflate(getStackRadius(parent)),
                e -> e != parent && e.getClass() == parent.getClass() && ((Mob) e).isBaby())) {
            if (remaining <= 0) {
                break;
            }
            Mob existing = (Mob) nearby;
            if (!MobVariants.sameVariant(parent, existing)) {
                continue;
            }
            int room = max - getStackSize(existing);
            if (room <= 0) {
                continue;
            }
            int add = Math.min(room, remaining);
            setStackSize(existing, getStackSize(existing) + add);
            remaining -= add;
        }

        while (remaining > 0) {
            AgeableMob child = parent.getBreedOffspring(level, parent);
            if (child == null) {
                return;
            }
            int size = Math.min(max, remaining);
            child.setBaby(true);
            setOrigin(child, MobSpawnType.BREEDING);
            child.moveTo(parent.getX(), parent.getY(), parent.getZ(), parent.getYRot(), parent.getXRot());
            setStackSize(child, size);
            level.addFreshEntity(child);
            remaining -= size;
        }
    }

    public static boolean shouldSpawnNewEntity(Mob entity, Entity.RemovalReason reason) {
        return (entity instanceof Creeper creeper && creeper.isIgnited()) ||
                reason == Entity.RemovalReason.KILLED;
    }

    public static boolean isBossEntity(Entity entity) {
        if (!(entity instanceof Mob)) return false;

        return bossEntityCache.computeIfAbsent(entity.getClass(), clazz -> {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().equals(ServerBossEvent.class)) {
                    bossFieldCache.put(clazz, field);
                    return true;
                }
            }
            return false;
        });
    }

    public static ServerBossEvent getBossField(Entity entity) {
        Field field = bossFieldCache.get(entity.getClass());
        if (field == null) return null;

        try {
            field.setAccessible(true);
            return (ServerBossEvent) field.get(entity);
        } catch (IllegalAccessException e) {
            logger.error("Could not access ServerBossEvent field for entity: {} ", entity.getType(), e);
            return null;
        }
    }

    public static int getStackSize(Mob entity) {
        if (!(entity instanceof ICustomDataHolder holder)) {
            return 1;
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        return customData.contains(STACK_SIZE_KEY) ? customData.getInt(STACK_SIZE_KEY) : 1;
    }

    public static void setStackSize(Mob entity, int size) {
        if (entity instanceof ICustomDataHolder holder) {
            holder.mobstacker$getCustomData().putInt(STACK_SIZE_KEY, size);
            if (size <= 1) {
                // Nothing left under the top mob, so there is no shared wound to remember either.
                setStackMemberDamage(entity, 0.0F);
            }
            updateStackDisplay(entity);
        }
    }

    /**
     * Damage every member below the top mob is already carrying, so their health is
     * {@code maxHealth - memberDamage}. Per-mob Sweeping Edge adds to it on every swing: members are
     * identical, take the same sweep and therefore always share the same health, which is what lets
     * one number stand for all of them.
     * <p>
     * It is stored with the stack data, so it survives saving, and the surviving remainder of a stack
     * inherits it (see {@link #spawnNewEntity}). A merge keeps the target stack's value — the merge
     * already tops the target's own health up the same way.
     */
    public static float getStackMemberDamage(Mob entity) {
        if (!(entity instanceof ICustomDataHolder holder)) {
            return 0.0F;
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        return customData.contains(MEMBER_DAMAGE_KEY) ? customData.getFloat(MEMBER_DAMAGE_KEY) : 0.0F;
    }

    public static void setStackMemberDamage(Mob entity, float damage) {
        if (!(entity instanceof ICustomDataHolder holder)) {
            return;
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        if (damage <= 0.0F) {
            customData.remove(MEMBER_DAMAGE_KEY);
        } else {
            customData.putFloat(MEMBER_DAMAGE_KEY, damage);
        }
    }

    public static boolean getCanStack(Mob entity) {
        if (!(entity instanceof ICustomDataHolder holder)) {
            return true;
        }
        CompoundTag customData = holder.mobstacker$getCustomData();
        return !customData.contains(CAN_STACK_KEY) || customData.getBoolean(CAN_STACK_KEY);
    }

    public static void setCanStack(Mob entity, boolean canStack) {
        if (entity instanceof ICustomDataHolder holder) {
            holder.mobstacker$getCustomData().putBoolean(CAN_STACK_KEY, canStack);
        }
    }

    // --- Who sees which region ------------------------------------------------------------------
    // Regions used to go to every modded client that asked, corners and all - a map of every farm
    // and base on the server, and the grief that follows. Now the config sync sends a player only
    // the regions they may see, and the server is the only side that decides; a client can draw no
    // more than it was sent.

    /**
     * Bumped on every change to the config (see {@code MobStackerConfig.save}), so whoever holds
     * something derived from it - a client's snapshot, an owner's command tree - can tell that it is
     * out of date without being told what changed.
     */
    public static volatile int configVersion;

    public static void configChanged() {
        configVersion++;
    }

    /** Whether this region is hidden from players it does not name: its own value, or the global one. */
    public static boolean regionIsPrivate(StackRegion region) {
        String own = region.getSetting("privateRegions");
        return own != null ? Boolean.parseBoolean(own.trim()) : config.getPrivateRegions();
    }

    /** Whether a player sees this region: any region if {@code seesAll}, a public one, or one of theirs. */
    public static boolean regionVisible(StackRegion region, UUID player, boolean seesAll) {
        return seesAll || !regionIsPrivate(region) || region.grantsAccessTo(player);
    }

    /**
     * Whether a player sees every region: an operator, the player whose singleplayer world it is (who
     * may edit it without cheats, too), or anyone a permissions mod gives
     * {@value MobStackerPermissions#VIEW_ALL_REGIONS}.
     */
    public static boolean seesAllRegions(ServerPlayer player) {
        return player.hasPermissions(2)
                || player.server.isSingleplayerOwner(player.getGameProfile())
                || MobStackerPermissions.check(player, MobStackerPermissions.VIEW_ALL_REGIONS, 2);
    }

    /** The regions this player may see, in the config's order. */
    public static List<StackRegion> regionsVisibleTo(ServerPlayer player) {
        boolean seesAll = seesAllRegions(player);
        List<StackRegion> out = new ArrayList<>();
        for (StackRegion region : config.getRegions()) {
            if (regionVisible(region, player.getUUID(), seesAll)) {
                out.add(region);
            }
        }
        return out;
    }

    /** Whether this player owns any region, which is what lets them use {@code /mobstacker region share}. */
    public static boolean ownsRegion(UUID player) {
        for (StackRegion region : config.getRegions()) {
            if (region.isOwnedBy(player)) {
                return true;
            }
        }
        return false;
    }

    private static int commandAccessVersion = -1;
    private static final Map<UUID, Boolean> OWNER_COMMANDS_SENT = new java.util.HashMap<>();

    /**
     * Sends a player their command tree again when they became, or stopped being, a region owner.
     * The tree a client gets lists only the commands it may use, decided when it is sent - at login
     * and when the operator level changes - so a new owner would not see {@code region share} until
     * they logged in again. Runs once per config change, from the server tick.
     */
    public static void tickCommandAccess(MinecraftServer server) {
        if (commandAccessVersion == configVersion) {
            return;
        }
        commandAccessVersion = configVersion;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean owns = ownsRegion(player.getUUID());
            Boolean before = OWNER_COMMANDS_SENT.put(player.getUUID(), owns);
            if (before == null || before != owns) {
                server.getCommands().sendCommands(player);
            }
        }
    }

    // --- Per-region settings -------------------------------------------------------------------
    // A region can carry its own value for (almost) any setting; anything it does not mention keeps
    // following the global config. Every gameplay read therefore goes through the helpers below with
    // the mob in question, so the answer is the one that applies where that mob is standing. The
    // lookup returns immediately when no regions exist, which is the usual case.

    /**
     * The region whose settings apply at this entity, or null when it is outside every region.
     * Where regions overlap the highest {@code priority} wins, and equal priorities are settled in
     * favour of the smaller region, so a small exception carved inside a large area behaves the way
     * it looks. Both ALLOW and DENY regions carry settings: a DENY region stops new stacks forming,
     * but existing stacks can still wander in and should behave the way that place is configured.
     */
    public static StackRegion regionAt(Entity entity) {
        List<StackRegion> regions = config.getRegions();
        if (entity == null || regions.isEmpty()) {
            return null;
        }
        BlockPos pos = entity.blockPosition();
        StackRegion best = null;
        for (StackRegion region : regions) {
            if (!region.contains(entity.level(), pos)) {
                continue;
            }
            if (best == null || region.getPriority() > best.getPriority()
                    || (region.getPriority() == best.getPriority() && region.volume() < best.volume())) {
                best = region;
            }
        }
        return best;
    }

    private static String regionValue(String id, Entity at) {
        if (at == null || config.getRegions().isEmpty()) {
            return null;
        }
        StackRegion region = regionAt(at);
        return region == null ? null : region.getSetting(id);
    }

    /** The value of a boolean setting where {@code at} is standing. */
    public static boolean setting(String id, Entity at, boolean fallback) {
        String value = regionValue(id, at);
        if (value == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    /** The value of a whole-number setting where {@code at} is standing. */
    public static int setting(String id, Entity at, int fallback) {
        String value = regionValue(id, at);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The value of a decimal setting where {@code at} is standing. */
    public static double setting(String id, Entity at, double fallback) {
        String value = regionValue(id, at);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The value of a text setting where {@code at} is standing. */
    public static String setting(String id, Entity at, String fallback) {
        String value = regionValue(id, at);
        return value == null ? fallback : value;
    }

    /** The value of an enum setting where {@code at} is standing. */
    public static <E extends Enum<E>> E setting(String id, Entity at, E fallback) {
        String value = regionValue(id, at);
        if (value == null) {
            return fallback;
        }
        for (E constant : fallback.getDeclaringClass().getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value.trim())) {
                return constant;
            }
        }
        return fallback;
    }

    public static double getStackRadius() {return config.getStackRadius();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static double getStackRadius(Entity at) {return setting("stackRadius", at, config.getStackRadius());}

    public static int getMaxMobStackSize() {return config.getMaxMobStackSize();}

    /**
     * The largest a stack of <em>this</em> mob may grow where it is standing.
     *
     * <p>Four answers, most specific first: a ceiling this region sets for this mob's type, one the
     * global config sets for that type, this region's own {@code maxStackSize}, and the global
     * {@code maxStackSize}. The per-type ceilings are looked up by entity id, so "cows 64, zombies
     * 16" is one line each and modded mobs work without the mod knowing they exist.
     */
    public static int getMaxMobStackSize(Entity at) {
        if (at == null) {
            return config.getMaxMobStackSize();
        }
        // One region lookup for all four answers. Going through setting() at the end would search the
        // region list a second time, and this runs for every mob of every scan.
        StackRegion region = regionAt(at);

        // The mob's type id costs a registry lookup and a fresh string, so it is only worked out when
        // somebody has actually set a per-type ceiling - which on most servers is never.
        boolean regionCeilings = region != null && region.hasMaxStackSizes();
        if (regionCeilings || config.hasMaxStackSizes()) {
            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(at.getType()).toString();
            if (regionCeilings) {
                Integer here = region.getMaxStackSize(typeId);
                if (here != null) {
                    return here;
                }
            }
            Integer globally = config.getMaxStackSize(typeId);
            if (globally != null) {
                return globally;
            }
        }

        String override = region == null ? null : region.getSetting("maxStackSize");
        if (override != null) {
            try {
                return Integer.parseInt(override.trim());
            } catch (NumberFormatException e) {
                // A region holding something unparseable falls back to the global value, exactly as
                // MobStacker#setting does for every other whole-number setting.
            }
        }
        return config.getMaxMobStackSize();
    }

    /**
     * Whether killing the top mob takes the whole stack with it. {@code stackHealth} pools the
     * stack's health into one bar, which only makes sense if the whole stack dies with it, so it
     * forces this on. The rule lives here rather than in the stored config so it also holds inside a
     * region that turns {@code stackHealth} on for itself, and so switching {@code stackHealth} off
     * gives the player their own value back.
     */
    public static boolean getKillWholeStackOnDeath() {return config.getStackHealth() || config.getKillWholeStackOnDeath();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getKillWholeStackOnDeath(Entity at) {
        return getStackHealth(at) || setting("killWholeStackOnDeath", at, config.getKillWholeStackOnDeath());
    }

    public static boolean getStackHealth() {return config.getStackHealth();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getStackHealth(Entity at) {return setting("stackHealth", at, config.getStackHealth());}

    public static boolean getDamageOverflow() {return config.getDamageOverflow();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getDamageOverflow(Entity at) {return setting("damageOverflow", at, config.getDamageOverflow());}

    public static boolean getSweepingEdgeOverflow() {return config.getSweepingEdgeOverflow();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getSweepingEdgeOverflow(Entity at) {return setting("sweepingEdgeOverflow", at, config.getSweepingEdgeOverflow());}

    public static boolean getSweepingEdgePerMob() {return config.getSweepingEdgePerMob();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getSweepingEdgePerMob(Entity at) {return setting("sweepingEdgePerMob", at, config.getSweepingEdgePerMob());}

    public static boolean getSweepingEdgeSingleHit() {return config.getSweepingEdgeSingleHit();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getSweepingEdgeSingleHit(Entity at) {return setting("sweepingEdgeSingleHit", at, config.getSweepingEdgeSingleHit());}

    public static boolean getSweepingEdgeVanillaConditions() {return config.getSweepingEdgeVanillaConditions();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getSweepingEdgeVanillaConditions(Entity at) {return setting("sweepingEdgeVanillaConditions", at, config.getSweepingEdgeVanillaConditions());}

    public static int getSweepingEdgeMaxKills() {return config.getSweepingEdgeMaxKills();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static int getSweepingEdgeMaxKills(Entity at) {return setting("sweepingEdgeMaxKills", at, config.getSweepingEdgeMaxKills());}

    public static boolean getEnvironmentDamagePerMob() {return config.getEnvironmentDamagePerMob();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getEnvironmentDamagePerMob(Entity at) {return setting("environmentDamagePerMob", at, config.getEnvironmentDamagePerMob());}

    /**
     * Whether this damage comes from the world rather than from a blow: nobody dealt it (falling,
     * fire, lava, drowning, suffocation, cramming, magma, cactus, berry bushes, freezing, the void,
     * lightning, poison and wither), or it is an explosion or a falling block, which strike everything
     * in the spot whoever set them off. What a crowd of loose mobs would all have taken, so what every
     * member of a stack takes under {@code environmentDamagePerMob}.
     *
     * <p>{@code /kill} is left out: it already takes the whole stack its own way.
     */
    public static boolean isEnvironmentDamage(DamageSource source) {
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return false;
        }
        return (source.getEntity() == null && source.getDirectEntity() == null)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypes.FALLING_ANVIL)
                || source.is(DamageTypes.FALLING_BLOCK)
                || source.is(DamageTypes.FALLING_STALACTITE);
    }

    // --- Vanilla sweep context -----------------------------------------------------------------
    // Whether the swing currently being resolved satisfies vanilla's own conditions for a sweep
    // attack (fully charged, no crit, not sprinting, on the ground, sword in hand). It has to be
    // sampled in Player#attack, because the attack-strength counter is reset there before the
    // target's hurt() ever runs. Attack and damage resolve back-to-back on the server thread, so a
    // single slot keyed by the attacker is enough.
    private static Entity sweepContextAttacker;
    private static boolean sweepContextVanillaSweep;

    public static void setVanillaSweepContext(Entity attacker, boolean vanillaSweep) {
        sweepContextAttacker = attacker;
        sweepContextVanillaSweep = vanillaSweep;
    }

    /** True when {@code attacker}'s current swing is one vanilla would have swept with. */
    public static boolean hadVanillaSweepConditions(Entity attacker) {
        return attacker != null && attacker == sweepContextAttacker && sweepContextVanillaSweep;
    }

    // --- Spawning & despawning (1.12.0, ROADMAP #15 / #41) ---

    /**
     * A stack counts towards the spawn caps as at most this many mobs. Past any cap a bigger number
     * changes nothing, and a ceiling may be as large as an int goes.
     */
    private static final int SPAWN_COUNT_LIMIT = 4096;

    /**
     * The world's entities as vanilla's spawn-cap count should see them: a stack of N is handed over
     * N times (countStackMembers), so the caps, the per-player local caps and the soul sand valley's
     * spawn charges all count every member, exactly as they would count the crowd standing loose.
     * Lazy, so nothing is copied; off, it is the iterable it was given.
     */
    public static Iterable<Entity> countedForSpawning(Iterable<Entity> entities) {
        if (!config.getCountStackMembers()) {
            return entities;
        }
        return () -> new Iterator<>() {
            private final Iterator<Entity> all = entities.iterator();
            private Entity current;
            private int repeats;

            @Override
            public boolean hasNext() {
                return repeats > 0 || all.hasNext();
            }

            @Override
            public Entity next() {
                if (repeats > 0) {
                    repeats--;
                    return current;
                }
                if (!all.hasNext()) {
                    throw new NoSuchElementException();
                }
                current = all.next();
                repeats = current instanceof Mob mob
                        ? Math.max(0, Math.min(getStackSize(mob), SPAWN_COUNT_LIMIT) - 1) : 0;
                return current;
            }
        };
    }

    public static boolean getDespawnPickedUpMobs(Entity at) {return setting("despawnPickedUpMobs", at, config.getDespawnPickedUpMobs());}

    public static int getPersistentStackSize(Entity at) {return Math.max(0, setting("persistentStackSize", at, config.getPersistentStackSize()));}

    /** Whether this stack is big enough that {@code persistentStackSize} keeps it from despawning. */
    public static boolean keepsFromDespawning(Mob mob) {
        int size = getStackSize(mob);
        if (size < 2) {
            return false;
        }
        int threshold = getPersistentStackSize(mob);
        return threshold > 0 && size >= threshold;
    }

    /**
     * Just before a mob despawns, with {@code despawnPickedUpMobs} on: everything it picked up goes
     * back on the ground - the slots vanilla marked as a guaranteed drop, which is what picking an
     * item up does - and so does every member's, and an enderman's block. Natural gear (a zombie
     * born with a helmet) despawns with the mob as in vanilla.
     */
    public static void dropOnDespawn(Mob mob) {
        if (mob.level().isClientSide() || !getDespawnPickedUpMobs(mob)) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (!stack.isEmpty() && dropChance(mob, slot) > 1.0F && !EnchantmentHelper.hasVanishingCurse(stack)) {
                mob.spawnAtLocation(stack.copy());
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        ListTag members = getMemberLoadouts(mob);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag loadout = members.getCompound(i);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                String key = slot.getName();
                if (loadout.contains(key, Tag.TAG_COMPOUND) && loadout.getFloat(key + DROP_CHANCE_SUFFIX) > 1.0F) {
                    ItemStack stack = ItemStack.of(loadout.getCompound(key));
                    if (!stack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(stack)) {
                        mob.spawnAtLocation(stack);
                    }
                }
            }
        }
        if (mob instanceof EnderMan enderMan && enderMan.getCarriedBlock() != null) {
            ItemStack block = new ItemStack(enderMan.getCarriedBlock().getBlock());
            if (!block.isEmpty()) {
                mob.spawnAtLocation(block);
            }
            enderMan.setCarriedBlock(null);
        }
    }

    public static boolean getStrictDataComparison(Entity at) {return setting("strictDataComparison", at, config.getStrictDataComparison());}

    public static String getIgnoredDataKeys(Entity at) {return setting("ignoredDataKeys", at, config.getIgnoredDataKeys());}

    public static boolean getShowStackLabels(Entity at) {return setting("showStackLabels", at, config.getShowStackLabels());}

    public static boolean getAlwaysShowStackLabels(Entity at) {return setting("alwaysShowStackLabels", at, config.getAlwaysShowStackLabels());}

    public static boolean getSeparateStackLabel(Entity at) {return setting("separateStackLabel", at, config.getSeparateStackLabel());}

    public static String getStackLabelFormat(Entity at) {return setting("stackLabelFormat", at, config.getStackLabelFormat());}

    /** Where the data other mods saved on these two differs (see {@link MobData}), or null. */
    public static String dataDifference(Mob source, Mob target) {
        return MobData.difference(source, target, MobData.ignored(getIgnoredDataKeys(target)));
    }

    // --- The original mod's remaining options (1.12.0, ROADMAP #49-#53) ---

    public static boolean getMultiplyGiftDrops(Entity at) {return setting("multiplyGiftDrops", at, config.getMultiplyGiftDrops());}

    public static int getKillBatchSize(Entity at) {return Math.max(1, setting("killBatchSize", at, config.getKillBatchSize()));}

    public static boolean getDamageWeaponPerHit(Entity at) {return setting("damageWeaponPerHit", at, config.getDamageWeaponPerHit());}

    public static boolean getStackOutgoingDamage(Entity at) {return setting("stackOutgoingDamage", at, config.getStackOutgoingDamage());}

    public static int getMaxDamageMultiplier(Entity at) {return Math.max(1, setting("maxDamageMultiplier", at, config.getMaxDamageMultiplier()));}

    public static boolean getEnableAssimilator(Entity at) {return setting("enableAssimilator", at, config.getEnableAssimilator());}

    public static boolean getConsumeAssimilator(Entity at) {return setting("consumeAssimilator", at, config.getConsumeAssimilator());}

    public static String getAssimilatorItem(Entity at) {return setting("assimilatorItem", at, config.getAssimilatorItem());}

    /**
     * multiplyGiftDrops: vanilla has just dropped one {@code item} for the top mob of a stack (an
     * egg, a scute); the other members drop theirs. In full stacks, so a stack of a hundred
     * chickens leaves seven entities, not a hundred.
     */
    public static void dropGiftForMembers(Mob mob, ItemLike item, int each) {
        int members = getStackSize(mob) - 1;
        if (members <= 0 || mob.level().isClientSide() || !getMultiplyGiftDrops(mob)) {
            return;
        }
        long left = (long) members * Math.max(1, each);
        int max = new ItemStack(item).getMaxStackSize();
        while (left > 0) {
            int count = (int) Math.min(left, max);
            mob.spawnAtLocation(new ItemStack(item, count));
            left -= count;
        }
    }

    /** stackOutgoingDamage: a stack's melee damage, multiplied by its size up to maxDamageMultiplier. */
    public static float stackMeleeDamage(Mob attacker, float damage) {
        int size = getStackSize(attacker);
        if (size <= 1 || attacker.level().isClientSide() || !getStackOutgoingDamage(attacker)) {
            return damage;
        }
        return damage * Math.min(size, getMaxDamageMultiplier(attacker));
    }

    /**
     * damageWeaponPerHit: a hit on a stack that dies as a whole also wears the weapon for every other
     * mob in it - {@code perHit} is what vanilla's own hit just took (1 for a sword, 2 for an axe).
     */
    public static void wearWeaponForStack(ItemStack weapon, LivingEntity target, Player attacker, int perHit) {
        if (perHit <= 0 || weapon.isEmpty() || !weapon.isDamageableItem() || attacker.getAbilities().instabuild
                || !(target instanceof Mob mob) || target.level().isClientSide()) {
            return;
        }
        int members = getStackSize(mob);
        if (members <= 1 || !getDamageWeaponPerHit(mob) || !getKillWholeStackOnDeath(mob)) {
            return;
        }
        long extra = (long) perHit * (members - 1);
        weapon.hurtAndBreak((int) Math.min(extra, Integer.MAX_VALUE), attacker,
                broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    /**
     * The assimilator (enableAssimilator): sneaking with it on a mob switches whether that mob - or
     * the whole stack it is - may stack. Sneaking, unlike the original mod, because the default item
     * is an emerald and a plain right-click with emeralds is how a villager is traded with.
     *
     * @return true when it was used, and the interaction is over
     */
    public static boolean useAssimilator(Player player, Mob mob, InteractionHand hand) {
        if (mob.level().isClientSide() || !player.isShiftKeyDown() || !getEnableAssimilator(mob)) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        ResourceLocation id = ResourceLocation.tryParse(getAssimilatorItem(mob));
        if (held.isEmpty() || id == null || !held.is(BuiltInRegistries.ITEM.get(id))) {
            return false;
        }
        boolean nowStacks = !getCanStack(mob);
        setCanStack(mob, nowStacks);
        Component name = mob.getDisplayName();
        player.displayClientMessage(Component.empty().append(name)
                .append(Component.literal(nowStacks ? " may stack again" : " will not stack")
                        .withStyle(nowStacks ? ChatFormatting.GREEN : ChatFormatting.GOLD)), true);
        if (!player.getAbilities().instabuild && getConsumeAssimilator(mob)) {
            if (held.isDamageableItem()) {
                held.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
            } else {
                held.shrink(1);
            }
        }
        return true;
    }

    public static boolean getStackBosses(Entity at) {return setting("stackBosses", at, config.getStackBosses());}

    public static int getBossHealthThreshold(Entity at) {return setting("bossHealthThreshold", at, config.getBossHealthThreshold());}

    public static boolean getStackEquippedMobs() {return config.getStackEquippedMobs();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getStackEquippedMobs(Entity at) {return setting("stackEquippedMobs", at, config.getStackEquippedMobs());}

    public static boolean getKeepMemberEquipment() {return config.getKeepMemberEquipment();}

    public static boolean getKeepMemberEquipment(Entity at) {return setting("keepMemberEquipment", at, config.getKeepMemberEquipment());}

    public static boolean getStackNamedMobs() {return config.getStackNamedMobs();}

    public static boolean getStackNamedMobs(Entity at) {return setting("stackNamedMobs", at, config.getStackNamedMobs());}

    public static boolean getStackKillActionBar() {return config.getStackKillActionBar();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getStackKillActionBar(Entity at) {return setting("stackKillActionBar", at, config.getStackKillActionBar());}

    public static boolean getStackKillParticles() {return config.getStackKillParticles();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getStackKillParticles(Entity at) {return setting("stackKillParticles", at, config.getStackKillParticles());}

    public static boolean getStackKillHologram() {return config.getStackKillHologram();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getStackKillHologram(Entity at) {return setting("stackKillHologram", at, config.getStackKillHologram());}

    public static int getStackScanInterval() {return config.getStackScanInterval();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static int getStackScanInterval(Entity at) {return setting("stackScanInterval", at, config.getStackScanInterval());}

    public static boolean getStackOnSpawn() {return config.getStackOnSpawn();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getStackOnSpawn(Entity at) {return setting("stackOnSpawn", at, config.getStackOnSpawn());}

    public static StackAi getStackAi() {return config.getStackAi();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static StackAi getStackAi(Entity at) {return setting("stackAi", at, config.getStackAi());}

    /** What this mob does with its AI: {@code stackAi} where it stands for a stack, NORMAL for anything else. */
    public static StackAi stackAiFor(Mob mob) {
        return getStackSize(mob) > 1 ? getStackAi(mob) : StackAi.NORMAL;
    }

    /** Per mob class, whether it swims up rather than sinking; see {@link #keepsAfloat}. */
    private static final Map<Class<?>, Boolean> FLOATERS = new ConcurrentHashMap<>();

    /**
     * Whether a stack that no longer thinks should still be kept afloat in water and lava, because the
     * mob would have swum up on its own.
     *
     * <p>Staying afloat is AI in vanilla - a goal ({@code FloatGoal}) for most mobs, a brain behaviour
     * for villagers, goats and the like - so a stack without it sank, and a stack of cows in a water
     * stream drowned. Mobs that sink in vanilla (zombies, skeletons, iron golems) and mobs that breathe
     * water keep doing what they did. The answer is the same for every mob of a class, since goals are
     * added when a mob is built, so it is worked out once.
     */
    public static boolean keepsAfloat(Mob mob, GoalSelector goals) {
        return FLOATERS.computeIfAbsent(mob.getClass(), type -> {
            Set<WrappedGoal> available = goals.getAvailableGoals();
            if (available.isEmpty()) {
                return !mob.canBreatheUnderwater(); // a brain, not goals: swims if it would drown
            }
            for (WrappedGoal goal : available) {
                if (goal.getGoal() instanceof FloatGoal) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * The colour a stack's name is drawn in. With {@code stackNameColorBySize} on the colour steps up
     * at the two configured stack sizes, so a huge stack is recognisable at a glance; otherwise every
     * stack uses the single configured colour.
     */
    public static ChatFormatting stackNameColor(Entity at, int stackSize) {
        if (setting("stackNameColorBySize", at, config.getStackNameColorBySize())) {
            if (stackSize >= setting("stackSizeLargeThreshold", at, config.getStackSizeLargeThreshold())) {
                return setting("stackNameColorLarge", at, config.getStackNameColorLarge()).format();
            }
            if (stackSize >= setting("stackSizeMediumThreshold", at, config.getStackSizeMediumThreshold())) {
                return setting("stackNameColorMedium", at, config.getStackNameColorMedium()).format();
            }
        }
        return setting("stackNameColor", at, config.getStackNameColor()).format();
    }

    public static boolean getStackedHarvest() {return config.getStackedHarvest();}

    public static boolean getStackedHarvest(Entity at) {return setting("stackedHarvest", at, config.getStackedHarvest());}

    public static boolean getEnableStackBreeding() {return config.getEnableStackBreeding();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getEnableStackBreeding(Entity at) {return setting("enableStackBreeding", at, config.getEnableStackBreeding());}

    public static boolean getBreedOnePerClick() {return config.getBreedOnePerClick();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getBreedOnePerClick(Entity at) {return setting("breedOnePerClick", at, config.getBreedOnePerClick());}

    public static boolean getEnableAnimalBabyStacking() {return config.getEnableAnimalBabyStacking();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getEnableAnimalBabyStacking(Entity at) {return setting("enableAnimalBabyStacking", at, config.getEnableAnimalBabyStacking());}

    public static boolean getEnableHostileBabyStacking() {return config.getEnableHostileBabyStacking();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getEnableHostileBabyStacking(Entity at) {return setting("enableHostileBabyStacking", at, config.getEnableHostileBabyStacking());}

    public static boolean getCompactDrops() {return config.getCompactDrops();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getCompactDrops(Entity at) {return setting("compactDrops", at, config.getCompactDrops());}

    public static boolean getCompactExperience() {return config.getCompactExperience();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getCompactExperience(Entity at) {return setting("compactExperience", at, config.getCompactExperience());}

    /**
     * Spawns a small floating "-N" hologram above the mob when a hit clears mobs off a stack.
     * It is an invisible marker armor stand (no hitbox, no gravity) that drifts up and is removed
     * ~1 second later by {@link #tickKillHolograms()}. This is the only feedback channel that
     * spawns an entity, so it is transient and gated behind its own config flag.
     */
    public static void spawnKillHologram(ServerLevel level, Mob mob, int killed) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            return;
        }
        double y = mob.getY() + mob.getBbHeight() + 0.55;
        stand.moveTo(mob.getX(), y, mob.getZ(), 0.0F, 0.0F);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        ((ArmorStandAccessor) stand).mobstacker$setMarker(true);
        stand.setSilent(true);
        stand.setNoBasePlate(true);
        stand.setInvulnerable(true);
        stand.setCustomName(Component.literal("-" + killed)
                .withStyle(setting("killHologramColor", mob, config.getKillHologramColor()).format(),
                        ChatFormatting.BOLD));
        stand.setCustomNameVisible(true);
        stand.addTag(KILL_HOLOGRAM_TAG);
        level.addFreshEntity(stand);
        killHolograms.add(new KillHologram(stand, level.getGameTime() + KILL_HOLOGRAM_LIFETIME_TICKS));
    }

    /** Drifts active kill holograms upward and discards them once their lifetime is up. */
    public static void tickKillHolograms() {
        if (killHolograms.isEmpty()) {
            return;
        }
        Iterator<KillHologram> it = killHolograms.iterator();
        while (it.hasNext()) {
            KillHologram hologram = it.next();
            ArmorStand stand = hologram.entity();
            if (stand.isRemoved() || stand.level().getGameTime() >= hologram.expireGameTime()) {
                if (!stand.isRemoved()) {
                    stand.discard();
                }
                it.remove();
            } else {
                stand.setPos(stand.getX(), stand.getY() + 0.025, stand.getZ());
            }
        }
    }

    /** True while this entity is one of the kill holograms we are actively ticking. */
    /**
     * Whether this armor stand looks like a kill hologram left behind by a version that did not tag
     * them (up to and including 1.5.x). Those were ordinary armor stands written into their chunk,
     * so a server that stopped - or a chunk that unloaded - inside a hologram's one second left one
     * floating for good, with nothing able to recognise it afterwards. Tagging fixed that going
     * forward but could not reach the ones already out there, and they do not go away on their own.
     * <p>
     * The shape is distinctive enough to be safe: an invisible, silent, invulnerable marker with no
     * gravity, no base plate and no equipment, whose whole visible name is {@code -<number>}. It is
     * also only ever asked on a stand's very first tick (see {@code ArmorStandMixin}), so nothing
     * built in the world afterwards can be caught by it.
     */
    public static boolean isLegacyKillHologram(ArmorStand stand) {
        if (!stand.isMarker() || !stand.isInvisible() || !stand.isNoGravity()
                || !stand.isInvulnerable() || !stand.isSilent()
                || !stand.isNoBasePlate() || !stand.isCustomNameVisible()) {
            return false;
        }
        Component name = stand.getCustomName();
        if (name == null) {
            return false;
        }
        String text = name.getString();
        // "-" plus at least one digit, and no longer than a stack size could ever be.
        if (text.length() < 2 || text.length() > 7 || text.charAt(0) != '-') {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!stand.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Removes a stray hologram and says so, so an operator can see an old world clean itself up. */
    public static void discardStrayKillHologram(ArmorStand stand) {
        logger.info("MobStacker: removed a leftover kill hologram at {} {} {}",
                stand.getBlockX(), stand.getBlockY(), stand.getBlockZ());
        stand.discard();
    }

    public static boolean isTrackedKillHologram(Entity entity) {
        for (KillHologram hologram : killHolograms) {
            if (hologram.entity() == entity) {
                return true;
            }
        }
        return false;
    }

    /**
     * Discards every hologram still being tracked and forgets them. Called when a server stops and
     * when a world's config is loaded, so holograms can never outlive the session that spawned them
     * and the list can never hold entities from a world we already left.
     */
    public static void clearKillHolograms() {
        for (KillHologram hologram : killHolograms) {
            ArmorStand stand = hologram.entity();
            if (!stand.isRemoved()) {
                stand.discard();
            }
        }
        killHolograms.clear();
    }

    // --- Drop compaction ---------------------------------------------------------------------
    // While a stacked mob is dying we capture the item stacks (and experience) it drops and
    // re-emit them merged: a handful of full item stacks and a single experience orb, instead of
    // dozens of scattered items and orbs. This never creates or destroys anything — it only
    // re-containers what was actually dropped, so it can't duplicate loot/XP on any kill path.
    // Death drops run synchronously on the server thread, so a single capture context suffices.
    private static Mob dropCaptureMob;
    private static boolean captureItems;
    private static boolean captureXp;
    private static final List<ItemStack> dropCaptureBuffer = new ArrayList<>();
    private static int xpCaptureTotal;

    /**
     * Starts buffering {@code mob}'s death drops. Items and experience are captured independently,
     * each gated by its own config flag, so either can be compacted without the other.
     */
    public static void beginDropCapture(Mob mob) {
        dropCaptureMob = mob;
        captureItems = getCompactDrops(mob);
        captureXp = getCompactExperience(mob);
        dropCaptureBuffer.clear();
        xpCaptureTotal = 0;
    }

    /**
     * Called from the {@code Entity.spawnAtLocation} hook. If this drop belongs to the mob we are
     * currently compacting it is buffered and the caller cancels the real spawn. Returns true when
     * the drop was captured.
     */
    public static boolean tryCaptureDrop(Entity entity, ItemStack stack) {
        if (dropCaptureMob == null || !captureItems || entity != dropCaptureMob || stack == null || stack.isEmpty()) {
            return false;
        }
        dropCaptureBuffer.add(stack.copy());
        return true;
    }

    /**
     * Called from the {@code ExperienceOrb.award} hook. While a stacked mob is dying its whole
     * experience (vanilla plus every extra mob killed via overflow / killWholeStackOnDeath) is
     * summed here instead of spawning many small orbs. Returns true when it was captured.
     */
    public static boolean tryCaptureExperience(int amount) {
        if (dropCaptureMob == null || !captureXp || amount <= 0) {
            return false;
        }
        xpCaptureTotal += amount;
        return true;
    }

    /**
     * Re-emits the captured drops for {@code mob}: items merged into as few item entities as
     * possible (identical stacks combined up to their max size) and the whole experience as a
     * single orb, all at the mob's centre so they stay clustered. Clears the capture context
     * afterwards. Safe to call for any dying mob — it no-ops unless {@code mob} is the one
     * currently being captured.
     */
    public static void finishDropCaptureAndCompact(Mob mob) {
        if (dropCaptureMob != mob) {
            return;
        }
        List<ItemStack> buffer = new ArrayList<>(dropCaptureBuffer);
        int xp = xpCaptureTotal;
        dropCaptureMob = null;
        captureItems = false;
        captureXp = false;
        dropCaptureBuffer.clear();
        xpCaptureTotal = 0;

        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }

        if (!buffer.isEmpty()) {
            List<ItemStack> merged = new ArrayList<>();
            for (ItemStack stack : buffer) {
                mergeIntoStacks(merged, stack);
            }
            for (ItemStack stack : merged) {
                spawnCompactItem(level, mob, stack);
            }
        }

        if (xp > 0) {
            level.addFreshEntity(new ExperienceOrb(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), xp));
        }
    }

    /** Folds {@code incoming} into {@code merged}, topping up compatible stacks before adding new ones. */
    private static void mergeIntoStacks(List<ItemStack> merged, ItemStack incoming) {
        int remaining = incoming.getCount();
        for (ItemStack existing : merged) {
            if (remaining <= 0) {
                return;
            }
            if (ItemStack.isSameItemSameTags(existing, incoming)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int moved = Math.min(space, remaining);
                    existing.grow(moved);
                    remaining -= moved;
                }
            }
        }
        while (remaining > 0) {
            ItemStack copy = incoming.copy();
            int size = Math.min(remaining, copy.getMaxStackSize());
            copy.setCount(size);
            merged.add(copy);
            remaining -= size;
        }
    }

    /** Spawns a single item entity at the mob's centre with no motion so the drops stay together. */
    private static void spawnCompactItem(ServerLevel level, Mob mob, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
        item.setDeltaMovement(0.0, 0.0, 0.0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    public static boolean getEnableSeparator() {return config.getEnableSeparator();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getEnableSeparator(Entity at) {return setting("enableSeparator", at, config.getEnableSeparator());}

    public static boolean getConsumeSeparator() {return config.getConsumeSeparator();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static boolean getConsumeSeparator(Entity at) {return setting("consumeSeparator", at, config.getConsumeSeparator());}

    public static String getSeparatorItem() {return config.getSeparatorItem();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static String getSeparatorItem(Entity at) {return setting("separatorItem", at, config.getSeparatorItem());}

    public static int getSeparationCooldown() {return config.getSeparationCooldown();}

    /** As above, but for where {@code at} is standing: a region may set its own value. */
    public static int getSeparationCooldown(Entity at) {return Math.max(0, setting("separationCooldown", at, config.getSeparationCooldown()));}

    public static int getMonsterMobCap() {return config.getMonsterMobCap();}
    public static int getCreatureMobCap() {return config.getCreatureMobCap();}
    public static int getAmbientMobCap() {return config.getAmbientMobCap();}
    public static int getAxolotlsMobCap() {return config.getAxolotlsMobCap();}
    public static int getUndergroundWaterCreatureMobCap() {return config.getUndergroundWaterCreatureMobCap();}
    public static int getWaterCreatureMobCap() {return config.getWaterCreatureMobCap();}
    public static int getWaterAmbientMobCap() {return config.getWaterAmbientMobCap();}

}
