package com.frikinjay.mobstacker.command;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import com.frikinjay.mobstacker.config.ConfigSelfTest;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import com.frikinjay.mobstacker.config.StackMode;
import com.frikinjay.mobstacker.config.RegionEdit;
import com.frikinjay.mobstacker.config.StackColor;
import com.frikinjay.mobstacker.config.StackRegion;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import java.util.Map;
import java.util.Set;
import java.util.Locale;
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
        LiteralArgumentBuilder<CommandSourceStack> root = literal(MOD_ID)
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
                        .then(literal("bounds")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("corner1", BlockPosArgument.blockPos())
                                                .then(argument("corner2", BlockPosArgument.blockPos())
                                                        .executes(MobStackerCommands::setRegionBounds)))))
                        .then(literal("type")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(literal("allow")
                                                .executes(ctx -> setRegionType(ctx, StackRegion.Type.ALLOW)))
                                        .then(literal("deny")
                                                .executes(ctx -> setRegionType(ctx, StackRegion.Type.DENY)))))
                        .then(literal("remove")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(MobStackerCommands::removeRegion)))
                        .then(literal("list")
                                .executes(MobStackerCommands::listRegions))
                        .then(literal("show")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(MobStackerCommands::showRegion)))
                        .then(literal("set")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("setting", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestRegionSettings)
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .suggests(MobStackerCommands::suggestValues)
                                                        .executes(MobStackerCommands::setRegionValue)))))
                        .then(literal("unset")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("setting", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestRegionOverrides)
                                                .executes(MobStackerCommands::unsetRegionValue))))
                        .then(literal("priority")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("priority", IntegerArgumentType.integer())
                                                .executes(MobStackerCommands::setRegionPriority))))
                        .then(literal("rename")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("newname", StringArgumentType.word())
                                                .executes(MobStackerCommands::renameRegion))))
                        .then(literal("color")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("color", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestRegionColors)
                                                .executes(MobStackerCommands::setRegionColor)))));

        // The self-test command is an opt-in developer/testing tool: it is only registered when the
        // JVM is started with -Dmobstacker.selftest=true, so the public release never exposes it.
        if (Boolean.getBoolean("mobstacker.selftest")) {
            root.then(literal("selftest").executes(MobStackerCommands::runSelfTest));
        }
        dispatcher.register(root);
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
        // Say so when the value above is not the one in the config file: another setting is forcing
        // it, or the setting it depends on is off and holding it at its default.
        String problem = MobStackerSettings.lockProblem(option, null, null);
        if (problem == null) {
            problem = MobStackerSettings.dependencyProblem(option, null, null);
        }
        if (problem != null) {
            String stored = option.storedValue();
            final String note = option.currentValue().equalsIgnoreCase(stored)
                    ? problem
                    : problem + "  (stored: " + stored + ")";
            context.getSource().sendSuccess(() -> Component.literal(note).withStyle(ChatFormatting.YELLOW), false);
        }
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
        if (changed > 0) {
            MobStacker.config.save();
        }
        final int count = changed;
        context.getSource().sendSuccess(() -> Component.literal("Reset " + count + " setting(s) to their defaults").withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /** Turns a {@link ConfigOption.Result} into consistent, colour-coded feedback. */
    private static int report(CommandSourceStack source, ConfigOption option, ConfigOption.Result result) {
        switch (result.status) {
            case CHANGED -> {
                // Persist the change so a command edit survives a restart, matching the GUI path.
                MobStacker.config.save();
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
        warnIfStacksNowhere(source);
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
        source.sendSuccess(() -> Component.literal("/mobstacker region <add|bounds|type|color|rename|remove|list|show|set|unset|priority>").withStyle(ChatFormatting.YELLOW), false);

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
            MobStacker.config.save();
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
            MobStacker.config.save();
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
            MobStacker.config.save();
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
            MobStacker.config.save();
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

    /**
     * Runs the automated settings self-test against a sandbox config (the live per-world config is
     * untouched) and reports pass/fail counts, listing any failures. Handy for verifying that every
     * setting still parses/validates/round-trips after changes to the registry.
     */
    private static int runSelfTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ConfigSelfTest.Report report = ConfigSelfTest.run();
        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(
                    "Self-test PASSED: " + report.checks + " checks across " + report.options + " settings").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal(
                "Self-test FAILED: " + report.failures + "/" + report.checks + " checks across " + report.options + " settings").withStyle(ChatFormatting.RED));
        int shown = 0;
        for (String message : report.messages) {
            if (shown++ >= 20) {
                int remaining = report.messages.size() - 20;
                source.sendFailure(Component.literal(" ... and " + remaining + " more").withStyle(ChatFormatting.RED));
                break;
            }
            source.sendFailure(Component.literal(" - " + message).withStyle(ChatFormatting.RED));
        }
        return 0;
    }

    // ============================================================ regions

    /**
     * True when {@code stackMode} is REGIONS but no ALLOW region exists — a configuration in which
     * nothing can ever stack (a common source of confusion). DENY regions do not help here.
     */
    private static boolean stacksNowhere() {
        return MobStacker.config.getStackMode() == StackMode.REGIONS
                && MobStacker.config.getRegions().stream().noneMatch(StackRegion::isAllow);
    }

    /** Emits a yellow heads-up when the current config would stack nowhere (see {@link #stacksNowhere()}). */
    private static void warnIfStacksNowhere(CommandSourceStack source) {
        if (stacksNowhere()) {
            source.sendSuccess(() -> Component.literal(
                    "Note: stackMode is REGIONS but no ALLOW region is defined - nothing will stack anywhere. "
                            + "Add one with /mobstacker region add <name> allow <c1> <c2>, or /mobstacker set stackMode everywhere."
            ).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static int addRegion(CommandContext<CommandSourceStack> context, StackRegion.Type type) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (MobStacker.config.getRegion(name) != null) {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' already exists. "
                    + "Use /mobstacker region bounds " + name + " <corner> <corner> to move it, "
                    + "which keeps its settings.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos corner1 = BlockPosArgument.getBlockPos(context, "corner1");
        BlockPos corner2 = BlockPosArgument.getBlockPos(context, "corner2");
        String dimension = context.getSource().getLevel().dimension().location().toString();

        return reportRegionEdit(context, RegionEdit.apply(name, type, dimension,
                corner1.getX(), corner1.getY(), corner1.getZ(),
                corner2.getX(), corner2.getY(), corner2.getZ()));
    }

    /**
     * Moves or resizes an existing region. The area is the only thing that changes: the settings it
     * carries, its priority and its name all stay, which is what deleting and re-adding it lost.
     */
    private static int setRegionBounds(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        StackRegion region = MobStacker.config.getRegion(name);
        if (region == null) {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' does not exist").withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos corner1 = BlockPosArgument.getBlockPos(context, "corner1");
        BlockPos corner2 = BlockPosArgument.getBlockPos(context, "corner2");
        // The corners are read in the dimension the command is run from, so the region follows.
        String dimension = context.getSource().getLevel().dimension().location().toString();
        boolean moved = !dimension.equals(region.getDimension());

        int result = reportRegionEdit(context, RegionEdit.apply(name, region.getType(), dimension,
                corner1.getX(), corner1.getY(), corner1.getZ(),
                corner2.getX(), corner2.getY(), corner2.getZ()));
        if (result == 1 && moved) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "The corners were read here, so the region moved to " + dimension + "."
            ).withStyle(ChatFormatting.AQUA), true);
        }
        return result;
    }

    /** Turns an allow region into a deny one, or back, without redrawing it. */
    private static int setRegionType(CommandContext<CommandSourceStack> context, StackRegion.Type type) {
        String name = StringArgumentType.getString(context, "name");
        StackRegion region = MobStacker.config.getRegion(name);
        if (region == null) {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' does not exist").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (region.getType() == type) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Region '" + name + "' is already " + type).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        RegionEdit.Result result = RegionEdit.apply(name, type, region.getDimension(),
                region.getMinX(), region.getMinY(), region.getMinZ(),
                region.getMaxX(), region.getMaxY(), region.getMaxZ());
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal(result.message()).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Region '" + name + "' is now " + type).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Turns a {@link RegionEdit} outcome into the usual green/red command feedback. */
    private static int reportRegionEdit(CommandContext<CommandSourceStack> context, RegionEdit.Result result) {
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal(result.message()).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** The "priority 5, 3 settings" tail shown after a region's bounds, omitted when there is none. */
    private static String describeRegionExtras(StackRegion region) {
        StringBuilder out = new StringBuilder();
        if (region.getPriority() != 0) {
            out.append("  priority ").append(region.getPriority());
        }
        int overrides = region.getSettings().size();
        if (overrides > 0) {
            out.append("  ").append(overrides).append(overrides == 1 ? " setting" : " settings");
        }
        return out.toString();
    }

    private static int removeRegion(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (MobStacker.config.removeRegion(name)) {
            MobStacker.config.save();
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
            warnIfStacksNowhere(context.getSource());
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("Stacking regions (mode: " + mode + "):").withStyle(ChatFormatting.AQUA), false);
        for (StackRegion region : regions) {
            ChatFormatting color = region.isDeny() ? ChatFormatting.RED : ChatFormatting.GREEN;
            context.getSource().sendSuccess(() -> Component.literal(
                    " - " + region.getName() + " [" + region.getType() + "] " + region.getDimension() + " "
                            + region.describeBounds() + describeRegionExtras(region)
            ).withStyle(color), false);
        }
        warnIfStacksNowhere(context.getSource());
        return 1;
    }

    // --- per-region settings -------------------------------------------------------------------

    /** Looks up the region named by the command, reporting to the source when there is none. */
    private static StackRegion requireRegion(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        StackRegion region = MobStacker.config.getRegion(name);
        if (region == null) {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' does not exist")
                    .withStyle(ChatFormatting.RED));
        }
        return region;
    }

    private static int setRegionValue(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        String settingId = StringArgumentType.getString(context, "setting");
        ConfigOption option = MobStackerSettings.byId(settingId);
        if (option == null) {
            return unknownSetting(context, settingId);
        }
        if (!MobStackerSettings.isRegionOverridable(option.id())) {
            context.getSource().sendFailure(Component.literal(
                    "'" + option.id() + "' is global and cannot differ per region. Set it with /mobstacker set "
                            + option.id() + " <value>.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String canonical;
        try {
            canonical = option.canonicalize(StringArgumentType.getString(context, "value"));
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }

        // Dependencies are judged by what is in force inside this region, so a region that enables
        // sweepingEdgeOverflow for itself may use the options built on it.
        String problem = MobStackerSettings.regionEditProblem(option, region, canonical);
        if (problem != null) {
            context.getSource().sendFailure(Component.literal(problem).withStyle(ChatFormatting.RED));
            return 0;
        }

        String previous = region.getSetting(option.id());
        if (canonical.equals(previous)) {
            context.getSource().sendSuccess(() -> Component.literal(
                    region.getName() + ": " + option.id() + " is already " + canonical).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        region.setSetting(option.id(), canonical);
        MobStacker.config.save();
        String from = previous != null ? previous : option.currentValue() + " (global)";
        context.getSource().sendSuccess(() -> Component.literal(
                region.getName() + ": " + option.id() + " " + from + " -> " + canonical).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int unsetRegionValue(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        String settingId = StringArgumentType.getString(context, "setting");
        ConfigOption option = MobStackerSettings.byId(settingId);
        String id = option != null ? option.id() : settingId;
        if (!region.clearSetting(id)) {
            context.getSource().sendSuccess(() -> Component.literal(
                    region.getName() + " does not override " + id).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        MobStacker.config.save();
        String now = option != null ? option.currentValue() : "the global value";
        context.getSource().sendSuccess(() -> Component.literal(
                region.getName() + ": " + id + " follows the global config again (" + now + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setRegionPriority(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        int priority = IntegerArgumentType.getInteger(context, "priority");
        int previous = region.getPriority();
        if (priority == previous) {
            context.getSource().sendSuccess(() -> Component.literal(
                    region.getName() + ": priority is already " + priority).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        region.setPriority(priority);
        MobStacker.config.save();
        context.getSource().sendSuccess(() -> Component.literal(
                region.getName() + ": priority " + previous + " -> " + priority).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * The sixteen colours plus "auto", which clears the choice and lets the region fall back to what
     * its kind means (green for allow, red for deny).
     */
    /** What {@code /mobstacker region color <name> auto} is spelled as. */
    private static final String AUTO_COLOR = "auto";

    private static CompletableFuture<Suggestions> suggestRegionColors(CommandContext<CommandSourceStack> context,
                                                                      SuggestionsBuilder builder) {
        builder.suggest(AUTO_COLOR);
        for (StackColor color : StackColor.values()) {
            builder.suggest(color.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }

    private static int renameRegion(CommandContext<CommandSourceStack> context) {
        String from = StringArgumentType.getString(context, "name");
        String to = StringArgumentType.getString(context, "newname");
        return reportRegionEdit(context, RegionEdit.rename(from, to));
    }

    private static int setRegionColor(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(context, "color").trim();
        StackColor chosen;
        if (AUTO_COLOR.equalsIgnoreCase(raw)) {
            chosen = null;
        } else {
            try {
                chosen = StackColor.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                context.getSource().sendFailure(Component.literal(
                        "Unknown colour '" + raw + "'. Use " + AUTO_COLOR + " or one of the sixteen chat colours."));
                return 0;
            }
        }
        region.setColor(chosen);
        MobStacker.config.save();
        StackColor shown = region.effectiveColor();
        String label = chosen == null
                ? AUTO_COLOR + " (" + shown.name().toLowerCase(Locale.ROOT) + ", from its type)"
                : shown.name().toLowerCase(Locale.ROOT);
        context.getSource().sendSuccess(() -> Component.literal(
                region.getName() + ": overlay colour " + label).withStyle(shown.format()), true);
        return 1;
    }

    private static int showRegion(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        ChatFormatting typeColor = region.isDeny() ? ChatFormatting.RED : ChatFormatting.GREEN;
        source.sendSuccess(() -> Component.literal("Region '" + region.getName() + "'").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(" type: " + region.getType()).withStyle(typeColor), false);
        source.sendSuccess(() -> Component.literal(
                " at: " + region.getDimension() + " " + region.describeBounds()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                " priority: " + region.getPriority() + " (higher wins where regions overlap)").withStyle(ChatFormatting.GRAY), false);
        StackColor color = region.effectiveColor();
        String colorNote = region.getColor() == null ? " (from its type)" : "";
        source.sendSuccess(() -> Component.literal(
                " overlay colour: " + color.name().toLowerCase(Locale.ROOT) + colorNote).withStyle(color.format()), false);

        Map<String, String> overrides = region.getSettings();
        if (overrides.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    " settings: none - everything follows the global config").withStyle(ChatFormatting.DARK_GRAY), false);
            source.sendSuccess(() -> Component.literal(
                    " set one with /mobstacker region set " + region.getName() + " <setting> <value>")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                " settings (" + overrides.size() + " overridden here):").withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            ConfigOption option = MobStackerSettings.byId(entry.getKey());
            String global = option != null ? option.currentValue() : "?";
            source.sendSuccess(() -> Component.literal("  " + entry.getKey() + " = ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(entry.getValue()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("   [global " + global + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestRegionSettings(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (ConfigOption option : MobStackerSettings.regionOverridable()) {
            if (option.id().toLowerCase().startsWith(remaining)) {
                builder.suggest(option.id());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRegionOverrides(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        StackRegion region = MobStacker.config.getRegion(StringArgumentType.getString(context, "name"));
        if (region == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining().toLowerCase();
        for (String id : region.getSettings().keySet()) {
            if (id.toLowerCase().startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }
}
