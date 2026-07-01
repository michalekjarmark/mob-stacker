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

    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "onChangedBlock", at = @At("HEAD"))
    private void mobstacker$onChangedBlock(CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (!mobstacker$thisEntity.level().isClientSide && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            if (MobStacker.getCanStack(mobstacker$self) && MobStacker.canStack(mobstacker$self)) {
                mobstacker$self.level().getEntities(mobstacker$self, mobstacker$self.getBoundingBox().inflate(MobStacker.getStackRadius()),
                                e -> e instanceof Mob && MobStacker.canStack((Mob) e))
                        .stream()
                        .filter(nearby -> MobStacker.canMerge(mobstacker$self, (Mob) nearby))
                        .findFirst()
                        .ifPresent(nearby -> MobStacker.mergeEntities((Mob) nearby, mobstacker$self));
            }
        }
    }

    /**
     * Drop compaction — start buffering this stacked mob's death drops before any loot is dropped.
     * The buffer is flushed (merged into full stacks) at {@link #mobstacker$finishDropCompaction}.
     * Only stacks are captured, so unstacked mobs keep the vanilla drop path untouched.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void mobstacker$beginDropCompaction(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && self instanceof Mob mob
                && MobStacker.getCompactDrops() && MobStacker.getStackSize(mob) > 1) {
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
        if (!MobStacker.getKillWholeStackOnDeath() && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            int stackSize = MobStacker.getStackSize(mobstacker$self);

            // With damage overflow a single hit can kill several mobs at once; otherwise exactly one.
            int killed = (MobStacker.getDamageOverflow() && mobstacker$overflowKills > 0)
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
                if (MobStacker.getStackKillParticles()) {
                    mobstacker$spawnStackKillParticles(mobstacker$self, killed);
                }
                if (MobStacker.getStackKillHologram() && mobstacker$self.level() instanceof ServerLevel serverLevel) {
                    MobStacker.spawnKillHologram(serverLevel, mobstacker$self, killed);
                }
                if (MobStacker.getStackKillActionBar()) {
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
        if(mobstacker$thisEntity instanceof Mob && MobStacker.getKillWholeStackOnDeath()) {
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
        if(mobstacker$thisEntity instanceof Mob && MobStacker.getKillWholeStackOnDeath()) {
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
        if(mobstacker$thisEntity instanceof Mob && MobStacker.getKillWholeStackOnDeath()) {
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
     * We fold the vanilla sweep damage back into the hit on the stack, where it feeds the damage
     * overflow below (so a sweeping sword chews through more mobs per swing, like it should).
     */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float mobstacker$applySweepingEdgeToStack(float amount, DamageSource damageSource) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof Mob mob)) {
            return amount;
        }
        if (!MobStacker.getDamageOverflow() || !MobStacker.getSweepingEdgeOverflow()) {
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
        // Vanilla: sweepDamage = 1.0 + (level / (level + 1)) * attackDamage.
        float ratio = (float) level / (level + 1);
        float sweepBonus = 1.0F + ratio * amount;
        return amount + sweepBonus;
    }

    /**
     * Damage overflow. When a hit would reduce the top mob of a stack below 0 HP, the leftover damage
     * is carried onto the mobs underneath it. We compute how many mobs the hit actually kills (and how
     * wounded the next survivor is left) here, then {@link #mobstacker$overflowDeathLoot} drops the
     * extra loot and {@link #mobstacker$onRemoveHead} spawns the wounded remainder.
     */
    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void mobstacker$overflowDamage(LivingEntity instance, float newHealth) {
        if (instance.level().isClientSide() || !(instance instanceof Mob mob)
                || MobStacker.getKillWholeStackOnDeath() || !MobStacker.getDamageOverflow()) {
            instance.setHealth(newHealth);
            return;
        }

        int stackSize = MobStacker.getStackSize(mob);
        if (newHealth > 0.0F || stackSize <= 1) {
            instance.setHealth(newHealth);
            return;
        }

        float maxHealth = instance.getMaxHealth();
        float overflow = -newHealth; // damage left over once the top mob's remaining health is gone
        int extraKills = maxHealth > 0.0F ? (int) Math.floor(overflow / maxHealth) : 0;
        int totalKilled = Math.min(1 + extraKills, stackSize);

        mobstacker$overflowKills = totalKilled;
        if (totalKilled < stackSize) {
            float leftover = overflow - (totalKilled - 1) * maxHealth; // 0 .. maxHealth, hurts the next mob
            leftover = Math.max(0.0F, Math.min(leftover, maxHealth));
            mobstacker$overflowSurvivorHealth = maxHealth - leftover;
        } else {
            mobstacker$overflowSurvivorHealth = -1.0F;
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
        if (MobStacker.getKillWholeStackOnDeath() || !MobStacker.getDamageOverflow()
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