package com.frikinjay.mobstacker.command;

import com.frikinjay.mobstacker.ICustomDataHolder;
import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.MobVariants;
import com.frikinjay.mobstacker.StackRefusal;
import com.frikinjay.mobstacker.config.StackRegion;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The commands that look at and act on the stacks standing in the world, rather than on the config:
 * {@code inspect} and {@code stats}. Kept apart from {@link MobStackerCommands}, which is about
 * settings and is long enough already.
 */
public final class StackToolsCommands {

    /** How far {@code inspect} looks along the player's line of sight for a mob. */
    private static final double LOOK_RANGE = 32.0;
    /** How far {@code inspect} looks around a mob for the nearest one of its kind. */
    private static final double NEIGHBOUR_RANGE = 16.0;
    /** How many mob types {@code stats} lists before it stops. */
    private static final int STATS_TYPES = 10;

    private StackToolsCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("inspect")
                        .executes(ctx -> inspect(ctx.getSource(), lookedAt(ctx.getSource())))
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("stats")
                        .executes(ctx -> stats(ctx.getSource(), -1))
                        .then(argument("radius", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> stats(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    // ============================================================ inspect

    /** The mob the command's player is looking at, up to the first block in the way, or null. */
    private static Entity lookedAt(CommandSourceStack source) {
        Entity viewer = source.getEntity();
        if (viewer == null) {
            return null;
        }
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = viewer.getViewVector(1.0F);
        double reach = LOOK_RANGE;
        HitResult block = viewer.pick(reach, 1.0F, false);
        if (block.getType() != HitResult.Type.MISS) {
            reach = block.getLocation().distanceTo(eye);
        }
        Vec3 end = eye.add(look.scale(reach));
        AABB searched = viewer.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, eye, end, searched,
                entity -> entity instanceof Mob && !entity.isSpectator(), reach * reach);
        return hit == null ? null : hit.getEntity();
    }

    private static int inspect(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob)) {
            source.sendFailure(Component.literal(entity == null
                    ? "Look at a mob, or name one: /mobstacker inspect <target>"
                    : "That is not a mob."));
            return 0;
        }
        int size = MobStacker.getStackSize(mob);
        String kind = MobStacker.getLocalizedEntityName(mob.getType()).getString();
        source.sendSuccess(() -> Component.literal("=== " + kind + (size > 1 ? " x" + size : " (not stacked)") + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        BlockPos pos = mob.blockPosition();
        StackRegion region = MobStacker.regionAt(mob);
        MutableComponent where = label("At ").append(value(pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                .append(label(" in ")).append(value(mob.level().dimension().location().toString()))
                .append(label(", "));
        where.append(region == null
                ? label("in no region")
                : label("region ").append(value(region.getName()))
                        .append(label(" (" + region.getType() + ", priority " + region.getPriority() + ")")));
        source.sendSuccess(() -> where, false);

        source.sendSuccess(() -> label("Ceiling here: ").append(value(String.valueOf(MobStacker.getMaxMobStackSize(mob))))
                .append(label(" (" + ceilingSource(mob, region) + ")"))
                .append(label("   stackMode ")).append(value(MobStacker.config.getStackMode().name()))
                .append(label("   stackRadius ")).append(value(String.valueOf(MobStacker.getStackRadius(mob)))), false);
        if (region != null && !region.getSettings().isEmpty()) {
            MutableComponent overrides = label("Set by the region: ");
            boolean first = true;
            for (Map.Entry<String, String> setting : region.getSettings().entrySet()) {
                if (!first) {
                    overrides.append(label(", "));
                }
                overrides.append(value(setting.getKey() + "=" + setting.getValue()));
                first = false;
            }
            source.sendSuccess(() -> overrides, false);
        }

        MutableComponent health = label("Health: ").append(value(round(mob.getHealth()) + "/" + round(mob.getMaxHealth())));
        if (size > 1) {
            float memberHealth = mob.getMaxHealth() - MobStacker.getStackMemberDamage(mob);
            health.append(label(", each mob under it ")).append(value(round(memberHealth) + "/" + round(mob.getMaxHealth())));
        }
        source.sendSuccess(() -> health, false);
        for (Component line : stackDataLines(mob)) {
            source.sendSuccess(() -> line, false);
        }

        StackRefusal own = ownRefusal(mob);
        source.sendSuccess(() -> own == null
                ? label("Stacking: ").append(Component.literal("yes").withStyle(ChatFormatting.GREEN))
                        .append(label(", it can join a stack or take mobs in"))
                : label("Stacking: ").append(Component.literal("no").withStyle(ChatFormatting.RED))
                        .append(label(" - " + own.reason())), false);

        Component neighbour = neighbourLine(mob, kind);
        source.sendSuccess(() -> neighbour, false);
        return 1;
    }

    /** Where the ceiling in force comes from, most specific first - the same order the game asks in. */
    private static String ceilingSource(Mob mob, StackRegion region) {
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        if (region != null && region.getMaxStackSize(typeId) != null) {
            return "the " + typeId + " ceiling in region '" + region.getName() + "'";
        }
        if (MobStacker.config.getMaxStackSize(typeId) != null) {
            return "the global " + typeId + " ceiling";
        }
        if (region != null && region.getSetting("maxStackSize") != null) {
            return "maxStackSize in region '" + region.getName() + "'";
        }
        return "maxStackSize";
    }

    /** What the stack remembers besides its size, one line per thing it has. */
    private static List<Component> stackDataLines(Mob mob) {
        List<Component> lines = new ArrayList<>();
        if (!(mob instanceof ICustomDataHolder holder)) {
            return lines;
        }
        CompoundTag data = holder.mobstacker$getCustomData();
        List<String> parts = new ArrayList<>();
        int loadouts = MobStacker.getMemberLoadouts(mob).size();
        if (loadouts > 0) {
            parts.add(loadouts + " members' gear");
        }
        if (data.contains(MobVariants.MEMBER_ROLLS_KEY, 9)) {
            parts.add(data.getList(MobVariants.MEMBER_ROLLS_KEY, 10).size() + " members' horse stats");
        }
        long now = mob.level().getGameTime();
        long cooldownEnd = data.getLong(MobStacker.BREED_COOLDOWN_END_KEY);
        int love = data.getInt(MobStacker.BREED_LOVE_KEY);
        int cooling = now < cooldownEnd ? data.getInt(MobStacker.BREED_COOLDOWN_COUNT_KEY) : 0;
        if (love > 0 || cooling > 0) {
            parts.add(love + " in love, " + cooling + " resting from breeding"
                    + (cooling > 0 ? " for " + ((cooldownEnd - now) / 20) + " s" : ""));
        }
        int grace = MobStacker.separationGrace(mob);
        if (grace > 0) {
            parts.add("kept out of stacks for " + (grace / 20) + " s more");
        }
        if (!data.getBoolean(MobStacker.CAN_STACK_KEY) && data.contains(MobStacker.CAN_STACK_KEY)) {
            parts.add("CanStack off");
        }
        if (!parts.isEmpty()) {
            lines.add(label("Remembers: ").append(value(String.join(", ", parts))));
        }
        Component given = MobStacker.playerGivenName(mob);
        if (given != null) {
            lines.add(label("Named by a player: ").append(given.copy())
                    .append(label(MobStacker.isNamedStack(mob) ? " (a stack's name, it keeps stacking)" : " (a pet's name)")));
        }
        return lines;
    }

    /** The first rule that keeps this mob out of stacking, its CanStack flag included. */
    private static StackRefusal ownRefusal(Mob mob) {
        return MobStacker.getCanStack(mob) ? MobStacker.stackRefusal(mob) : StackRefusal.CAN_STACK_OFF;
    }

    /**
     * What happens between this mob and the nearest one of its kind: they merge, and which survives,
     * or the first rule that stops them. Asked the way the scan asks it - the other mob has to be
     * within this one's stack radius and able to stack itself, and the bigger of the two survives.
     */
    private static Component neighbourLine(Mob mob, String kind) {
        Mob nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : mob.level().getEntities(mob, mob.getBoundingBox().inflate(NEIGHBOUR_RANGE),
                candidate -> candidate instanceof Mob && candidate.getClass() == mob.getClass() && candidate.isAlive())) {
            double distance = entity.distanceToSqr(mob);
            if (distance < best) {
                best = distance;
                nearest = (Mob) entity;
            }
        }
        if (nearest == null) {
            return label("No other " + kind.toLowerCase(Locale.ROOT) + " within " + (int) NEIGHBOUR_RANGE + " blocks.");
        }
        int otherSize = MobStacker.getStackSize(nearest);
        MutableComponent line = label("Nearest " + kind.toLowerCase(Locale.ROOT) + ": ")
                .append(value((otherSize > 1 ? "x" + otherSize : "a single one") + ", "
                        + String.format(Locale.ROOT, "%.1f", Math.sqrt(best)) + " blocks away"))
                .append(label(" - "));

        if (ownRefusal(mob) != null) {
            return line.append(no("this one cannot stack (see above)"));
        }
        StackRefusal theirs = ownRefusal(nearest);
        if (theirs != null) {
            return line.append(no("that one cannot stack: " + theirs.reason()));
        }
        double radius = MobStacker.getStackRadius(mob);
        if (!mob.getBoundingBox().inflate(radius).intersects(nearest.getBoundingBox())) {
            return line.append(no("too far apart for stackRadius " + radius));
        }
        boolean keepThis = MobStacker.getStackSize(mob) > otherSize;
        Mob target = keepThis ? mob : nearest;
        Mob source = keepThis ? nearest : mob;
        StackRefusal refusal = MobStacker.mergeRefusal(source, target);
        if (refusal == StackRefusal.DIFFERENT_VARIANT) {
            return line.append(no(refusal.reason() + ": " + MobVariants.variantDifference(source, target)));
        }
        if (refusal != null) {
            return line.append(no(refusal.reason()));
        }
        return line.append(Component.literal("they merge; " + (keepThis ? "this one" : "that one") + " survives")
                .withStyle(ChatFormatting.GREEN));
    }

    // ============================================================ stats

    /**
     * How many stacks there are and how many mobs they stand for, per type: in every loaded chunk of
     * every dimension, or within {@code radius} blocks of the command's source.
     */
    private static int stats(CommandSourceStack source, int radius) {
        Map<EntityType<?>, long[]> byType = new HashMap<>(); // stacks, mobs, largest
        long entities = 0;
        long represented = 0;
        long stacks = 0;
        Iterable<ServerLevel> levels = radius < 0 ? source.getServer().getAllLevels() : List.of(source.getLevel());
        Vec3 centre = source.getPosition();
        double reachSq = (double) radius * radius;
        for (ServerLevel level : levels) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                    continue;
                }
                if (radius >= 0 && entity.distanceToSqr(centre) > reachSq) {
                    continue;
                }
                int size = MobStacker.getStackSize(mob);
                entities++;
                represented += size;
                if (size > 1) {
                    stacks++;
                    long[] row = byType.computeIfAbsent(mob.getType(), type -> new long[3]);
                    row[0]++;
                    row[1] += size;
                    row[2] = Math.max(row[2], size);
                }
            }
        }
        long saved = represented - entities;
        String scope = radius < 0 ? "in loaded chunks" : "within " + radius + " blocks";
        source.sendSuccess(() -> Component.literal("=== Stacks " + scope + " ===").withStyle(ChatFormatting.GOLD), false);
        long finalEntities = entities;
        long finalRepresented = represented;
        long finalStacks = stacks;
        source.sendSuccess(() -> value(count(finalEntities)).append(label(" mob entities stand for "))
                .append(value(count(finalRepresented))).append(label(" mobs; "))
                .append(value(count(finalStacks))).append(label(finalStacks == 1 ? " stack saves " : " stacks save "))
                .append(Component.literal(count(saved)).withStyle(ChatFormatting.GREEN))
                .append(label(" entities.")), false);

        List<Map.Entry<EntityType<?>, long[]>> rows = new ArrayList<>(byType.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        for (int i = 0; i < rows.size() && i < STATS_TYPES; i++) {
            Map.Entry<EntityType<?>, long[]> row = rows.get(i);
            long[] numbers = row.getValue();
            source.sendSuccess(() -> label(" " + BuiltInRegistries.ENTITY_TYPE.getKey(row.getKey()) + ": ")
                    .append(value(count(numbers[1]))).append(label(" mobs in "))
                    .append(value(count(numbers[0]))).append(label(numbers[0] == 1 ? " stack" : " stacks"))
                    .append(label(" (largest x" + numbers[2] + ")")), false);
        }
        if (rows.size() > STATS_TYPES) {
            source.sendSuccess(() -> label(" ... and " + (rows.size() - STATS_TYPES) + " more kinds"), false);
        }
        return (int) Math.min(Integer.MAX_VALUE, stacks);
    }

    // ============================================================ formatting

    private static MutableComponent label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent value(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent no(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    private static String round(float number) {
        return number == Math.rint(number) ? String.valueOf((int) number) : String.format(Locale.ROOT, "%.1f", number);
    }

    private static String count(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }
}
