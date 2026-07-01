package com.frikinjay.mobstacker.command;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import com.frikinjay.mobstacker.config.StackRegion;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.frikinjay.mobstacker.MobStacker.MOD_ID;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mobstacker} command tree.
 * <p>
 * Scalar settings are not hand-wired here: {@code set}/{@code get}/{@code toggle}/{@code reset}
 * are generic and driven by the {@link MobStackerSettings} registry, so the whole config surface
 * is exposed by a handful of handlers. Collection-shaped state (regions, ignore lists) and the
 * per-entity live {@code stacksize} action keep dedicated subcommands.
 */
public class MobStackerCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(MOD_ID)
                .requires(source -> source.hasPermission(2))
                .executes(MobStackerCommands::showOverview)
                .then(literal("help")
                        .executes(MobStackerCommands::showHelpRoot)
                        .then(argument("category", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestCategories)
                                .executes(MobStackerCommands::showHelpCategory)))
                .then(literal("reload").executes(MobStackerCommands::reloadConfig))
                .then(literal("get")
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestSettings)
                                .executes(MobStackerCommands::getValue)))
                .then(literal("set")
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestSettings)
                                .then(argument("value", StringArgumentType.greedyString())
                                        .suggests(MobStackerCommands::suggestValues)
                                        .executes(MobStackerCommands::setValue))))
                .then(literal("toggle")
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestToggles)
                                .executes(MobStackerCommands::toggleValue)))
                .then(literal("reset")
                        .then(literal("all").executes(MobStackerCommands::resetAll))
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestSettings)
                                .executes(MobStackerCommands::resetValue)))
                .then(literal("stacksize")
                        .then(argument("target", EntityArgument.entity())
                                .then(argument("size", IntegerArgumentType.integer(1))
                                        .executes(MobStackerCommands::setStackSizeLive))))
                .then(literal("ignore")
                        .then(literal("entity")
                                .then(literal("add")
                                        .then(argument("entityId", ResourceLocationArgument.id())
                                                .suggests(MobStackerCommands::suggestEntities)
                                                .executes(MobStackerCommands::ignoreEntity)))
                                .then(literal("remove")
                                        .then(argument("entityId", ResourceLocationArgument.id())
                                                .suggests(MobStackerCommands::suggestIgnoredEntities)
                                                .executes(MobStackerCommands::unignoreEntity)))
                                .then(literal("list").executes(MobStackerCommands::listIgnoredEntities)))
                        .then(literal("mod")
                                .then(literal("add")
                                        .then(argument("modId", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestMods)
                                                .executes(MobStackerCommands::ignoreMod)))
                                .then(literal("remove")
                                        .then(argument("modId", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestIgnoredMods)
                                                .executes(MobStackerCommands::unignoreMod)))
                                .then(literal("list").executes(MobStackerCommands::listIgnoredMods))))
                .then(literal("region")
                        .then(literal("add")
                                .then(argument("name", StringArgumentType.word())
                                        .then(literal("allow")
                                                .then(argument("corner1", BlockPosArgument.blockPos())
                                                        .then(argument("corner2", BlockPosArgument.blockPos())
                                                                .executes(ctx -> addRegion(ctx, StackRegion.Type.ALLOW)))))
                                        .then(literal("deny")
                                                .then(argument("corner1", BlockPosArgument.blockPos())
                                                        .then(argument("corner2", BlockPosArgument.blockPos())
                                                                .executes(ctx -> addRegion(ctx, StackRegion.Type.DENY)))))))
                        .then(literal("remove")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(MobStackerCommands::removeRegion)))
                        .then(literal("list")
                                .executes(MobStackerCommands::listRegions))));
    }

    // ============================================================ generic settings

    private static int getValue(CommandContext<CommandSourceStack> context) {
        ConfigOption option = MobStackerSettings.byId(StringArgumentType.getString(context, "setting"));
        if (option == null) {
            return unknownSetting(context, StringArgumentType.getString(context, "setting"));
        }
        context.getSource().sendSuccess(() -> Component.literal(option.id()).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                .append(valueComponent(option))
                .append(Component.literal("   [default " + option.defaultValue() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
        context.getSource().sendSuccess(() -> Component.literal(option.description()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int setValue(CommandContext<CommandSourceStack> context) {
        String settingId = StringArgumentType.getString(context, "setting");
        ConfigOption option = MobStackerSettings.byId(settingId);
        if (option == null) {
            return unknownSetting(context, settingId);
        }
        return report(context.getSource(), option, option.apply(StringArgumentType.getString(context, "value")));
    }

    private static int toggleValue(CommandContext<CommandSourceStack> context) {
        String settingId = StringArgumentType.getString(context, "setting");
        ConfigOption option = MobStackerSettings.byId(settingId);
        if (option == null) {
            return unknownSetting(context, settingId);
        }
        return report(context.getSource(), option, option.toggle());
    }

    private static int resetValue(CommandContext<CommandSourceStack> context) {
        String settingId = StringArgumentType.getString(context, "setting");
        ConfigOption option = MobStackerSettings.byId(settingId);
        if (option == null) {
            return unknownSetting(context, settingId);
        }
        return report(context.getSource(), option, option.reset());
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        int changed = 0;
        java.util.List<ConfigOption> retry = new java.util.ArrayList<>();
        // First pass; some options may be blocked by a dependency (e.g. killWholeStackOnDeath while
        // stackHealth is still on), so any errors are retried once after the rest have reset.
        for (ConfigOption option : MobStackerSettings.all()) {
            ConfigOption.Result result = option.reset();
            if (result.status == ConfigOption.Result.Status.CHANGED) {
                changed++;
            } else if (result.status == ConfigOption.Result.Status.ERROR) {
                retry.add(option);
            }
        }
        for (ConfigOption option : retry) {
            if (option.reset().status == ConfigOption.Result.Status.CHANGED) {
                changed++;
            }
        }
        final int count = changed;
        context.getSource().sendSuccess(() -> Component.literal("Reset " + count + " setting(s) to their defaults").withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /** Turns a {@link ConfigOption.Result} into consistent, colour-coded feedback. */
    private static int report(CommandSourceStack source, ConfigOption option, ConfigOption.Result result) {
        switch (result.status) {
            case CHANGED -> {
                MutableComponent message = Component.literal("Set ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(option.id()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(result.oldValue).withStyle(ChatFormatting.RED))
                        .append(Component.literal(" -> ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(result.newValue).withStyle(ChatFormatting.GREEN));
                if (result.message != null) {
                    message.append(Component.literal("   (" + result.message + ")").withStyle(ChatFormatting.YELLOW));
                }
                source.sendSuccess(() -> message, true);
                return 1;
            }
            case UNCHANGED -> {
                source.sendSuccess(() -> Component.literal(option.id() + " is already " + result.oldValue).withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }
            default -> {
                source.sendFailure(Component.literal(result.message).withStyle(ChatFormatting.RED));
                return 0;
            }
        }
    }

    private static int unknownSetting(CommandContext<CommandSourceStack> context, String settingId) {
        context.getSource().sendFailure(Component.literal("Unknown setting '" + settingId + "'. Try /mobstacker help").withStyle(ChatFormatting.RED));
        return 0;
    }

    private static Component valueComponent(ConfigOption option) {
        String value = option.currentValue();
        ChatFormatting color = ChatFormatting.AQUA;
        if (option.type() == ConfigOption.Type.BOOL) {
            color = Boolean.parseBoolean(value) ? ChatFormatting.GREEN : ChatFormatting.RED;
        }
        return Component.literal(value).withStyle(color);
    }

    // ============================================================ overview & help

    private static int showOverview(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("=== MobStacker: Restacked ===").withStyle(ChatFormatting.GOLD), false);
        for (Category category : Category.values()) {
            List<ConfigOption> options = MobStackerSettings.byCategory(category);
            if (options.isEmpty()) {
                continue;
            }
            MutableComponent line = Component.literal("[" + category.display() + "] ").withStyle(ChatFormatting.GOLD);
            boolean first = true;
            for (ConfigOption option : options) {
                if (!first) {
                    line.append(Component.literal("  ").withStyle(ChatFormatting.GRAY));
                }
                first = false;
                line.append(Component.literal(option.id() + ":").withStyle(ChatFormatting.GRAY))
                        .append(valueComponent(option));
            }
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> Component.literal("Regions: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(MobStacker.config.getRegions().size())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("   Ignored entities: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(MobStacker.config.getIgnoredEntities().size())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("   Ignored mods: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(MobStacker.config.getIgnoredMods().size())).withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal("Commands: set, get, toggle, reset, stacksize, ignore, region, reload  -  ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mobstacker help").withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int showHelpRoot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("=== MobStacker: Restacked - help ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/mobstacker set <setting> <value>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  change a setting").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/mobstacker get|toggle|reset <setting>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  inspect / flip / restore").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/mobstacker reset all").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  restore every setting to default").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/mobstacker stacksize <target> <n>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  force a targeted mob's live stack count").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/mobstacker ignore <entity|mod> <add|remove|list>").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("/mobstacker region <add|remove|list>").withStyle(ChatFormatting.YELLOW), false);

        MutableComponent categories = Component.literal("Categories (").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mobstacker help <category>").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("): ").withStyle(ChatFormatting.GRAY));
        boolean first = true;
        for (Category category : Category.values()) {
            int count = MobStackerSettings.byCategory(category).size();
            if (count == 0) {
                continue;
            }
            if (!first) {
                categories.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            first = false;
            categories.append(Component.literal(category.name().toLowerCase()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" (" + count + ")").withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> categories, false);
        return 1;
    }

    private static int showHelpCategory(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "category");
        Category category = null;
        for (Category candidate : Category.values()) {
            if (candidate.name().equalsIgnoreCase(key) || candidate.display().equalsIgnoreCase(key)) {
                category = candidate;
                break;
            }
        }
        if (category == null) {
            context.getSource().sendFailure(Component.literal("Unknown category '" + key + "'").withStyle(ChatFormatting.RED));
            return 0;
        }
        final Category resolved = category;
        context.getSource().sendSuccess(() -> Component.literal("[" + resolved.display() + "]").withStyle(ChatFormatting.GOLD), false);
        for (ConfigOption option : MobStackerSettings.byCategory(category)) {
            context.getSource().sendSuccess(() -> Component.literal(option.id()).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                    .append(valueComponent(option))
                    .append(Component.literal("   [default " + option.defaultValue() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
            context.getSource().sendSuccess(() -> Component.literal("  " + option.description()).withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    // ============================================================ suggestions

    private static CompletableFuture<Suggestions> suggestSettings(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String id : MobStackerSettings.ids()) {
            if (id.toLowerCase().startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestToggles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (ConfigOption option : MobStackerSettings.all()) {
            if (option.type() == ConfigOption.Type.BOOL && option.id().toLowerCase().startsWith(remaining)) {
                builder.suggest(option.id());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestValues(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ConfigOption option = MobStackerSettings.byId(StringArgumentType.getString(context, "setting"));
        if (option == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining().toLowerCase();
        if (option.type() == ConfigOption.Type.ITEM) {
            for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
                String value = itemId.toString();
                if (value.toLowerCase().startsWith(remaining)) {
                    builder.suggest(value);
                }
            }
        } else {
            for (String value : option.valueSuggestions()) {
                if (value.toLowerCase().startsWith(remaining)) {
                    builder.suggest(value);
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (Category category : Category.values()) {
            if (category.name().toLowerCase().startsWith(remaining)) {
                builder.suggest(category.name().toLowerCase());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestEntities(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        BuiltInRegistries.ENTITY_TYPE.forEach(entityType -> {
            if (entityType.create(context.getSource().getLevel()) instanceof Mob) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                if (id.toString().toLowerCase().startsWith(remaining)) {
                    builder.suggest(id.toString());
                }
            }
        });
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        Set<String> modsWithMobs = new HashSet<>();
        BuiltInRegistries.ENTITY_TYPE.forEach(entityType -> {
            if (entityType.create(context.getSource().getLevel()) instanceof Mob) {
                modsWithMobs.add(BuiltInRegistries.ENTITY_TYPE.getKey(entityType).getNamespace());
            }
        });
        modsWithMobs.stream()
                .filter(modId -> modId.toLowerCase().startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestIgnoredEntities(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        MobStacker.config.getIgnoredEntities().stream()
                .filter(entity -> entity.startsWith(builder.getRemaining()))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        MobStacker.config.getIgnoredMods().stream()
                .filter(mod -> mod.startsWith(builder.getRemaining()))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRegions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        MobStacker.config.getRegions().stream()
                .map(StackRegion::getName)
                .filter(regionName -> regionName.toLowerCase().startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    // ============================================================ ignore lists

    private static int ignoreEntity(CommandContext<CommandSourceStack> context) {
        ResourceLocation entityId = ResourceLocationArgument.getId(context, "entityId");
        String entityIdString = entityId.toString();
        if (MobStacker.config.getIgnoredEntities().contains(entityIdString)) {
            context.getSource().sendSuccess(() -> Component.literal("Entity '" + entityIdString + "' is already ignored").withStyle(ChatFormatting.YELLOW), false);
        } else {
            MobStacker.config.addIgnoredEntity(entityIdString);
            context.getSource().sendSuccess(() -> Component.literal("Added '" + entityIdString + "' to ignored entities").withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    private static int ignoreMod(CommandContext<CommandSourceStack> context) {
        String modId = StringArgumentType.getString(context, "modId");
        if (MobStacker.config.getIgnoredMods().contains(modId)) {
            context.getSource().sendSuccess(() -> Component.literal("Mod '" + modId + "' is already ignored").withStyle(ChatFormatting.YELLOW), false);
        } else {
            MobStacker.config.addIgnoredMod(modId);
            context.getSource().sendSuccess(() -> Component.literal("Added '" + modId + "' to ignored mods").withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    private static int unignoreEntity(CommandContext<CommandSourceStack> context) {
        ResourceLocation entityId = ResourceLocationArgument.getId(context, "entityId");
        String entityIdString = entityId.toString();
        if (!MobStacker.config.getIgnoredEntities().contains(entityIdString)) {
            context.getSource().sendSuccess(() -> Component.literal("Entity '" + entityIdString + "' is not in the ignored list").withStyle(ChatFormatting.YELLOW), false);
        } else {
            MobStacker.config.removeIgnoredEntity(entityIdString);
            context.getSource().sendSuccess(() -> Component.literal("Removed '" + entityIdString + "' from ignored entities").withStyle(ChatFormatting.GOLD), true);
        }
        return 1;
    }

    private static int unignoreMod(CommandContext<CommandSourceStack> context) {
        String modId = StringArgumentType.getString(context, "modId");
        if (!MobStacker.config.getIgnoredMods().contains(modId)) {
            context.getSource().sendSuccess(() -> Component.literal("Mod '" + modId + "' is not in the ignored list").withStyle(ChatFormatting.YELLOW), false);
        } else {
            MobStacker.config.removeIgnoredMod(modId);
            context.getSource().sendSuccess(() -> Component.literal("Removed '" + modId + "' from ignored mods").withStyle(ChatFormatting.GOLD), true);
        }
        return 1;
    }

    private static int listIgnoredEntities(CommandContext<CommandSourceStack> context) {
        List<String> ignored = MobStacker.config.getIgnoredEntities();
        if (ignored.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No ignored entities").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("Ignored entities (" + ignored.size() + "):").withStyle(ChatFormatting.AQUA), false);
        for (String entity : ignored) {
            context.getSource().sendSuccess(() -> Component.literal(" - " + entity).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int listIgnoredMods(CommandContext<CommandSourceStack> context) {
        List<String> ignored = MobStacker.config.getIgnoredMods();
        if (ignored.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No ignored mods").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("Ignored mods (" + ignored.size() + "):").withStyle(ChatFormatting.AQUA), false);
        for (String mod : ignored) {
            context.getSource().sendSuccess(() -> Component.literal(" - " + mod).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    // ============================================================ live per-entity stack size

    private static int setStackSizeLive(CommandContext<CommandSourceStack> context) {
        try {
            Entity targetEntity = EntityArgument.getEntity(context, "target");
            int newSize = IntegerArgumentType.getInteger(context, "size");

            if (!(targetEntity instanceof Mob mob)) {
                context.getSource().sendFailure(Component.literal("Target is not a stackable mob").withStyle(ChatFormatting.RED));
                return 0;
            }

            int currentSize = MobStacker.getStackSize(mob);
            if (currentSize == newSize) {
                context.getSource().sendSuccess(() -> Component.literal("That mob's stack size is already " + newSize).withStyle(ChatFormatting.YELLOW), false);
            } else {
                MobStacker.setStackSize(mob, newSize);
                context.getSource().sendSuccess(() -> Component.literal("Set the targeted mob's stack size to " + newSize).withStyle(ChatFormatting.GREEN), true);
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error setting stack size: " + e.getMessage()).withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    // ============================================================ reload

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        MobStacker.reloadConfig();
        context.getSource().sendSuccess(() -> Component.literal("MobStacker config reloaded from disk").withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    // ============================================================ regions

    private static int addRegion(CommandContext<CommandSourceStack> context, StackRegion.Type type) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (MobStacker.config.getRegion(name) != null) {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' already exists. Remove it first.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos corner1 = BlockPosArgument.getBlockPos(context, "corner1");
        BlockPos corner2 = BlockPosArgument.getBlockPos(context, "corner2");
        String dimension = context.getSource().getLevel().dimension().location().toString();

        StackRegion region = new StackRegion(name, dimension, type,
                corner1.getX(), corner1.getY(), corner1.getZ(),
                corner2.getX(), corner2.getY(), corner2.getZ());
        MobStacker.config.addRegion(region);

        context.getSource().sendSuccess(() -> Component.literal(
                "Added " + type + " region '" + name + "' in " + dimension + " " + region.describeBounds()
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeRegion(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (MobStacker.config.removeRegion(name)) {
            context.getSource().sendSuccess(() -> Component.literal("Removed region '" + name + "'").withStyle(ChatFormatting.GOLD), true);
        } else {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' does not exist").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int listRegions(CommandContext<CommandSourceStack> context) {
        List<StackRegion> regions = MobStacker.config.getRegions();
        String mode = String.valueOf(MobStacker.config.getStackMode());

        if (regions.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No regions defined (current mode: " + mode + ")").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("Stacking regions (mode: " + mode + "):").withStyle(ChatFormatting.AQUA), false);
        for (StackRegion region : regions) {
            ChatFormatting color = region.isDeny() ? ChatFormatting.RED : ChatFormatting.GREEN;
            context.getSource().sendSuccess(() -> Component.literal(
                    " - " + region.getName() + " [" + region.getType() + "] " + region.getDimension() + " " + region.describeBounds()
            ).withStyle(color), false);
        }
        return 1;
    }
}
