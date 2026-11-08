package com.frikinjay.mobstacker.command;

import com.frikinjay.mobstacker.ICustomDataHolder;
import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.MobVariants;
import com.frikinjay.mobstacker.SpawnOrigin;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
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
import com.frikinjay.mobstacker.compat.Compat;

/**
 * The commands that look at and act on the stacks standing in the world, rather than on the config:
 * {@code inspect}, {@code stats}, {@code unstack} and {@code stack}. Kept apart from
 * {@link MobStackerCommands}, which is about settings and is long enough already.
 */
public final class StackToolsCommands {

    /** How far {@code inspect} looks along the player's line of sight for a mob. */
    private static final double LOOK_RANGE = 32.0;
    /** How far {@code inspect} looks around a mob for the nearest one of its kind. */
    private static final double NEIGHBOUR_RANGE = 16.0;
    /** How many mob types {@code stats} lists before it stops. */
    private static final int STATS_TYPES = 10;
    /** How many mobs {@code unstack} brings back per tick, so a big job cannot stall the server. */
    private static final int UNSTACK_PER_TICK = 100;
    /** Above this many new mobs, {@code unstack} asks to be run again with {@code confirm}. */
    private static final int UNSTACK_CONFIRM_ABOVE = 500;
    /**
     * How many mobs may be set down on the stack's own spot when there is no room around it. Vanilla
     * starts hurting mobs past 24 in one place (maxEntityCramming), so this stays well under it and the
     * rest of that stack is left stacked instead.
     */
    private static final int UNSTACK_MAX_ON_THE_SPOT = 12;
    /** The largest radius {@code stack} takes; it compares every mob in it with its neighbours. */
    private static final int STACK_MAX_RADIUS = 128;

    /** Stacks {@code unstack} is still splitting, oldest job first. */
    private static final List<UnstackJob> unstackJobs = new ArrayList<>();

    private StackToolsCommands() {
    }

