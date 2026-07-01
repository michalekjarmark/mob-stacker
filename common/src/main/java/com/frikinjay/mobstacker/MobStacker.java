package com.frikinjay.mobstacker;

import com.frikinjay.mobstacker.api.MobStackerAPI;
import com.frikinjay.mobstacker.config.MobStackerConfig;
import com.frikinjay.mobstacker.config.StackMode;
import com.frikinjay.mobstacker.config.StackRegion;
import com.frikinjay.mobstacker.mixin.ArmorStandAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

public final class MobStacker {
    public static final String MOD_ID = "mobstacker";

    public static final Logger logger = LogUtils.getLogger();
    public static final String STACK_DATA_KEY = "StackData";
    public static final String STACK_SIZE_KEY = "StackSize";
    public static final String CAN_STACK_KEY = "CanStack";

    // --- Stack breeding (feeding a stacked adult its food breeds its members in pairs) ---
    // Members currently "in love" waiting for a partner; kept so partial feeding never wastes food.
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

    private record KillHologram(ArmorStand entity, long expireGameTime) {}

    private static final Map<Class<? extends Mob>, BiPredicate<Mob, Mob>> VARIANT_CHECKERS = Map.of(
            Sheep.class, (self, other) -> ((Sheep)self).getColor() == ((Sheep)other).getColor()
                    && ((Sheep)self).isSheared() == ((Sheep)other).isSheared(),
            Villager.class, (self, other) -> checkVillagerMatch((Villager)self, (Villager)other),
            ZombieVillager.class, (self, other) -> checkZombieVillagerMatch((ZombieVillager)self, (ZombieVillager)other),
            Slime.class, (self, other) -> ((Slime)self).getSize() == ((Slime)other).getSize(),
            Frog.class, (self, other) -> ((Frog)self).getVariant() == ((Frog)other).getVariant(),
            Axolotl.class, (self, other) -> ((Axolotl)self).getVariant() == ((Axolotl)other).getVariant(),
            Cat.class, (self, other) -> ((Cat)self).getVariant() == ((Cat)other).getVariant(),
            Fox.class, (self, other) -> ((Fox)self).getVariant() == ((Fox)other).getVariant(),
            MushroomCow.class, (self, other) -> ((MushroomCow)self).getVariant() == ((MushroomCow)other).getVariant()
    );

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
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
            Files.createDirectories(dir);
            configFile = dir.resolve("mobstacker.json").toFile();
        } catch (Exception e) {
            logger.error("MobStacker: could not resolve the per-world config path, using the global file", e);
            configFile = new File("config/mobstacker.json");
        }
        config = MobStackerConfig.load();
        config.save();
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
                    ? config.getEnableAnimalBabyStacking()
                    : config.getEnableHostileBabyStacking();
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
        if (!config.getStackEquippedMobs() && hasEquipment(entity)) {
            return false;
        }

        if (!isStackingAllowedAt(entity)) {
            return false;
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (config.getIgnoredEntities().contains(entityId.toString()) ||
                config.getIgnoredMods().contains(entityId.getNamespace())) {
            return false;
        }

        return hasValidCustomNameForStacking(entity) && getStackSize(entity) < getMaxMobStackSize();
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

        // StackMode.REGIONS: only stack when inside at least one ALLOW region.
        return insideAllowRegion;
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

        if ((getStackSize(self) + getStackSize(nearby)) > getMaxMobStackSize()) {
            return false;
        }

        // Never mix a baby with an adult (they carry different growth state). Two babies may
        // merge freely; mergeEntities keeps the youngest age so none grows up early.
        if (self.isBaby() != nearby.isBaby()) {
            return false;
        }

        BiPredicate<Mob, Mob> variantChecker = VARIANT_CHECKERS.get(self.getClass());
        if (variantChecker != null && !variantChecker.test(self, nearby)) {
            return false;
        }

        return MobStackerAPI.checkCustomMergingConditions(self, nearby);
    }

    private static boolean checkVillagerMatch(Villager self, Villager other) {
        return self.getVariant() == other.getVariant()
                && self.getVillagerData().getProfession() == VillagerProfession.NONE
                && other.getVillagerData().getProfession() == VillagerProfession.NONE;
    }

    private static boolean checkZombieVillagerMatch(ZombieVillager self, ZombieVillager other) {
        return self.getVariant() == other.getVariant()
                && self.getVillagerData().getProfession() == VillagerProfession.NONE
                && other.getVillagerData().getProfession() == VillagerProfession.NONE;
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
        MobStacker.setStackSize(newEntity, newStackSize);
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
        MobStackerAPI.applyEntityDataModifiers(source, target);
    }

    private static void copyVariantData(Mob source, Mob target) {
        if (source instanceof Sheep sourceSheep && target instanceof Sheep targetSheep) {
            targetSheep.setSheared(sourceSheep.isSheared());
            targetSheep.setColor(sourceSheep.getColor());
        } else if (source instanceof Villager sourceVillager && target instanceof Villager targetVillager) {
            targetVillager.setVillagerData(sourceVillager.getVillagerData());
            targetVillager.setVariant(sourceVillager.getVariant());
        } else if (source instanceof ZombieVillager sourceZombie && target instanceof ZombieVillager targetZombie) {
            targetZombie.setVillagerData(sourceZombie.getVillagerData());
            targetZombie.setVariant(sourceZombie.getVariant());
        } else if (source instanceof Slime sourceSlime && target instanceof Slime targetSlime) {
            targetSlime.setSize(sourceSlime.getSize(), true);
        } else if (source instanceof Frog sourceFrog && target instanceof Frog targetFrog) {
            targetFrog.setVariant(sourceFrog.getVariant());
        } else if (source instanceof Axolotl sourceAxolotl && target instanceof Axolotl targetAxolotl) {
            targetAxolotl.setVariant(sourceAxolotl.getVariant());
        } else if (source instanceof Cat sourceCat && target instanceof Cat targetCat) {
            targetCat.setVariant(sourceCat.getVariant());
        } else if (source instanceof Fox sourceFox && target instanceof Fox targetFox) {
            targetFox.setVariant(sourceFox.getVariant());
        } else if (source instanceof MushroomCow sourceCow && target instanceof MushroomCow targetCow) {
            targetCow.setVariant(sourceCow.getVariant());
        }
    }

    public static void separateEntity(Mob entity) {
        if (entity.level().isClientSide()) return;

        try {
            ServerLevel serverLevel = (ServerLevel) entity.level();
            EntityType<?> entityType = entity.getType();
            Mob newEntity = (Mob) entityType.create(entity.level());
            if (newEntity == null) return;

            setStackSize(entity, getStackSize(entity) - 1);

            copyEntityDataForSeparation(entity, newEntity, serverLevel);
            handleHealthOnSeparation(entity, newEntity);

            // Apply custom entity data
            MobStackerAPI.applyEntityDataModifiersOnSeparation(entity, newEntity);
            entity.level().addFreshEntity(newEntity);

        } catch (Exception e) {
            setStackSize(entity, getStackSize(entity) + 1);
            logger.error("Error occurred while separating entity: {}", e.getMessage());
        }
    }

    private static void copyEntityDataForSeparation(Mob source, Mob target, ServerLevel serverLevel) {
        target.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(source.blockPosition()),
                MobSpawnType.NATURAL, null, null);
        target.moveTo(source.position().x, source.position().y, source.position().z,
                source.getYRot(), source.getXRot());
        target.yBodyRot = source.yBodyRot;

        Component newName = Component.literal("Lone " + getLocalizedEntityName(source.getType()).getString());
        target.setCustomName(newName);

        copyVariantData(source, target);
    }

    private static void handleHealthOnSeparation(Mob source, Mob target) {
        if (getStackHealth() && source.getHealth() > target.getMaxHealth()) {
            source.setHealth(source.getHealth() - target.getMaxHealth());
        }
    }

    public static void mergeEntities(Mob target, Mob source) {
        int newStackSize = Math.min(getStackSize(target) + getStackSize(source), getMaxMobStackSize());

        // When two babies merge, keep the youngest (most negative) age so no member ever grows up
        // early — this replaces a strict age-band gate and lets baby-stacks freely consolidate.
        Integer mergedBabyAge = null;
        if (target.isBaby() && source.isBaby()
                && target instanceof AgeableMob targetAge && source instanceof AgeableMob sourceAge) {
            mergedBabyAge = Math.min(targetAge.getAge(), sourceAge.getAge());
        }

        CompoundTag targetNbt = new CompoundTag();
        target.saveWithoutId(targetNbt);

        dropPickedEquipment(source);
        dropPickedEquipment(target);

        copyRelevantNbtData(source, targetNbt);

        updateStackDataInNbt(targetNbt, newStackSize);

        target.load(targetNbt);

        updateHealth(target, source);

        if (mergedBabyAge != null && target instanceof AgeableMob mergedAge) {
            mergedAge.setAge(mergedBabyAge);
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
                key.equals("Attributes") || key.equals(STACK_DATA_KEY);
    }

    private static void updateStackDataInNbt(CompoundTag nbt, int stackSize) {
        CompoundTag stackData = nbt.contains(STACK_DATA_KEY, 10) ?
                nbt.getCompound(STACK_DATA_KEY) : new CompoundTag();
        stackData.putInt(STACK_SIZE_KEY, stackSize);
        nbt.put(STACK_DATA_KEY, stackData);
    }

    public static void updateStackDisplay(Mob entity) {
        int stackSize = getStackSize(entity);
        boolean isBoss = isBossEntity(entity);

        Component newName = generateNewDisplayName(entity, stackSize);
        updateEntityName(entity, newName, isBoss);
    }

    private static Component generateNewDisplayName(Mob entity, int stackSize) {
        if (entity.hasCustomName() && !matchesStackedName(entity.getCustomName().getString(), entity)) {
            String baseName = STACKED_NAME_PATTERN.matcher(entity.getCustomName().getString())
                    .replaceFirst("");
            return Component.literal(baseName + (stackSize > 1 ? " x" + stackSize : ""))
                    .withStyle(entity.getCustomName().getStyle());
        }
        return stackSize > 1 ?
                Component.literal(getLocalizedEntityName(entity.getType()).getString() + " x" + stackSize) :
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

        if (getStackHealth() && getKillWholeStackOnDeath()) {
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

    public static boolean hasValidCustomNameForStacking(Mob entity) {
        return !entity.hasCustomName() ||
                matchesStackedName(entity.getCustomName().getString(), entity);
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
        int limit = getBreedOnePerClick() ? Math.min(1, free) : free;
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
        int limit = getBreedOnePerClick() ? 1 : stackSize;
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
        int max = getMaxMobStackSize();
        int remaining = count;

        BiPredicate<Mob, Mob> variantChecker = VARIANT_CHECKERS.get(parent.getClass());
        for (Entity nearby : level.getEntities(parent, parent.getBoundingBox().inflate(getStackRadius()),
                e -> e != parent && e.getClass() == parent.getClass() && ((Mob) e).isBaby())) {
            if (remaining <= 0) {
                break;
            }
            Mob existing = (Mob) nearby;
            if (variantChecker != null && !variantChecker.test(parent, existing)) {
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
            updateStackDisplay(entity);
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

    public static double getStackRadius() {return config.getStackRadius();}

    public static int getMaxMobStackSize() {return config.getMaxMobStackSize();}

    public static boolean getKillWholeStackOnDeath() {return config.getKillWholeStackOnDeath();}

    public static boolean getStackHealth() {return config.getStackHealth();}

    public static boolean getDamageOverflow() {return config.getDamageOverflow();}

    public static boolean getSweepingEdgeOverflow() {return config.getSweepingEdgeOverflow();}

    public static boolean getStackEquippedMobs() {return config.getStackEquippedMobs();}

    public static boolean getStackKillActionBar() {return config.getStackKillActionBar();}

    public static boolean getStackKillParticles() {return config.getStackKillParticles();}

    public static boolean getStackKillHologram() {return config.getStackKillHologram();}

    public static boolean getEnableStackBreeding() {return config.getEnableStackBreeding();}

    public static boolean getBreedOnePerClick() {return config.getBreedOnePerClick();}

    public static boolean getEnableAnimalBabyStacking() {return config.getEnableAnimalBabyStacking();}

    public static boolean getEnableHostileBabyStacking() {return config.getEnableHostileBabyStacking();}

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
        stand.setCustomName(Component.literal("-" + killed).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        stand.setCustomNameVisible(true);
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

    public static boolean getEnableSeparator() {return config.getEnableSeparator();}

    public static boolean getConsumeSeparator() {return config.getConsumeSeparator();}

    public static String getSeparatorItem() {return config.getSeparatorItem();}

    public static int getMonsterMobCap() {return config.getMonsterMobCap();}
    public static int getCreatureMobCap() {return config.getCreatureMobCap();}
    public static int getAmbientMobCap() {return config.getAmbientMobCap();}
    public static int getAxolotlsMobCap() {return config.getAxolotlsMobCap();}
    public static int getUndergroundWaterCreatureMobCap() {return config.getUndergroundWaterCreatureMobCap();}
    public static int getWaterCreatureMobCap() {return config.getWaterCreatureMobCap();}
    public static int getWaterAmbientMobCap() {return config.getWaterAmbientMobCap();}

}
