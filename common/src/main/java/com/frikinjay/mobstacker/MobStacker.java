package com.frikinjay.mobstacker;

import com.frikinjay.mobstacker.api.MobStackerAPI;
import com.frikinjay.mobstacker.config.MobLists;
import com.frikinjay.mobstacker.config.MobStackerConfig;
import com.frikinjay.mobstacker.config.StackMode;
import com.frikinjay.mobstacker.config.StackRegion;
import com.frikinjay.mobstacker.mixin.ArmorStandAccessor;
import com.frikinjay.mobstacker.mixin.MobEquipmentAccessor;
import com.frikinjay.mobstacker.mixin.ParrotAccessor;
import com.frikinjay.mobstacker.mixin.OcelotAccessor;
import com.frikinjay.mobstacker.mixin.FoxAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.slf4j.Logger;

import net.minecraft.world.entity.decoration.ArmorStand;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public final class MobStacker {
    public static final String MOD_ID = "mobstacker";

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

    // --- Stack breeding (feeding a stacked adult its food breeds its members in pairs) ---
    // Members currently "in love" waiting for a partner; kept so partial feeding never wastes food.
    // Set on a mob the mod itself has just taken out of a stack, so the stack-on-spawn pass leaves
    // it alone. Without it a mob would be handed to the player and walk straight back in on the same
    // tick, which is exactly what separating was asked to undo.
    private static final String JUST_SEPARATED_KEY = "JustSeparated";
    /** Ticks left before a mob the mod handed to a player may walk back into a stack. */
    private static final String SEPARATION_GRACE_KEY = "SeparationGrace";
    /**
     * How long that is: fifteen seconds, refreshed by every further interaction with the mob.
     *
     * <p>One tick was not enough. A horse that bucks the player off, a wolf that shrugs off a bone -
     * the animal went straight back into the herd on the next scan, and the next click peeled a
     * fresh one out, so taming never got anywhere. Long enough to keep at one animal, short enough
     * that one you fed and walked away from still rejoins the herd on its own.
     */
    private static final int SEPARATION_GRACE_TICKS = 300;
    private static final String BREED_LOVE_KEY = "BreedLove";
    // How many members recently bred and are on breeding cooldown, and until when (game time).
    private static final String BREED_COOLDOWN_COUNT_KEY = "BreedCooldownCount";
    private static final String BREED_COOLDOWN_END_KEY = "BreedCooldownEnd";
    // Vanilla breeding cooldown is 5 minutes (6000 ticks).
    private static final int BREED_COOLDOWN_TICKS = 6000;
    public static MobStackerConfig config;
    // Resolved to the running world's save folder on server start (see loadWorldConfig).
    // The global path is only a pre-server fallback so the field is never null.
    public static File configFile = new File("config/mobstacker.json");

    private static final WeakHashMap<Class<?>, Boolean> bossEntityCache = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Field> bossFieldCache = new WeakHashMap<>();

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
        logger.info("MobStacker config reloaded");
    }

    public static boolean canStack(Mob entity) {
        if (!(entity instanceof Mob)) {
            return false;
        }

        // Babies stack only when their category's flag allows it. Farm animals (Animal) and
        // hostile/other mobs (e.g. baby zombies) are controlled independently. Baby-vs-adult
        // mixing is prevented separately in canMerge.
        if (entity.isBaby()) {
            boolean allowed = (entity instanceof Animal)
                    ? getEnableAnimalBabyStacking(entity)
                    : getEnableHostileBabyStacking(entity);
            if (!allowed) {
                return false;
            }
        }

        // A mob that is dead or playing its death animation (health <= 0) must not take part
        // in stacking. Merging into a dying mob restores its health mid-animation, which makes
        // the death animation loop. Excluding it lets the death finish cleanly.
        if (entity.isDeadOrDying() || entity.isRemoved()) {
            return false;
        }

        // Mobs that hold or wear something (e.g. an armed/armored zombie) carry per-mob data the
        // stack cannot represent, and merging would drop their gear. Keep them unstacked unless
        // explicitly allowed.
        if (!getStackEquippedMobs(entity) && hasEquipment(entity)) {
            return false;
        }

        if (isPlayerBound(entity)) {
            return false;
        }

        // Handed to a player a moment ago: leave it with them. Checked here rather than only at the
        // merge attempt so nothing merges INTO it either - a stack absorbing the animal somebody is
        // halfway through taming is the same bug seen from the other side.
        if (separationGrace(entity) > 0) {
            return false;
        }

        if (!isStackingAllowedAt(entity)) {
            return false;
        }

        // Whether this kind of mob may stack at all here: a blacklist or a whitelist, global or
        // the region's own. MobLists is the single place that decides, because the answer now
        // depends on three things (the mode in force, which lists it consults, and whether the
        // region overrides them) and working any of them out twice is how tables drift apart.
        if (!MobLists.allows(entity)) {
            return false;
        }

        return hasValidCustomNameForStacking(entity) && getStackSize(entity) < getMaxMobStackSize(entity);
    }

    /**
     * Decides whether a mob is allowed to form stacks at its current location,
     * based on the global {@link StackMode} and the configured regions.
     * Note: this only gates the formation of new stacks. Mobs that are already
     * stacked keep behaving correctly anywhere (death splitting, separation, ...).
     */
    public static boolean isStackingAllowedAt(Mob entity) {
        StackMode mode = config.getStackMode();
        if (mode == StackMode.OFF) {
            return false;
        }

        String dimension = entity.level().dimension().location().toString();
        BlockPos pos = entity.blockPosition();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        boolean insideAllowRegion = false;
        for (StackRegion region : config.getRegions()) {
            if (region.contains(dimension, x, y, z)) {
                if (region.isDeny()) {
                    return false; // DENY overrides everything, in every mode.
                }
                insideAllowRegion = true;
            }
        }

        if (mode == StackMode.EVERYWHERE) {
            return true;
        }

        if (mode == StackMode.PLAYERS) {
            // Only stack near a player. getNearestPlayer applies the distance limit itself and
            // excludes spectators; a radius <= 0 would disable stacking entirely (guarded below).
            double radius = config.getPlayerStackRadius();
            return radius > 0 && entity.level().getNearestPlayer(entity, radius) != null;
        }

        // StackMode.REGIONS: only stack when inside at least one ALLOW region.
        return insideAllowRegion;
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
        // Trust is the other way a player owns an animal without owning it. An ocelot is never tamed
        // and a fox remembers who bred it by UUID, so neither has an owner field for the checks above
        // to notice - and a merge would hand one wild mob's indifference to the whole stack.
        if (entity instanceof Ocelot ocelot) {
            return ((OcelotAccessor) ocelot).mobstacker$isTrusting();
        }
        return entity instanceof Fox fox && !((FoxAccessor) fox).mobstacker$trustedUUIDs().isEmpty();
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

    public static boolean canMerge(Mob self, Mob nearby) {
        if (self.getClass() != nearby.getClass() || !getCanStack(nearby)) {
            return false;
        }

        // Never merge into / from a dying or removed mob (see canStack) — guards the
        // destructive mergeEntities call against the death-animation loop.
        if (self.isDeadOrDying() || nearby.isDeadOrDying() || self.isRemoved() || nearby.isRemoved()) {
            return false;
        }

        if ((getStackSize(self) + getStackSize(nearby)) > getMaxMobStackSize(self)) {
            return false;
        }

        // Never mix a baby with an adult (they carry different growth state). Two babies may
        // merge freely; mergeEntities keeps the youngest age so none grows up early.
        if (self.isBaby() != nearby.isBaby()) {
            return false;
        }

        // The stack shows one name, and the survivor keeps its own, so a mob carrying a different
        // player-given name must not be merged away into it - that name would simply vanish.
        Component ownName = playerGivenName(self);
        if (ownName != null && !ownName.equals(playerGivenName(nearby))) {
            return false;
        }

        if (!MobVariants.sameVariant(self, nearby)) {
            return false;
        }

        return MobStackerAPI.checkCustomMergingConditions(self, nearby);
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

        EntityType<?> entityType = self.getType();
        Mob newEntity = (Mob) entityType.create(serverLevel);
        if (newEntity == null) return;

        copyEntityData(self, newEntity, serverLevel);
        copyStackNaming(self, newEntity);
        // After copyEntityData, because finalizeSpawn hands out random gear of its own that the
        // stored loadout has to overwrite.
        handOverMemberEquipment(self, newEntity, newStackSize);
        MobStacker.setStackSize(newEntity, newStackSize);
        if (newStackSize > 1) {
            // The mobs under the new top one are the same wounded members as before the kill.
            MobStacker.setStackMemberDamage(newEntity, getStackMemberDamage(self));
        }
        copyBreedData(self, newEntity, newStackSize);
        if (survivorHealth > 0.0F && survivorHealth <= newEntity.getMaxHealth()) {
            newEntity.setHealth(survivorHealth);
        }
        serverLevel.addFreshEntity(newEntity);
    }

    private static void copyEntityData(Mob source, Mob target, ServerLevel serverLevel) {
        target.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(source.blockPosition()),
                MobSpawnType.NATURAL, null, null);
        target.moveTo(source.position().x, source.position().y, source.position().z,
                source.getYRot(), source.getXRot());
        target.yBodyRot = source.yBodyRot;

        if (source.hasCustomName()) {
            target.setCustomName(source.getCustomName());
        }

        copyVariantData(source, target);
        copyAgeData(source, target);
        MobStackerAPI.applyEntityDataModifiers(source, target);
    }

    /**
     * Copies the source's growth state (age / baby flag) onto a freshly-created entity, so a
     * stack's respawned remainder (after a kill) or a separated mob keeps being a baby with the
     * same growth progress. Without this, {@code entityType.create()} / {@code finalizeSpawn}
     * would make it an adult (finalizeSpawn even has a ~5% chance to roll a random baby).
     */
    private static void copyAgeData(Mob source, Mob target) {
        if (source instanceof AgeableMob sourceAge && target instanceof AgeableMob targetAge) {
            targetAge.setAge(sourceAge.getAge());
        } else if (source instanceof Zombie sourceZombie && target instanceof Zombie targetZombie) {
            targetZombie.setBaby(sourceZombie.isBaby());
        }
    }

    private static void copyVariantData(Mob source, Mob target) {
        MobVariants.copyVariant(source, target);
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
            EntityType<?> entityType = entity.getType();
            Mob newEntity = (Mob) entityType.create(entity.level());
            if (newEntity == null) return null;

            setStackSize(entity, getStackSize(entity) - 1);

            copyEntityDataForSeparation(entity, newEntity, serverLevel, markAsSeparated);
            handleHealthOnSeparation(entity, newEntity);
            // The separated mob is one of the stored members, so it leaves wearing that member's
            // gear; the stack keeps the rest.
            takeMemberEquipmentFor(entity, newEntity);

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

    private static void copyEntityDataForSeparation(Mob source, Mob target, ServerLevel serverLevel,
                                                    boolean markAsSeparated) {
        target.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(source.blockPosition()),
                MobSpawnType.NATURAL, null, null);
        target.moveTo(source.position().x, source.position().y, source.position().z,
                source.getYRot(), source.getXRot());
        target.yBodyRot = source.yBodyRot;

        if (markAsSeparated) {
            target.setCustomName(Component.literal("Lone " + getLocalizedEntityName(source.getType()).getString()));
        }

        copyVariantData(source, target);
        copyAgeData(source, target);
    }

    private static void handleHealthOnSeparation(Mob source, Mob target) {
        if (getStackHealth(source) && source.getHealth() > target.getMaxHealth()) {
            source.setHealth(source.getHealth() - target.getMaxHealth());
        }
    }

    /**
     * Looks for a nearby stack this mob can join and merges into it. The nearby mob is kept as the
     * stack and this one is discarded, so callers must not touch {@code self} afterwards.
     *
     * @return true when the mob was merged away
     */
    public static boolean tryMergeIntoNearbyStack(Mob self) {
        for (Entity nearby : self.level().getEntities(self, self.getBoundingBox().inflate(getStackRadius(self)),
                entity -> entity instanceof Mob && canStack((Mob) entity))) {
            if (canMerge(self, (Mob) nearby)) {
                mergeEntities((Mob) nearby, self);
                return true;
            }
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
        int newStackSize = Math.min(getStackSize(target) + getStackSize(source), getMaxMobStackSize(target));

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

        updateHealth(target, source);

        if (mergedBabyAge != null && target instanceof AgeableMob mergedAge) {
            mergedAge.setAge(mergedBabyAge);
        }

        if (mergedLoadouts != null) {
            // After load(), which put the target's own stack data back from the snapshot above.
            setMemberLoadouts(target, mergedLoadouts);
        }

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
                key.equals("Motion") || key.equals("Health") ||
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

    public static void updateStackDisplay(Mob entity) {
        int stackSize = getStackSize(entity);
        boolean isBoss = isBossEntity(entity);

        Component newName = generateNewDisplayName(entity, stackSize);
        updateEntityName(entity, newName, isBoss);
    }

    private static Component generateNewDisplayName(Mob entity, int stackSize) {
        Component given = playerGivenName(entity);
        if (given != null) {
            // Exactly what the player typed on the name tag, plus the live count. Nothing is parsed
            // back off the label, so a stack named "Cow x5" stays "Cow x5" and gains " x16".
            return stackSize > 1 ? given.copy().append(" x" + stackSize) : given.copy();
        }
        if (entity.hasCustomName() && !matchesStackedName(entity.getCustomName().getString(), entity)) {
            String baseName = STACKED_NAME_PATTERN.matcher(entity.getCustomName().getString())
                    .replaceFirst("");
            return Component.literal(baseName + (stackSize > 1 ? " x" + stackSize : ""))
                    .withStyle(entity.getCustomName().getStyle());
        }
        return stackSize > 1 ?
                Component.literal(getLocalizedEntityName(entity.getType()).getString() + " x" + stackSize)
                        .withStyle(stackNameColor(entity, stackSize)) :
                null;
    }

    private static void updateEntityName(Mob entity, Component newName, boolean isBoss) {
        if (newName != null) {
            entity.setCustomName(newName);
            if (isBoss) {
                ServerBossEvent bossEvent = getBossField(entity);
                if (bossEvent != null) {
                    bossEvent.setName(newName);
                }
            }
        } else if (isBoss) {
            handleBossNameReset(entity);
        } else if (entity.hasCustomName()) {
            entity.setCustomName(null);
        }

        if (!isBoss && hasNonCustomName(entity)) {
            entity.setCustomNameVisible(false);
        }
    }

    private static void handleBossNameReset(Mob entity) {
        delayExecution((ServerLevel) entity.level(), 1, () -> {
            ServerBossEvent bossEvent = getBossField(entity);
            if (bossEvent != null) {
                bossEvent.setName(Component.literal(entity.getDisplayName().getString()));
                entity.setCustomName(null);
            }
        });
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
     * Remembers that the mod put this mob here, so nothing merges it away again while the player is
     * still busy with it. See {@link #SEPARATION_GRACE_TICKS}.
     */
    private static void markJustSeparated(Mob mob) {
        if (mob instanceof ICustomDataHolder holder) {
            CompoundTag data = holder.mobstacker$getCustomData();
            data.putBoolean(JUST_SEPARATED_KEY, true);
            data.putInt(SEPARATION_GRACE_KEY, SEPARATION_GRACE_TICKS);
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
                data.putInt(SEPARATION_GRACE_KEY, SEPARATION_GRACE_TICKS);
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
        String dimension = entity.level().dimension().location().toString();
        BlockPos pos = entity.blockPosition();
        StackRegion best = null;
        for (StackRegion region : regions) {
            if (!region.contains(dimension, pos.getX(), pos.getY(), pos.getZ())) {
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

    public static int getMonsterMobCap() {return config.getMonsterMobCap();}
    public static int getCreatureMobCap() {return config.getCreatureMobCap();}
    public static int getAmbientMobCap() {return config.getAmbientMobCap();}
    public static int getAxolotlsMobCap() {return config.getAxolotlsMobCap();}
    public static int getUndergroundWaterCreatureMobCap() {return config.getUndergroundWaterCreatureMobCap();}
    public static int getWaterCreatureMobCap() {return config.getWaterCreatureMobCap();}
    public static int getWaterAmbientMobCap() {return config.getWaterAmbientMobCap();}

}