    /** Operators only: the root lets region owners in as well, for region share (see MobStackerCommands). */
    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("inspect").requires(StackToolsCommands::isAdmin)
                        .executes(ctx -> inspect(ctx.getSource(), lookedAt(ctx.getSource())))
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("stats").requires(StackToolsCommands::isAdmin)
                        .executes(ctx -> stats(ctx.getSource(), -1))
                        .then(argument("radius", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> stats(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(literal("unstack").requires(StackToolsCommands::isAdmin)
                        .then(literal("loaded")
                                .executes(ctx -> unstack(ctx.getSource(), -1, false))
                                .then(literal("confirm").executes(ctx -> unstack(ctx.getSource(), -1, true))))
                        .then(argument("radius", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> unstack(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), false))
                                .then(literal("confirm").executes(ctx -> unstack(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"), true)))))
                .then(literal("stack").requires(StackToolsCommands::isAdmin)
                        .then(argument("radius", IntegerArgumentType.integer(1, STACK_MAX_RADIUS))
                                .executes(ctx -> stack(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))));
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
        CommandFeedback.success(source, () -> Component.literal("=== " + kind + (size > 1 ? " x" + size : " (not stacked)") + " ===")
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
        CommandFeedback.success(source, () -> where, false);
        // What a region does NOT cover here and why, since the answer above cannot say: switched off,
        // or the spot is inside its area but in a biome its filter leaves out (1.12.0).
        String dimension = mob.level().dimension().location().toString();
        String biome = mob.level().getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("?");
        for (StackRegion other : MobStacker.config.getRegions()) {
            if (other == region) {
                continue;
            }
            if (!other.coversArea(dimension, pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            String why = !other.isEnabled() ? "is switched off"
                    : !other.contains(mob.level(), pos) ? "leaves out this biome (" + biome + ")"
                    : null;
            if (why != null) {
                String reason = why;
                CommandFeedback.success(source, () -> label("  Region ").append(value(other.getName()))
                        .append(label(" covers this spot but " + reason)), false);
            }
        }

        CommandFeedback.success(source, () -> label("Ceiling here: ").append(value(String.valueOf(MobStacker.getMaxMobStackSize(mob))))
                .append(label(" (" + ceilingSource(mob, region) + ")"))
                .append(label("   stackMode ")).append(value(MobStacker.config.getStackMode().name()))
                .append(label("   stackRadius ")).append(value(String.valueOf(MobStacker.getStackRadius(mob))))
                .append(label("   stackAi ")).append(value(MobStacker.getStackAi(mob).name())), false);
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
            CommandFeedback.success(source, () -> overrides, false);
        }

        MutableComponent health = label("Health: ").append(value(round(mob.getHealth()) + "/" + round(mob.getMaxHealth())));
        if (size > 1) {
            float memberHealth = mob.getMaxHealth() - MobStacker.getStackMemberDamage(mob);
            health.append(label(", each mob under it ")).append(value(round(memberHealth) + "/" + round(mob.getMaxHealth())));
        }
        CommandFeedback.success(source, () -> health, false);
        String recorded = MobStacker.originOf(mob);
        SpawnOrigin origin = SpawnOrigin.of(recorded);
        // Vanilla builds a golem without finalizeSpawn, so nothing is recorded for one; its own flag
        // still says who made it (round 12 read "not recorded" as a fault).
        boolean built = recorded == null && mob instanceof net.minecraft.world.entity.animal.IronGolem golem
                && golem.isPlayerCreated();
        CommandFeedback.success(source, () -> built
                ? label("Origin: ").append(value("built by a player"))
                        .append(label(" - a golem a player built never stacks"))
                : label("Origin: ").append(value(recorded == null ? "not recorded" : recorded))
                        .append(label(" - it " + (recorded == null ? "counts as having spawned on its own" : origin.description())
                                + "; " + origin.settingId() + " "
                                + (MobStacker.originStacks(mob, recorded) ? "lets it stack here" : "is off here"))), false);
        if (size > 1) {
            // Only worth a line when the members did not all come from where the top did.
            Map<String, Integer> members = MobStacker.memberOrigins(mob);
            String top = recorded == null ? MobStacker.NO_ORIGIN : recorded;
            if (!members.keySet().stream().allMatch(top::equals)) {
                MutableComponent under = label("Under it: ");
                boolean first = true;
                for (Map.Entry<String, Integer> entry : members.entrySet()) {
                    String memberOrigin = MobStacker.NO_ORIGIN.equals(entry.getKey()) ? null : entry.getKey();
                    if (!first) {
                        under.append(label(", "));
                    }
                    under.append(value(entry.getValue() + " " + (memberOrigin == null ? "not recorded" : memberOrigin)))
                            .append(label(MobStacker.originStacks(mob, memberOrigin) ? "" : " ("
                                    + SpawnOrigin.of(memberOrigin).settingId() + " is off here)"));
                    first = false;
                }
                CommandFeedback.success(source, () -> under, false);
            }
        }
        for (Component line : stackDataLines(mob)) {
            CommandFeedback.success(source, () -> line, false);
        }

        // Every rule that keeps it out, not only the first: the first alone hid the one a player was
        // looking for (round 12: a raider showed only that event spawns were off, not its raid).
        List<StackRefusal> refusals = ownRefusals(mob);
        CommandFeedback.success(source, () -> refusals.isEmpty()
                ? label("Stacking: ").append(Component.literal("yes").withStyle(ChatFormatting.GREEN))
                        .append(label(", it can join a stack or take mobs in"))
                : label("Stacking: ").append(Component.literal("no").withStyle(ChatFormatting.RED))
                        .append(label(" - " + refusals.get(0).reason())), false);
        for (StackRefusal also : refusals.subList(Math.min(1, refusals.size()), refusals.size())) {
            CommandFeedback.success(source, () -> label("  and " + also.reason()), false);
        }

        Component neighbour = neighbourLine(mob, kind);
        CommandFeedback.success(source, () -> neighbour, false);
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
    /** Every rule that keeps this mob out of stacking, its CanStack flag first. */
    private static List<StackRefusal> ownRefusals(Mob mob) {
        List<StackRefusal> all = new ArrayList<>();
        if (!MobStacker.getCanStack(mob)) {
            all.add(StackRefusal.CAN_STACK_OFF);
        }
        all.addAll(MobStacker.stackRefusals(mob));
        return all;
    }

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
        if (refusal == StackRefusal.DIFFERENT_DATA) {
            return line.append(no(refusal.reason() + ": '" + MobStacker.dataDifference(source, target) + "'"));
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
        CommandFeedback.success(source, () -> Component.literal("=== Stacks " + scope + " ===").withStyle(ChatFormatting.GOLD), false);
        long finalEntities = entities;
        long finalRepresented = represented;
        long finalStacks = stacks;
        CommandFeedback.success(source, () -> value(count(finalEntities)).append(label(" mob entities stand for "))
                .append(value(count(finalRepresented))).append(label(" mobs; "))
                .append(value(count(finalStacks))).append(label(finalStacks == 1 ? " stack saves " : " stacks save "))
                .append(Component.literal(count(saved)).withStyle(ChatFormatting.GREEN))
                .append(label(" entities.")), false);

        List<Map.Entry<EntityType<?>, long[]>> rows = new ArrayList<>(byType.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        for (int i = 0; i < rows.size() && i < STATS_TYPES; i++) {
            Map.Entry<EntityType<?>, long[]> row = rows.get(i);
            long[] numbers = row.getValue();
            CommandFeedback.success(source, () -> label(" " + BuiltInRegistries.ENTITY_TYPE.getKey(row.getKey()) + ": ")
                    .append(value(count(numbers[1]))).append(label(" mobs in "))
                    .append(value(count(numbers[0]))).append(label(numbers[0] == 1 ? " stack" : " stacks"))
                    .append(label(" (largest x" + numbers[2] + ")")), false);
        }
        if (rows.size() > STATS_TYPES) {
            CommandFeedback.success(source, () -> label(" ... and " + (rows.size() - STATS_TYPES) + " more kinds"), false);
        }
        return (int) Math.min(Integer.MAX_VALUE, stacks);
    }

    // ============================================================ unstack

    /**
     * One run of {@code unstack}: the stacks it found, split a few mobs a tick, and who to tell when it
     * is done.
     */
    private static final class UnstackJob {
        final CommandSourceStack source;
        final List<Mob> stacks;
        int next;
        int onTheSpot;
        int mobs;
        int split;
        int leftStacked;

        UnstackJob(CommandSourceStack source, List<Mob> stacks) {
            this.source = source;
            this.stacks = stacks;
        }
    }

    /**
     * Splits every stack in the loaded chunks (radius -1) or within {@code radius} blocks back into the
     * mobs it stands for.
     *
     * <p>The way out before the mod is removed, or when stacking is turned off somewhere: without the mod
     * a stack is one mob, and every mob it stood for is gone. Only loaded chunks can be reached - a stack
     * in a chunk nobody has loaded stays a stack. The mobs come back a hundred a tick, set down apart
     * from each other where there is room, because a stack of sixty-four let out on one spot would be
     * hurt by vanilla's crowding limit.
     */
    private static int unstack(CommandSourceStack source, int radius, boolean confirmed) {
        List<Mob> stacks = new ArrayList<>();
        long mobs = 0;
        Iterable<ServerLevel> levels = radius < 0 ? source.getServer().getAllLevels() : List.of(source.getLevel());
        Vec3 centre = source.getPosition();
        double reachSq = (double) radius * radius;
        for (ServerLevel level : levels) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && mob.isAlive() && MobStacker.getStackSize(mob) > 1
                        && (radius < 0 || entity.distanceToSqr(centre) <= reachSq)) {
                    stacks.add(mob);
                    mobs += MobStacker.getStackSize(mob) - 1;
                }
            }
        }
        String scope = radius < 0 ? "loaded" : String.valueOf(radius);
        if (stacks.isEmpty()) {
            CommandFeedback.success(source, () -> label("No stacks " + (radius < 0 ? "in the loaded chunks." : "within " + radius + " blocks.")), false);
            return 0;
        }
        long newMobs = mobs;
        if (newMobs > UNSTACK_CONFIRM_ABOVE && !confirmed) {
            CommandFeedback.success(source, () -> Component.literal("That would bring back " + count(newMobs) + " mobs from "
                            + count(stacks.size()) + " stacks. Run ").withStyle(ChatFormatting.YELLOW)
                    .append(value("/mobstacker unstack " + scope + " confirm"))
                    .append(Component.literal(" to go ahead.").withStyle(ChatFormatting.YELLOW)), false);
            return 0;
        }
        unstackJobs.add(new UnstackJob(source, stacks));
        CommandFeedback.success(source, () -> label("Splitting ").append(value(count(stacks.size())))
                .append(label(stacks.size() == 1 ? " stack back into " : " stacks back into ")).append(value(count(newMobs)))
                .append(label(" more mobs, " + UNSTACK_PER_TICK + " a tick...")), true);
        if (MobStacker.config.getStackMode() != com.frikinjay.mobstacker.config.StackMode.OFF) {
            CommandFeedback.success(source, () -> Component.literal("stackMode is " + MobStacker.config.getStackMode()
                    + ", so they will merge again wherever stacking is allowed. Before removing the mod, "
                    + "run /mobstacker set stackMode off first.").withStyle(ChatFormatting.GOLD), false);
        }
        if (radius < 0) {
            CommandFeedback.success(source, () -> label("Only loaded chunks are reached: a stack in a chunk nobody has "
                    + "loaded stays a stack."), false);
        }
        return stacks.size();
    }

    /** Called every server tick: splits up to {@link #UNSTACK_PER_TICK} mobs off the stacks waiting. */
    public static void tickUnstacking() {
        int budget = UNSTACK_PER_TICK;
        while (budget > 0 && !unstackJobs.isEmpty()) {
            UnstackJob job = unstackJobs.get(0);
            while (budget > 0 && job.next < job.stacks.size()) {
                Mob stack = job.stacks.get(job.next);
                if (stack.isRemoved() || !stack.isAlive() || MobStacker.getStackSize(stack) <= 1) {
                    finishStack(job);
                    continue;
                }
                Vec3 spot = placeApart(stack, job.onTheSpot >= UNSTACK_MAX_ON_THE_SPOT);
                if (spot == null) {
                    // No room left around it: what is still stacked stays stacked, rather than being
                    // let out onto a spot where the crowd would hurt it.
                    job.leftStacked++;
                    finishStack(job);
                    continue;
                }
                Mob separated = MobStacker.separateOne(stack, false);
                budget--;
                if (separated == null) {
                    job.leftStacked++;
                    finishStack(job);
                    continue;
                }
                separated.moveTo(spot.x, spot.y, spot.z, stack.getRandom().nextFloat() * 360.0F, 0.0F);
                if (spot.equals(stack.position())) {
                    job.onTheSpot++;
                }
                job.mobs++;
            }
            if (job.next >= job.stacks.size()) {
                unstackJobs.remove(0);
                int mobs = job.mobs;
                int split = job.split;
                int left = job.leftStacked;
                CommandFeedback.success(job.source, () -> Component.literal("Unstacked: ").withStyle(ChatFormatting.GREEN)
                        .append(value(count(mobs))).append(label(" mobs back from "))
                        .append(value(count(split))).append(label(split == 1 ? " stack." : " stacks."))
                        .append(left > 0 ? label(" " + count(left) + (left == 1 ? " was" : " were")
                                + " left partly stacked: no room around " + (left == 1 ? "it." : "them."))
                                : Component.empty()), true);
            }
        }
    }

    private static void finishStack(UnstackJob job) {
        job.split++;
        job.next++;
        job.onTheSpot = 0;
    }

    /** Forgets every job; the server is stopping. */
    public static void cancelUnstacking() {
        unstackJobs.clear();
    }

    /**
     * A spot near the stack to set one of its mobs down: free of blocks, reachable from the stack in a
     * straight line (so a mob in a pen is never put down on the far side of the fence), on the ground if
     * the stack is on the ground and in water if the stack is in water. Further out the bigger the stack.
     * The stack's own spot when nothing else fits and {@code spotFull} is false; null when it is full too.
     */
    private static Vec3 placeApart(Mob stack, boolean spotFull) {
        Level level = stack.level();
        Vec3 base = stack.position();
        EntityType<?> type = stack.getType();
        double width = Math.max(0.6, stack.getBbWidth());
        double spread = Math.min(8.0, Math.max(1.5, width * Math.sqrt(MobStacker.getStackSize(stack)) * 0.6));
        boolean grounded = !level.noCollision(Compat.spawnBox(type, base.x, base.y - 0.5, base.z));
        boolean wet = !level.getFluidState(stack.blockPosition()).isEmpty();
        Vec3 from = base.add(0.0, Math.min(0.5, stack.getBbHeight() * 0.5), 0.0);
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = stack.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(stack.getRandom().nextDouble()) * spread;
            Vec3 spot = new Vec3(base.x + Math.cos(angle) * distance, base.y, base.z + Math.sin(angle) * distance);
            if (!level.noCollision(Compat.spawnBox(type, spot.x, spot.y, spot.z))) {
                continue;
            }
            if (grounded && level.noCollision(Compat.spawnBox(type, spot.x, spot.y - 0.5, spot.z))) {
                continue; // nothing to stand on: an edge, a hole, a ledge
            }
            if (wet != !level.getFluidState(BlockPos.containing(spot)).isEmpty()) {
                continue;
            }
            Vec3 to = spot.add(0.0, from.y - base.y, 0.0);
            if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, stack))
                    .getType() != HitResult.Type.MISS) {
                continue; // a wall or a fence between them
            }
            return spot;
        }
        return spotFull ? null : base;
    }

    // ============================================================ stack

    /**
     * Merges the mobs within {@code radius} blocks that could merge with each other by every rule but the
     * one about the place: stackMode and the regions are not asked, so an operator can tidy up a crowd
     * where the scan is not allowed to. Mobs still only merge with their own kind within the stack radius,
     * under the ceiling, and never when a name, gear, a variant or a player's claim keeps them apart. The
     * stacks stay stacks afterwards, like any stack that wanders out of a stacking area.
     */
    private static int stack(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        Vec3 centre = source.getPosition();
        AABB area = new AABB(centre, centre).inflate(radius);
        double reachSq = (double) radius * radius;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area,
                mob -> mob.isAlive() && mob.distanceToSqr(centre) <= reachSq);
        // The biggest stacks take the others in, the same way round as the scan's merges.
        mobs.sort((a, b) -> Integer.compare(MobStacker.getStackSize(b), MobStacker.getStackSize(a)));
        int merged = 0;
        List<Mob> grew = new ArrayList<>();
        for (Mob target : mobs) {
            if (target.isRemoved() || !freeToStack(target)) {
                continue;
            }
            for (Mob candidate : level.getEntitiesOfClass(Mob.class,
                    target.getBoundingBox().inflate(MobStacker.getStackRadius(target)),
                    other -> other != target && other.getClass() == target.getClass() && other.isAlive()
                            && other.distanceToSqr(centre) <= reachSq)) {
                if (candidate.isRemoved() || !freeToStack(candidate)
                        || MobStacker.mergeRefusal(candidate, target) != null) {
                    continue;
                }
                MobStacker.mergeEntities(target, candidate);
                merged++;
                if (!grew.contains(target)) {
                    grew.add(target);
                }
                if (!freeToStack(target)) {
                    break; // full
                }
            }
        }
        int mergedMobs = merged;
        int stacks = grew.size();
        CommandFeedback.success(source, () -> mergedMobs == 0
                ? label("Nothing within " + radius + " blocks could merge. /mobstacker inspect shows why for any one mob.")
                : Component.literal("Stacked: ").withStyle(ChatFormatting.GREEN)
                        .append(value(count(mergedMobs))).append(label(mergedMobs == 1 ? " mob merged into " : " mobs merged into "))
                        .append(value(count(stacks))).append(label(stacks == 1 ? " stack." : " stacks.")), true);
        return mergedMobs;
    }

    /** Whether this mob may take part in {@code stack}: every rule but where it stands. */
    private static boolean freeToStack(Mob mob) {
        return MobStacker.getCanStack(mob) && MobStacker.stackRefusal(mob, false) == null;
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
