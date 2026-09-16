package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.api.MobStackerAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    protected abstract void dropAllDeathLoot(DamageSource damageSource);

    @Unique
    private LivingEntity mobstacker$thisEntity;
    @Unique
    private Mob mobstacker$self;
    @Unique
    private int mobstacker$overflowKills = 0;
    @Unique
    private float mobstacker$overflowSurvivorHealth = -1.0F;
    // Per-mob Sweeping Edge: the sweep damage owed to the rest of the stack for the hit currently
    // being resolved, plus the raw damage it was computed from (used to re-apply the same armor
    // reduction). Both are set at hurt() HEAD and consumed once the hit lands.
    @Unique
    private float mobstacker$pendingSweepDamage = 0.0F;
    @Unique
    private float mobstacker$pendingSweepRawAmount = 0.0F;

    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "onChangedBlock", at = @At("HEAD"))
    private void mobstacker$onChangedBlock(CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (!mobstacker$thisEntity.level().isClientSide && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            if (MobStacker.getCanStack(mobstacker$self) && MobStacker.canStack(mobstacker$self)) {
                MobStacker.tryMergeIntoNearbyStack(mobstacker$self);
            }
        }
    }

    /**
     * Drop compaction — start buffering this stacked mob's death drops (items and/or experience)
     * before any loot is dropped. The buffer is flushed (merged into full stacks and a single XP
     * orb) at {@link #mobstacker$finishDropCompaction}. Only stacks are captured, so unstacked
     * mobs keep the vanilla drop path untouched.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void mobstacker$beginDropCompaction(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && self instanceof Mob mob && MobStacker.getStackSize(mob) > 1
                && (MobStacker.getCompactDrops(mob) || MobStacker.getCompactExperience(mob))) {
            MobStacker.beginDropCapture(mob);
        }
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void mobstacker$finishDropCompaction(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && self instanceof Mob mob) {
            MobStacker.finishDropCaptureAndCompact(mob);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void mobstacker$onDie(DamageSource damageSource, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (mobstacker$thisEntity instanceof Mob && damageSource.is(DamageTypes.GENERIC_KILL)) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            MobStacker.setStackSize(mobstacker$self, 1);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void mobstacker$onRemoveHead(RemovalReason reason, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (mobstacker$thisEntity instanceof Mob && !MobStacker.getKillWholeStackOnDeath(mobstacker$thisEntity)) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            int stackSize = MobStacker.getStackSize(mobstacker$self);

            // With damage overflow a single hit can kill several mobs at once; otherwise exactly one.
            int killed = (MobStacker.getDamageOverflow(mobstacker$self) && mobstacker$overflowKills > 0)
                    ? mobstacker$overflowKills : 1;
            int survivors = stackSize - killed;

            if (MobStacker.shouldSpawnNewEntity(mobstacker$self, reason) && survivors >= 1 && mobstacker$self.level() instanceof ServerLevel serverLevel) {
                MobStackerAPI.executeCustomDeathHandlers(mobstacker$self, mobstacker$self.getLastDamageSource());
                MobStacker.spawnNewEntity(serverLevel, mobstacker$self, survivors, mobstacker$overflowSurvivorHealth);
            }

            // Feedback when a stacked mob is killed: a particle "pop" near the mob, a floating
            // "-N" hologram above it, and/or an action bar line to the killer. Each is toggled
            // by its own config flag.
            if (stackSize > 1 && MobStacker.shouldSpawnNewEntity(mobstacker$self, reason)) {
                if (MobStacker.getStackKillParticles(mobstacker$self)) {
                    mobstacker$spawnStackKillParticles(mobstacker$self, killed);
                }
                if (MobStacker.getStackKillHologram(mobstacker$self) && mobstacker$self.level() instanceof ServerLevel serverLevel) {
                    MobStacker.spawnKillHologram(serverLevel, mobstacker$self, killed);
                }
                if (MobStacker.getStackKillActionBar(mobstacker$self)) {
                    mobstacker$sendStackKillFeedback(mobstacker$self, killed, Math.max(survivors, 0));
                }
            }

            mobstacker$overflowKills = 0;
            mobstacker$overflowSurvivorHealth = -1.0F;
        }
    }

    @Unique
    private void mobstacker$sendStackKillFeedback(Mob mob, int killed, int remaining) {
        if (!(mob.getKillCredit() instanceof ServerPlayer player)) {
            return;
        }
        String name = MobStacker.getLocalizedEntityName(mob.getType()).getString();
        Component message = Component.literal("Killed ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(killed + "× " + name).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(remaining + " left").withStyle(ChatFormatting.GREEN));
        player.displayClientMessage(message, true);
    }

    /**
     * A small particle "pop" at the mob when a hit clears one or more mobs off a stack. It scales
     * with how many died (capped so a huge overflow can't spam particles) and needs no extra
     * entities, keeping it cheap in line with the rest of the mod.
     */
    @Unique
    private void mobstacker$spawnStackKillParticles(Mob mob, int killed) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = mob.getX();
        double y = mob.getY() + mob.getBbHeight() * 0.6;
        double z = mob.getZ();
        // Both the amount and the height of the burst grow with the number killed, so a big
        // multi-kill visibly erupts higher and denser than a single kill (capped to stay cheap).
        double spreadXZ = mob.getBbWidth() * 0.5;
        double spreadY = mob.getBbHeight() * (0.4 + Math.min(killed, 20) * 0.18);
        int sparks = Math.min(10 + killed * 10, 160);
        int puffs = Math.min(3 + killed * 2, 48);
        serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, sparks, spreadXZ, spreadY, spreadXZ, 0.2);
        serverLevel.sendParticles(ParticleTypes.POOF, x, y, z, puffs, spreadXZ, spreadY * 0.6, spreadXZ, 0.03);
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;awardKillScore(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void mobstacker$onDieAllScore(DamageSource damageSource, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if(mobstacker$thisEntity instanceof Mob && MobStacker.getKillWholeStackOnDeath(mobstacker$thisEntity)) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            int stackSize = MobStacker.getStackSize(mobstacker$self);
            LivingEntity livingEntity = mobstacker$self.getKillCredit();
            if (mobstacker$self.deathScore >= 0 && livingEntity != null) {
                for (int i = 1; i < stackSize; i++) {
                    livingEntity.awardKillScore(mobstacker$self, mobstacker$self.deathScore, damageSource);
                }
            }
        }
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void mobstacker$onDieAllDropLoot(DamageSource damageSource, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if(mobstacker$thisEntity instanceof Mob && MobStacker.getKillWholeStackOnDeath(mobstacker$thisEntity)) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            int stackSize = MobStacker.getStackSize(mobstacker$self);
            for (int i = 1; i < stackSize; i++) {
                if(!mobstacker$self.level().isClientSide()) {
                    dropAllDeathLoot(damageSource);
                }
            }
        }
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void mobstacker$onDieAllCreateWRose(DamageSource damageSource, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if(mobstacker$thisEntity instanceof Mob && MobStacker.getKillWholeStackOnDeath(mobstacker$thisEntity)) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            int stackSize = MobStacker.getStackSize(mobstacker$self);
            LivingEntity livingEntity = mobstacker$self.getKillCredit();
            for (int i = 1; i < stackSize; i++) {
                mobstacker$self.createWitherRose(livingEntity);
            }
        }
    }

    /**
     * Sweeping Edge normally deals bonus damage to mobs <i>around</i> the target. Because a stack is
     * a single entity there is nothing around it, so the enchantment would otherwise do nothing here.
     * Vanilla's sweep damage is {@code 1 + attackDamage * (level / (level + 1))}, where the attack
     * damage already includes Sharpness / Smite / Bane of Arthropods -- and since every member of a
     * stack is the same mob type, {@code amount} here is exactly that value, so the right enchantment
     * bonus is baked in for free.
     * <p>
     * Two behaviours share this hook:
     * <ul>
     *   <li>default: one sweep's worth of damage is folded into the hit, feeding the damage overflow
     *       below, so a sweeping sword chews a little deeper into the stack;</li>
     *   <li>{@code sweepingEdgePerMob}: the hit itself is left alone and the sweep is dealt to every
     *       other member of the stack instead (see {@link #mobstacker$overflowDamage}), which is what
     *       a vanilla sweep through those same mobs standing loose would have done.</li>
     * </ul>
     */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float mobstacker$applySweepingEdgeToStack(float amount, DamageSource damageSource) {
        mobstacker$pendingSweepDamage = 0.0F;
        mobstacker$pendingSweepRawAmount = 0.0F;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof Mob mob)) {
            return amount;
        }
        if (!MobStacker.getDamageOverflow(mob) || !MobStacker.getSweepingEdgeOverflow(mob)) {
            return amount;
        }
        if (MobStacker.getStackSize(mob) <= 1 || !damageSource.is(DamageTypes.PLAYER_ATTACK)) {
            return amount;
        }
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) {
            return amount;
        }
        int level = EnchantmentHelper.getEnchantmentLevel(Enchantments.SWEEPING_EDGE, attacker);
        if (level <= 0) {
            return amount;
        }
        if (MobStacker.getSweepingEdgeVanillaConditions(mob) && !MobStacker.hadVanillaSweepConditions(attacker)) {
            return amount;
        }
        // Vanilla: sweepDamage = 1.0 + (level / (level + 1)) * attackDamage.
        float ratio = (float) level / (level + 1);
        float sweepDamage = 1.0F + ratio * amount;
        if (MobStacker.getSweepingEdgePerMob(mob)) {
            // Dealt to the other members once this hit resolves, where post-armor damage is known.
            mobstacker$pendingSweepDamage = sweepDamage;
            mobstacker$pendingSweepRawAmount = amount;
            return amount;
        }
        return amount + sweepDamage;
    }

    /**
     * The sweep damage this hit owes the members under the top mob, softened by the same
     * armor / absorption reduction the main hit just took (every member wears the same gear). Also
     * clears the pending values, so a hit that never reaches the stack cannot leak into the next one.
     *
     * @param dealt the damage the top mob actually took, after armor
     */
    @Unique
    private float mobstacker$consumeSweepDamage(Mob mob, float dealt) {
        float rawSweep = mobstacker$pendingSweepDamage;
        float rawAmount = mobstacker$pendingSweepRawAmount;
        mobstacker$pendingSweepDamage = 0.0F;
        mobstacker$pendingSweepRawAmount = 0.0F;

        if (rawSweep <= 0.0F || rawAmount <= 0.0F || dealt <= 0.0F || !MobStacker.getSweepingEdgePerMob(mob)) {
            return 0.0F;
        }
        float reduction = Math.max(0.0F, Math.min(1.0F, dealt / rawAmount));
        return rawSweep * reduction;
    }

    /**
     * Damage overflow, and with it per-mob Sweeping Edge. When a hit would reduce the top mob of a
     * stack below 0 HP, the leftover damage is carried onto the mobs underneath it. We compute how
     * many mobs the hit actually kills (and how wounded the next survivor is left) here, then
     * {@link #mobstacker$overflowDeathLoot} drops the extra loot and {@link #mobstacker$onRemoveHead}
     * spawns the wounded remainder.
     * <p>
     * A vanilla sweep hits every mob standing <i>around</i> the target, whether or not the target
     * dies, and those mobs keep the wound until something finishes them off. A stack keeps all of its
     * members in one spot and they are identical, so the sweep is recorded once for all of them (see
     * {@link MobStacker#getStackMemberDamage}) and deepens with every swing, exactly as a herd of
     * loose mobs would wear down.
     */
    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void mobstacker$overflowDamage(LivingEntity instance, float newHealth) {
        if (instance.level().isClientSide() || !(instance instanceof Mob mob)
                || MobStacker.getKillWholeStackOnDeath(instance) || !MobStacker.getDamageOverflow(instance)) {
            mobstacker$pendingSweepDamage = 0.0F;
            mobstacker$pendingSweepRawAmount = 0.0F;
            instance.setHealth(newHealth);
            return;
        }

        int stackSize = MobStacker.getStackSize(mob);
        float maxHealth = instance.getMaxHealth();
        float dealt = instance.getHealth() - newHealth;
        float memberDamage = MobStacker.getStackMemberDamage(mob) + mobstacker$consumeSweepDamage(mob, dealt);

        if (newHealth > 0.0F || stackSize <= 1 || maxHealth <= 0.0F) {
            // The top mob takes the whole hit while the members only take the smaller sweep, so it is
            // always the first to fall and nothing under it can die while it still stands. Should a
            // hit ever leave it alive with the members spent (a healed or freshly merged top mob),
            // they are held a sliver from death and the next swing takes them the ordinary way.
            MobStacker.setStackMemberDamage(mob, stackSize > 1 ? Math.min(memberDamage, maxHealth - 0.5F) : 0.0F);
            instance.setHealth(newHealth);
            return;
        }

        // The top mob is dead; work out how deep into the stack this hit reaches.
        int killed = 1;
        int remaining = stackSize - 1;
        float memberHealth = Math.max(0.5F, maxHealth - memberDamage);

        if (memberDamage >= maxHealth && remaining > 0) {
            // The accumulated sweep alone finished every member below the top one.
            int sweepKills = remaining;
            int cap = MobStacker.getSweepingEdgeMaxKills(mob);
            if (cap > 0) {
                sweepKills = Math.min(sweepKills, cap);
            }
            killed += sweepKills;
            remaining -= sweepKills;
            // Whoever the cap spared is left a sliver from death, not healed back up.
            memberDamage = maxHealth - 0.5F;
            memberHealth = 0.5F;
        }

        float overflow = -newHealth; // damage left over once the top mob's remaining health is gone
        int overflowKills = remaining > 0 ? Math.min((int) Math.floor(overflow / memberHealth), remaining) : 0;
        killed += overflowKills;
        remaining -= overflowKills;

        mobstacker$overflowKills = killed;
        if (remaining > 0) {
            float leftover = Math.max(0.0F, Math.min(overflow - overflowKills * memberHealth, memberHealth));
            mobstacker$overflowSurvivorHealth = Math.max(0.5F, memberHealth - leftover);
            MobStacker.setStackMemberDamage(mob, Math.min(memberDamage, maxHealth - 0.5F));
        } else {
            mobstacker$overflowSurvivorHealth = -1.0F;
            MobStacker.setStackMemberDamage(mob, 0.0F);
        }

        instance.setHealth(newHealth); // let the top mob die normally
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void mobstacker$overflowDeathLoot(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof Mob mob)) {
            return;
        }
        // killWholeStackOnDeath has its own multi-drop logic; /kill must not duplicate loot.
        if (MobStacker.getKillWholeStackOnDeath(mob) || !MobStacker.getDamageOverflow(mob)
                || damageSource.is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        int extraKilled = mobstacker$overflowKills - 1; // the top mob already dropped its loot
        if (extraKilled <= 0) {
            return;
        }
        LivingEntity killCredit = mob.getKillCredit();
        for (int i = 0; i < extraKilled; i++) {
            dropAllDeathLoot(damageSource);
            if (mob.deathScore >= 0 && killCredit != null) {
                killCredit.awardKillScore(mob, mob.deathScore, damageSource);
            }
            mob.createWitherRose(killCredit);
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mobstacker$onConstructed(EntityType<?> entityType, Level level, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (!level.isClientSide && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            if (MobStacker.getStackSize(mobstacker$self) == 1) {
                MobStacker.setStackSize(mobstacker$self, 1);
            }
            if (MobStacker.getCanStack(mobstacker$self)) {
                MobStacker.setCanStack(mobstacker$self, true);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void mobstacker$onReadAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (!mobstacker$thisEntity.level().isClientSide && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            MobStacker.updateStackDisplay(mobstacker$self);
        }
    }
}