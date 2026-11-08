package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.StackAi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity {

    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Shadow
    private boolean persistenceRequired;

    @Shadow
    protected abstract boolean shouldDespawnInPeaceful();

    /** Whether the mob was persistent before the pickup now running (despawnPickedUpMobs). */
    @Unique
    private boolean mobstacker$persistentBeforePickup;

    /** {@code stackAi} as it applies to this mob, worked out once a tick; see {@link #mobstacker$aiMode}. */
    @Unique
    private StackAi mobstacker$aiMode = StackAi.NORMAL;
    @Unique
    private int mobstacker$aiModeTick = -1;

    protected MobMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    // --- despawnPickedUpMobs (1.12.0): picking an item up no longer makes a mob persistent ---
    // Vanilla's pickup (equipItemIfPossible, a piglin's off hand) ends in setItemSlotAndDropWhenKilled,
    // which marks the slot as a guaranteed drop and sets persistenceRequired. The mark stays, which is
    // how dropOnDespawn knows the item later; only the persistence is put back to what it was, so a
    // mob that was already persistent (named, from a structure, set by a command) stays so.

    @Inject(method = "setItemSlotAndDropWhenKilled", at = @At("HEAD"))
    private void mobstacker$beforePickup(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        mobstacker$persistentBeforePickup = persistenceRequired;
    }

    @Inject(method = "setItemSlotAndDropWhenKilled", at = @At("TAIL"))
    private void mobstacker$afterPickup(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (!mobstacker$persistentBeforePickup && !level().isClientSide()
                && MobStacker.getDespawnPickedUpMobs(this)) {
            persistenceRequired = false;
        }
    }

    /**
     * persistentStackSize: a stack that big does not despawn, treated as vanilla treats a persistent
     * mob (its idle timer starts over). Peaceful still clears monsters.
     */
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void mobstacker$keepBigStacks(CallbackInfo ci) {
        if (level().isClientSide() || !MobStacker.keepsFromDespawning((Mob) (Object) this)) {
            return;
        }
        if (level().getDifficulty() == Difficulty.PEACEFUL && shouldDespawnInPeaceful()) {
            return;
        }
        noActionTime = 0;
        ci.cancel();
    }

    /** stackOutgoingDamage: the hit of vanilla's standard melee attack, as hard as the whole stack. */
    @ModifyArg(method = "doHurtTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float mobstacker$stackMeleeDamage(float damage) {
        return MobStacker.stackMeleeDamage((Mob) (Object) this, damage);
    }

    /** Every despawn in checkDespawn (far away, idle, peaceful) drops what the mob had picked up. */
    @Inject(method = "checkDespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;discard()V"))
    private void mobstacker$dropOnDespawn(CallbackInfo ci) {
        MobStacker.dropOnDespawn((Mob) (Object) this);
    }

    /**
     * Gives every mob a chance to join a nearby stack: once on its first tick, and periodically
     * after that. Merging is otherwise only attempted when a mob crosses a block boundary, so mobs
     * that never move - several spawn eggs used on one block, mobs with no AI, a penned-in group -
     * would stand side by side and never stack, and a spawner batch would wait out a whole scan
     * interval first. The first tick is also the earliest point at which a mob can safely be merged,
     * since finalizeSpawn has run and its variant and age are settled by then.
     * <p>
     * The timer, the staggering and the cheap early-outs live in MobStacker#tickStackScan.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void mobstacker$periodicStackScan(CallbackInfo ci) {
        if (mobstacker$aiMode() != StackAi.NORMAL) {
            // serverAiStep counts this for despawning, and a stack without AI no longer runs it: a
            // stack nobody goes near would otherwise never despawn the way its mobs would have.
            noActionTime++;
            // A creeper that was swelling when its AI stopped would go on swelling, with nothing left
            // to call it off, and blow the stack up. One lit with flint and steel still goes off.
            if ((Object) this instanceof Creeper creeper && !creeper.isIgnited() && creeper.getSwellDir() > 0) {
                creeper.setSwellDir(-1);
            }
        }
        MobStacker.tickStackScan((Mob) (Object) this);
    }

    /**
     * What {@code stackAi} asks of this mob, asked once a tick rather than every time: the game checks
     * {@code isEffectiveAi} several times a tick, and the answer needs the stack size and a region
     * lookup. A tick late is the most it can be wrong by, just after a merge. Always NORMAL on a client.
     */
    @Unique
    private StackAi mobstacker$aiMode() {
        if (level().isClientSide()) {
            return StackAi.NORMAL;
        }
        if (mobstacker$aiModeTick != tickCount) {
            mobstacker$aiModeTick = tickCount;
            mobstacker$aiMode = MobStacker.stackAiFor((Mob) (Object) this);
        }
        return mobstacker$aiMode;
    }

    /**
     * {@code stackAi FROZEN}: the stack answers as a mob with vanilla's NoAI does, so it neither thinks
     * nor moves - vanilla skips the AI step and all movement for a mob that is not "effective AI".
     *
     * <p>The NoAI flag itself is never set. It is saved with the mob, so every stack would have stayed
     * frozen after the mod was removed, and a split or a merge would have had to carry it around.
     */
    @Inject(method = "isEffectiveAi", at = @At("RETURN"), cancellable = true)
    private void mobstacker$frozenStack(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && mobstacker$aiMode() == StackAi.FROZEN) {
            cir.setReturnValue(false);
        }
    }

    /**
     * {@code stackAi PHYSICS_ONLY}: the stack skips its whole AI step - goals, brain, pathfinding,
     * looking and moving on purpose - and nothing else, so gravity, water and knockback still act on
     * it. What it was walking towards is let go of, and it is kept afloat if its kind would swim.
     */
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void mobstacker$physicsOnlyStack(CallbackInfo ci) {
        if (mobstacker$aiMode() != StackAi.PHYSICS_ONLY) {
            return;
        }
        Mob self = (Mob) (Object) this;
        self.setXxa(0.0F);
        self.setYya(0.0F);
        self.setZza(0.0F);
        boolean deepEnough = (self.isInWater() && self.getFluidHeight(FluidTags.WATER) > self.getFluidJumpThreshold())
                || self.isInLava();
        self.setJumping(deepEnough && MobStacker.keepsAfloat(self, goalSelector));
        ci.cancel();
    }

    /**
     * Remembers how this mob came into the world, for the Spawn origin settings. The first call is the
     * one that counts: vanilla's own, when the mob is spawned. Every later one is the mod setting up a
     * mob it made, which then takes its origin from the mob it was made from.
     */
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void mobstacker$recordOrigin(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                         //? if >=1.20.5 {
                                         /*SpawnGroupData spawnData,
                                         *///?} else {
                                         SpawnGroupData spawnData, CompoundTag entityTag,
                                         //?}
                                         CallbackInfoReturnable<SpawnGroupData> cir) {
        MobStacker.recordOrigin((Mob) (Object) this, reason);
    }

    @Inject(method = "convertTo", at = @At("RETURN"), cancellable = true)
    private <T extends Mob> void mobstacker$convertTo(EntityType<T> entityType, boolean bl, CallbackInfoReturnable<T> cir) {
        Mob instance = (Mob) (Object) this;
        T mob = cir.getReturnValue();
        if (mob == null) {
                cir.setReturnValue(null);
        } else {
            // A stack a player named keeps its name through a conversion (zombie -> drowned, ...),
            // so the label is rebuilt from the name they gave rather than dropped on the floor.
            MobStacker.carryStackInto(instance, mob);
        }
    }

    /**
     * Touching a herd hands you one animal out of it.
     *
     * <p>A horse is an individual: it is tamed, saddled, given armor and ridden one at a time, and
     * every one of those acts on a stack would have applied to all sixteen at once — you would
     * climb onto a single entity carrying a whole herd. So a stacked mount steps one mob out of the
     * stack and lets that mob take the interaction instead, for every kind of right-click there is.
     *
     * <p>A saddle on a pig or strider does the same, for the same reason as a horse's
     * ({@link MobStacker#isSaddlingInteraction}), and so do a dye on a sheep and a golden apple on a
     * zombie villager, which vanilla takes one per mob. A bucket does too, for the opposite reason: bucketing takes the entity out of the
     * world and puts it in an item, so on a stack of fish the whole shoal went into one bucket and
     * a single fish came back out of it. Shears do too while {@code stackedHarvest} is off, because
     * shearing changes the mob and the stack is one mob (see
     * {@link MobStacker#isShearingOneInteraction}).
     *
     * <p>A wolf, cat or parrot pack does the same, but only when the player is actually holding the
     * thing that tames it. That asymmetry is deliberate: a horse answers every right-click with
     * something, while an untamed wolf ignores an empty hand entirely, and peeling a wolf off the
     * pack for a click that would have done nothing would be worse than the bug being fixed.
     *
     * <p>Server side only. The client keeps predicting the interaction against the stack exactly as
     * vanilla would, and the server's answer — a new mob, a smaller stack — arrives right after.
     * That is harmless for everything except a bucket, whose prediction throws the client's copy of
     * the stack away; the stack is sent to the client again once the bucket is filled.
     */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void mobstacker$interactWithOneOfTheStack(Player player, InteractionHand hand,
                                                      CallbackInfoReturnable<InteractionResult> cir) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        // Still working on the animal the stack handed over a moment ago: hold it out of the herd
        // for another while. Without this the taming attempts run out of time rather than out of
        // bones - the mob rejoins mid-session and the next click peels off a fresh one.
        MobStacker.refreshSeparationGrace(self);
        if (MobStacker.getStackSize(self) <= 1) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (MobStacker.isSeparatorInteraction(self, held)) {
            return; // PlayerMixin already took one out at interactOn; two would leave the stack
        }
        boolean bucketing = MobStacker.isBucketingInteraction(self, held);
        boolean dyeing = MobStacker.isDyeingInteraction(self, held);
        if (!(self instanceof AbstractHorse)
                && !MobStacker.isTamingInteraction(self, held)
                && !MobStacker.isSaddlingInteraction(self, held)
                && !MobStacker.isCuringInteraction(self, held)
                && !dyeing
                && !bucketing
                && !MobStacker.isShearingOneInteraction(self, held)) {
            return;
        }
        Mob separated = MobStacker.separateOne(self, false);
        if (separated != null) {
            InteractionResult result = separated.interact(player, hand);
            if (dyeing && !result.consumesAction()) {
                // A dye is the item's interaction, not the sheep's: Player.interactOn would hand it
                // the stack that was clicked, which is the very thing this is here to avoid.
                result = held.interactLivingEntity(player, separated, hand);
            }
            if (bucketing) {
                MobStacker.resendToClients(self);
            }
            cir.setReturnValue(result);
        }
    }

}
