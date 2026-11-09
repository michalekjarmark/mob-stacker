package com.frikinjay.mobstacker.command;

import net.minecraft.world.entity.MobSpawnType;
import com.frikinjay.mobstacker.compat.Compat;
import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.ConfigOption.Category;
import com.frikinjay.mobstacker.config.ConfigSelfTest;
import com.frikinjay.mobstacker.config.MobListKind;
import com.frikinjay.mobstacker.config.MobListMode;
import com.frikinjay.mobstacker.config.MobLists;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import com.frikinjay.mobstacker.config.SettingSearch;
import com.frikinjay.mobstacker.config.StackMode;
import com.frikinjay.mobstacker.config.RegionBiomes;
import com.frikinjay.mobstacker.config.RegionEdit;
import com.frikinjay.mobstacker.config.StackColor;
import com.frikinjay.mobstacker.config.StackRegion;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
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
                // Operators, and region owners for the one thing they may do: share a region. Every
                // other branch asks for an operator itself (isAdmin), so an owner sees no more than
                // /mobstacker region share - ConfigSelfTest walks the tree to keep it that way.
                .requires(MobStackerCommands::mayUseRoot)
                .executes(MobStackerCommands::showOverview)
                .then(literal("help").requires(MobStackerCommands::isAdmin)
                        .executes(MobStackerCommands::showHelpRoot)
                        .then(argument("topic", StringArgumentType.greedyString())
                                .suggests(MobStackerCommands::suggestCategories)
                                .executes(MobStackerCommands::showHelpCategory)))
                .then(literal("reload").requires(MobStackerCommands::isAdmin).executes(MobStackerCommands::reloadConfig))
                .then(literal("get").requires(MobStackerCommands::isAdmin)
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestSettings)
                                .executes(MobStackerCommands::getValue)))
                .then(literal("set").requires(MobStackerCommands::isAdmin)
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestSettings)
                                .then(argument("value", StringArgumentType.greedyString())
                                        .suggests(MobStackerCommands::suggestValues)
                                        .executes(MobStackerCommands::setValue))))
                .then(literal("toggle").requires(MobStackerCommands::isAdmin)
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestToggles)
                                .executes(MobStackerCommands::toggleValue)))
                .then(literal("reset").requires(MobStackerCommands::isAdmin)
                        .then(literal("all").executes(MobStackerCommands::resetAll))
                        .then(argument("setting", StringArgumentType.word())
                                .suggests(MobStackerCommands::suggestSettings)
                                .executes(MobStackerCommands::resetValue)))
                .then(literal("stacksize").requires(MobStackerCommands::isAdmin)
                        .then(argument("target", EntityArgument.entity())
                                .then(argument("size", IntegerArgumentType.integer(1))
                                        .executes(MobStackerCommands::setStackSizeLive))))
                .then(literal("list").requires(MobStackerCommands::isAdmin)
                        .then(listHalf("deny", MobListKind.Half.DENY, null))
                        .then(listHalf("allow", MobListKind.Half.ALLOW, null)))
                // The old name for the deny lists, kept working because it is in every existing
                // guide and in people's fingers. It reaches exactly the same code.
                .then(literal("ignore").requires(MobStackerCommands::isAdmin)
                        .then(listFlavour(MobListKind.DENY_ENTITIES, null))
                        .then(listFlavour(MobListKind.DENY_MODS, null)))
                .then(literal("maxstack").requires(MobStackerCommands::isAdmin)
                        .then(literal("list").executes(ctx -> showMaxStacks(ctx, null)))
                        .then(argument("entityId", ResourceLocationArgument.id())
                                .suggests(MobStackerCommands::suggestEntities)
                                .then(literal("default").executes(ctx -> setMaxStack(ctx, null, null)))
                                // Brigadier wants a number where a number is declared, so "max" is a
                                // branch of its own beside it - the same shape "default" already uses.
                                .then(literal(ConfigOption.MAX_KEYWORD)
                                        .executes(ctx -> setMaxStack(ctx, null, Integer.MAX_VALUE)))
                                .then(argument("size", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setMaxStack(ctx, null,
                                                IntegerArgumentType.getInteger(ctx, "size"))))))
                .then(literal("region")
                        .then(literal("owner").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(MobStackerCommands::showRegionAccess)
                                        .then(literal("none").executes(ctx -> setRegionOwner(ctx, false)))
                                        .then(argument("player", GameProfileArgument.gameProfile())
                                                .executes(ctx -> setRegionOwner(ctx, true)))))
                        // The one branch a region's owner may use without being an operator; each
                        // handler checks again that the region named is theirs.
                        .then(literal("share").requires(MobStackerCommands::mayUseRoot)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestSharableRegions)
                                        .executes(MobStackerCommands::showRegionAccess)
                                        .then(literal("add")
                                                .then(argument("player", GameProfileArgument.gameProfile())
                                                        .executes(MobStackerCommands::shareRegion)))
                                        .then(literal("remove")
                                                .then(argument("player", StringArgumentType.word())
                                                        .suggests(MobStackerCommands::suggestSharedPlayers)
                                                        .executes(MobStackerCommands::unshareRegion)))))
                        .then(literal("add").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .then(literal("allow")
                                                .then(argument("corner1", BlockPosArgument.blockPos())
                                                        .then(argument("corner2", BlockPosArgument.blockPos())
                                                                .executes(ctx -> addRegion(ctx, StackRegion.Type.ALLOW))))
                                                .then(wholeDimension(ctx -> addWholeDimension(ctx, StackRegion.Type.ALLOW))))
                                        .then(literal("deny")
                                                .then(argument("corner1", BlockPosArgument.blockPos())
                                                        .then(argument("corner2", BlockPosArgument.blockPos())
                                                                .executes(ctx -> addRegion(ctx, StackRegion.Type.DENY))))
                                                .then(wholeDimension(ctx -> addWholeDimension(ctx, StackRegion.Type.DENY))))))
                        .then(literal("bounds").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("corner1", BlockPosArgument.blockPos())
                                                .then(argument("corner2", BlockPosArgument.blockPos())
                                                        .executes(MobStackerCommands::setRegionBounds)))
                                        .then(wholeDimension(MobStackerCommands::setRegionWholeDimension))))
                        .then(literal("biomes").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(MobStackerCommands::showRegionBiomes)
                                        .then(literal("add")
                                                .then(argument("biome", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.BIOME))
                                                        .suggests(MobStackerCommands::suggestBiomes)
                                                        .executes(ctx -> editRegionBiomes(ctx, true))))
                                        .then(literal("remove")
                                                .then(argument("biome", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.BIOME))
                                                        .suggests(MobStackerCommands::suggestRegionBiomes)
                                                        .executes(ctx -> editRegionBiomes(ctx, false))))
                                        .then(literal("clear").executes(MobStackerCommands::clearRegionBiomes))))
                        .then(literal("enable").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(ctx -> setRegionEnabled(ctx, true, null, 0))
                                        // What the switches in the chat run: the panel they sit in is
                                        // drawn again, so the switch shows what it did.
                                        .then(literal("show").executes(ctx -> setRegionEnabled(ctx, true, REDRAW_SHOW, 0)))
                                        .then(literal("list").then(argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setRegionEnabled(ctx, true, REDRAW_LIST,
                                                        IntegerArgumentType.getInteger(ctx, "page")))))))
                        .then(literal("disable").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(ctx -> setRegionEnabled(ctx, false, null, 0))
                                        // What the switches in the chat run: the panel they sit in is
                                        // drawn again, so the switch shows what it did.
                                        .then(literal("show").executes(ctx -> setRegionEnabled(ctx, false, REDRAW_SHOW, 0)))
                                        .then(literal("list").then(argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> setRegionEnabled(ctx, false, REDRAW_LIST,
                                                        IntegerArgumentType.getInteger(ctx, "page")))))))
                        .then(literal("type").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(literal("allow")
                                                .executes(ctx -> setRegionType(ctx, StackRegion.Type.ALLOW)))
                                        .then(literal("deny")
                                                .executes(ctx -> setRegionType(ctx, StackRegion.Type.DENY)))))
                        .then(literal("remove").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(MobStackerCommands::removeRegion)))
                        .then(literal("list").requires(MobStackerCommands::isAdmin)
                                .executes(ctx -> listRegions(ctx, 1))
                                .then(argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> listRegions(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(literal("show").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .executes(MobStackerCommands::showRegion)))
                        .then(literal("set").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("setting", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestRegionSettings)
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .suggests(MobStackerCommands::suggestValues)
                                                        .executes(MobStackerCommands::setRegionValue)))))
                        .then(literal("unset").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("setting", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestRegionOverrides)
                                                .executes(MobStackerCommands::unsetRegionValue))))
                        .then(literal("priority").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("priority", IntegerArgumentType.integer())
                                                .executes(MobStackerCommands::setRegionPriority))))
                        .then(literal("rename").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("newname", StringArgumentType.word())
                                                .executes(MobStackerCommands::renameRegion))))
                        // "mobs" rather than "list", which this tree already uses for listing regions.
                        .then(literal("mobs").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(listHalf("deny", MobListKind.Half.DENY, "name"))
                                        .then(listHalf("allow", MobListKind.Half.ALLOW, "name"))))
                        .then(literal("maxstack").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(literal("list").executes(ctx -> showMaxStacks(ctx, "name")))
                                        .then(argument("entityId", ResourceLocationArgument.id())
                                                .suggests(MobStackerCommands::suggestEntities)
                                                .then(literal("default").executes(ctx -> setMaxStack(ctx, "name", null)))
                                                .then(literal(ConfigOption.MAX_KEYWORD)
                                                        .executes(ctx -> setMaxStack(ctx, "name", Integer.MAX_VALUE)))
                                                .then(argument("size", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> setMaxStack(ctx, "name",
                                                                IntegerArgumentType.getInteger(ctx, "size")))))))
                        .then(literal("color").requires(MobStackerCommands::isAdmin)
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(MobStackerCommands::suggestRegions)
                                        .then(argument("color", StringArgumentType.word())
                                                .suggests(MobStackerCommands::suggestRegionColors)
                                                .executes(MobStackerCommands::setRegionColor)))));

        // The self-test command is an opt-in developer/testing tool: it is only registered when the
        // JVM is started with -Dmobstacker.selftest=true, so the public release never exposes it.
        if (Boolean.getBoolean("mobstacker.selftest")) {
            root.then(literal("selftest").requires(MobStackerCommands::isAdmin).executes(MobStackerCommands::runSelfTest));
        }
        StackToolsCommands.attach(root);
        dispatcher.register(root);
    }

    // ============================================================ generic settings

    private static int getValue(CommandContext<CommandSourceStack> context) {
        ConfigOption option = MobStackerSettings.byId(StringArgumentType.getString(context, "setting"));
        if (option == null) {
            return unknownSetting(context, StringArgumentType.getString(context, "setting"));
        }
        CommandFeedback.success(context.getSource(), () -> Component.literal(option.id()).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                .append(valueComponent(option))
                .append(Component.literal("   [default " + option.defaultValue() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
        CommandFeedback.success(context.getSource(), () -> Component.literal(option.description()).withStyle(ChatFormatting.GRAY), false);
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
            CommandFeedback.success(context.getSource(), () -> Component.literal(note).withStyle(ChatFormatting.YELLOW), false);
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
        CommandFeedback.success(context.getSource(), () -> Component.literal("Reset " + count + " setting(s) to their defaults").withStyle(ChatFormatting.AQUA), true);
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
                CommandFeedback.success(source, () -> message, true);
                return 1;
            }
            case UNCHANGED -> {
                CommandFeedback.success(source, () -> Component.literal(option.id() + " is already " + result.oldValue).withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }
            default -> {
                source.sendFailure(Component.literal(result.message).withStyle(ChatFormatting.RED));
                return 0;
            }
        }
    }

    /**
     * No setting by that name: says which one was most likely meant, as a click that puts the
     * command back in the chat box with the name put right (1.12.0, the same search as the config
     * screen's).
     */
    private static int unknownSetting(CommandContext<CommandSourceStack> context, String settingId) {
        MutableComponent message = Component.literal("Unknown setting '" + settingId + "'.").withStyle(ChatFormatting.RED);
        ConfigOption meant = SettingSearch.best(settingId, MobStackerSettings.all());
        String input = context.getInput();
        int at = input.lastIndexOf(settingId);
        if (meant != null && at >= 0) {
            String fixed = "/" + input.substring(0, at) + meant.id() + input.substring(at + settingId.length());
            message.append(Component.literal(" Did you mean ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(meant.id()).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                            .withUnderlined(true)
                            .withClickEvent(Compat.suggestCommand(fixed))
                            .withHoverEvent(Compat.showText(Component.literal(fixed)))))
                    .append(Component.literal("?").withStyle(ChatFormatting.RED));
        } else {
            message.append(Component.literal(" Try /mobstacker help <words>").withStyle(ChatFormatting.RED));
        }
        context.getSource().sendFailure(message);
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
        if (!isAdmin(context.getSource())) {
            return showOwnerOverview(context.getSource());
        }
        CommandSourceStack source = context.getSource();
        CommandFeedback.success(source, () -> Component.literal("=== MobStacker: Restacked ===").withStyle(ChatFormatting.GOLD), false);
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
            CommandFeedback.success(source, () -> line, false);
        }
        CommandFeedback.success(source, () -> Component.literal("Regions: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(MobStacker.config.getRegions().size())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("   Ignored entities: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(MobStacker.config.getIgnoredEntities().size())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("   Ignored mods: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(MobStacker.config.getIgnoredMods().size())).withStyle(ChatFormatting.AQUA)), false);
        CommandFeedback.success(source, () -> Component.literal("Commands: set, get, toggle, reset, list, maxstack, region, inspect, stats, unstack, stack, reload  -  ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/mobstacker help").withStyle(ChatFormatting.YELLOW)), false);
        warnIfStacksNowhere(source);
        return 1;
    }

    private static int showHelpRoot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CommandFeedback.success(source, () -> Component.literal("=== MobStacker: Restacked - help ===").withStyle(ChatFormatting.GOLD), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker set <setting> <value>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  change a setting").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker get|toggle|reset <setting>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  inspect / flip / restore").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker reset all").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  restore every setting to default").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker stacksize <target> <n>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  force a targeted mob's live stack count").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker list <deny|allow> <entity|mod> <add|remove|list>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  which mobs stack; see mobListMode").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker maxstack <entity> <n|default> | maxstack list").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  a ceiling for one mob type").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker region <add|bounds|biomes|type|enable|disable|color|rename|remove|list|show|set|unset|priority|mobs|maxstack|owner|share>").withStyle(ChatFormatting.YELLOW), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker region add <name> <allow|deny> dimension [dimension]").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  a region that is a whole dimension, with no box").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker region biomes <name> [add|remove <biome|#tag>|clear]").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  only the parts of a region in these biomes").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker region owner <name> <player|none> | region share <name> <add|remove> <player>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  who besides admins sees a region (privateRegions)").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker inspect [target]").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  a stack up close, and why it will not stack").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker stats [radius]").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  how many mobs the stacks stand for").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker unstack <radius|loaded>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  split stacks back into mobs - do this before removing the mod").withStyle(ChatFormatting.GRAY)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker stack <radius>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  merge what can merge here now, whatever stackMode says").withStyle(ChatFormatting.GRAY)), false);

        CommandFeedback.success(source, () -> Component.literal("/mobstacker help <words>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  find a setting by a few letters or words, e.g. help seperator, help wool").withStyle(ChatFormatting.GRAY)), false);
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
        CommandFeedback.success(source, () -> categories, false);
        return 1;
    }

    private static int showHelpCategory(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "topic").trim();
        Category category = null;
        for (Category candidate : Category.values()) {
            if (candidate.name().equalsIgnoreCase(key) || candidate.display().equalsIgnoreCase(key)) {
                category = candidate;
                break;
            }
        }
        if (category == null) {
            return showHelpSearch(context, key);
        }
        final Category resolved = category;
        CommandFeedback.success(context.getSource(), () -> Component.literal("[" + resolved.display() + "]").withStyle(ChatFormatting.GOLD), false);
        for (ConfigOption option : MobStackerSettings.byCategory(category)) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(option.id()).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                    .append(valueComponent(option))
                    .append(Component.literal("   [default " + option.defaultValue() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
            CommandFeedback.success(context.getSource(), () -> Component.literal("  " + option.description()).withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    /** How many settings {@code help <words>} lists; the config screen's search shows them all. */
    private static final int HELP_SEARCH_SHOWN = 8;

    /**
     * {@code help <words>} when the words are not a category: the settings the config screen's
     * search would find, best first, each a click away from {@code set}.
     */
    private static int showHelpSearch(CommandContext<CommandSourceStack> context, String words) {
        List<SettingSearch.Hit> hits = SettingSearch.search(words, MobStackerSettings.all());
        if (hits.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No category or setting matches '" + words
                    + "'. Try fewer letters, or a word from what the setting does.").withStyle(ChatFormatting.RED));
            return 0;
        }
        CommandSourceStack source = context.getSource();
        CommandFeedback.success(source, () -> Component.literal("Settings for '" + words + "':").withStyle(ChatFormatting.GOLD), false);
        for (SettingSearch.Hit hit : hits.subList(0, Math.min(HELP_SEARCH_SHOWN, hits.size()))) {
            ConfigOption option = hit.option();
            String command = "/mobstacker set " + option.id() + " ";
            CommandFeedback.success(source, () -> Component.literal(option.id()).withStyle(style -> style
                            .withColor(ChatFormatting.WHITE).withUnderlined(true)
                            .withClickEvent(Compat.suggestCommand(command))
                            .withHoverEvent(Compat.showText(Component.literal(command))))
                    .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                    .append(valueComponent(option))
                    .append(Component.literal("   [" + option.category().display() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
            CommandFeedback.success(source, () -> Component.literal("  " + option.description()).withStyle(ChatFormatting.DARK_GRAY), false);
        }
        if (hits.size() > HELP_SEARCH_SHOWN) {
            int more = hits.size() - HELP_SEARCH_SHOWN;
            CommandFeedback.success(source, () -> Component.literal("... and " + more + " more: more words narrow it down")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return hits.size();
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
            if (Compat.create(entityType, context.getSource().getLevel(), MobSpawnType.COMMAND) instanceof Mob) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                if (id.toString().toLowerCase().startsWith(remaining)) {
                    builder.suggest(id.toString());
                }
            }
        });
        return builder.buildFuture();
    }

    /** The mobs, then every entity tag the game knows ({@code #minecraft:raiders}), for a mob list. */
    private static CompletableFuture<Suggestions> suggestEntitiesAndTags(CommandContext<CommandSourceStack> context,
                                                                       SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        // "#rai" is "rai" among the tags: the "#" is what was typed to ask for one, not part of the
        // name. Compared with it, nothing matched once a "#" was typed (round 12).
        boolean tagTyped = remaining.startsWith(MobLists.TAG_PREFIX);
        String bare = tagTyped ? remaining.substring(MobLists.TAG_PREFIX.length()) : remaining;
        for (String tag : MobLists.tagEntries()) {
            String id = tag.substring(MobLists.TAG_PREFIX.length());
            if (id.startsWith(bare) || id.substring(id.indexOf(':') + 1).startsWith(bare)) {
                builder.suggest(tag);
            }
        }
        return tagTyped ? builder.buildFuture() : suggestEntities(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        Set<String> modsWithMobs = new HashSet<>();
        BuiltInRegistries.ENTITY_TYPE.forEach(entityType -> {
            if (Compat.create(entityType, context.getSource().getLevel(), MobSpawnType.COMMAND) instanceof Mob) {
                modsWithMobs.add(BuiltInRegistries.ENTITY_TYPE.getKey(entityType).getNamespace());
            }
        });
        modsWithMobs.stream()
                .filter(modId -> modId.toLowerCase().startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    // ============================================================ who sees a region

    /** An operator: everything in this tree but {@code region share} asks for this. */
    private static boolean isAdmin(CommandSourceStack source) {
        return Compat.hasPermission(source, 2);
    }

    /**
     * Operators, and players who own a region - who may share it, and nothing more. Asked of the
     * entity behind the command, by UUID: only an operator can make a command run as another one.
     */
    private static boolean mayUseRoot(CommandSourceStack source) {
        return isAdmin(source) || (source.getEntity() != null && MobStacker.ownsRegion(source.getEntity().getUUID()));
    }

    private static boolean mayManageSharing(CommandSourceStack source, StackRegion region) {
        return isAdmin(source) || (source.getEntity() != null && region.isOwnedBy(source.getEntity().getUUID()));
    }

    /**
     * The region named by the command when this source may say who sees it - an operator, or its
     * owner - and otherwise a refusal. A region that is somebody else's is refused in the same words
     * as one that does not exist: answering differently would tell a player which names are taken.
     */
    private static StackRegion requireSharableRegion(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        StackRegion region = MobStacker.config.getRegion(name);
        CommandSourceStack source = context.getSource();
        if (region != null && mayManageSharing(source, region)) {
            return region;
        }
        source.sendFailure(Component.literal(isAdmin(source)
                ? "Region '" + name + "' does not exist"
                : "You have no region called '" + name + "'").withStyle(ChatFormatting.RED));
        return null;
    }

    /** What {@code /mobstacker} alone says to a region owner who is not an operator. */
    private static int showOwnerOverview(CommandSourceStack source) {
        List<String> own = new ArrayList<>();
        if (source.getEntity() != null) {
            for (StackRegion region : MobStacker.config.getRegions()) {
                if (region.isOwnedBy(source.getEntity().getUUID())) {
                    own.add(region.getName());
                }
            }
        }
        CommandFeedback.success(source, () -> Component.literal("Your regions: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.join(", ", own)).withStyle(ChatFormatting.AQUA)), false);
        CommandFeedback.success(source, () -> Component.literal("/mobstacker region share <region> add <player>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  let a friend see one too; ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("remove <player>").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" takes it back, and no player at all shows who has it").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    /** A player's name as the server knows it now, falling back to the one stored with the region. */
    private static String playerName(MinecraftServer server, String uuid, String stored) {
        try {
            UUID id = UUID.fromString(uuid);
            String known = Compat.knownName(server, id);
            if (known != null) {
                return known;
            }
        } catch (IllegalArgumentException ignored) {
            // not a UUID: show what was stored
        }
        return stored != null ? stored : uuid;
    }

    /** The "who sees it" lines of {@code region show} and {@code region share <name>}. */
    private static List<Component> regionAccessLines(MinecraftServer server, StackRegion region) {
        List<Component> lines = new ArrayList<>();
        UUID owner = region.getOwner();
        lines.add(Component.literal(" owner: ").withStyle(ChatFormatting.GRAY)
                .append(owner == null
                        ? Component.literal("nobody").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(playerName(server, owner.toString(), region.getOwnerName())).withStyle(ChatFormatting.AQUA)));
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, String> entry : region.getSharedWith().entrySet()) {
            names.add(playerName(server, entry.getKey(), entry.getValue()));
        }
        lines.add(Component.literal(" shared with: ").withStyle(ChatFormatting.GRAY)
                .append(names.isEmpty()
                        ? Component.literal("nobody").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(String.join(", ", names)).withStyle(ChatFormatting.AQUA)));
        lines.add(Component.literal(MobStacker.regionIsPrivate(region)
                ? " seen by: admins, its owner and the players it is shared with"
                : " seen by: everybody (privateRegions is off for it)").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static int showRegionAccess(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireSharableRegion(context);
        if (region == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        CommandFeedback.success(source, () -> Component.literal("Region '" + region.getName() + "'").withStyle(ChatFormatting.AQUA), false);
        for (Component line : regionAccessLines(source.getServer(), region)) {
            CommandFeedback.success(source, () -> line, false);
        }
        return 1;
    }

    private static int setRegionOwner(CommandContext<CommandSourceStack> context, boolean set) throws CommandSyntaxException {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        if (!set) {
            if (region.getOwner() == null) {
                CommandFeedback.success(source, () -> Component.literal(region.getName() + " has no owner").withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }
            region.setOwner(null, null);
            MobStacker.config.save();
            CommandFeedback.success(source, () -> Component.literal(region.getName() + " has no owner now").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        List<Compat.PlayerRef> profiles = Compat.profiles(context, "player");
        if (profiles.size() != 1) {
            source.sendFailure(Component.literal("A region has one owner - name a single player").withStyle(ChatFormatting.RED));
            return 0;
        }
        Compat.PlayerRef profile = profiles.get(0);
        region.setOwner(profile.id(), profile.name());
        // The owner sees it anyway; staying on the list as well would outlive a change of owner.
        region.unshare(profile.id());
        MobStacker.config.save();
        CommandFeedback.success(source, () -> Component.literal(region.getName() + " now belongs to " + profile.name()
                + " - they see it, and may share it with /mobstacker region share").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int shareRegion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        StackRegion region = requireSharableRegion(context);
        if (region == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        List<String> added = new ArrayList<>();
        for (Compat.PlayerRef profile : Compat.profiles(context, "player")) {
            if (region.isOwnedBy(profile.id())) {
                CommandFeedback.success(source, () -> Component.literal(profile.name() + " owns " + region.getName()
                        + " and sees it anyway").withStyle(ChatFormatting.YELLOW), false);
            } else if (region.share(profile.id(), profile.name())) {
                added.add(profile.name());
            } else {
                CommandFeedback.success(source, () -> Component.literal(region.getName() + " is already shared with "
                        + profile.name()).withStyle(ChatFormatting.YELLOW), false);
            }
        }
        if (added.isEmpty()) {
            return 0;
        }
        MobStacker.config.save();
        CommandFeedback.success(source, () -> Component.literal("Shared " + region.getName() + " with " + String.join(", ", added))
                .withStyle(ChatFormatting.GREEN), true);
        if (!MobStacker.regionIsPrivate(region)) {
            CommandFeedback.success(source, () -> Component.literal(" (privateRegions is off for it, so everybody sees it anyway)")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    private static int unshareRegion(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireSharableRegion(context);
        if (region == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        String who = StringArgumentType.getString(context, "player");
        // By the name the server knows now, the name stored when it was shared, or the UUID - a
        // player who was renamed, or never comes back, must still be removable.
        for (Map.Entry<String, String> entry : region.getSharedWith().entrySet()) {
            String name = playerName(source.getServer(), entry.getKey(), entry.getValue());
            if (who.equalsIgnoreCase(name) || who.equalsIgnoreCase(entry.getValue()) || who.equalsIgnoreCase(entry.getKey())) {
                region.unshare(UUID.fromString(entry.getKey()));
                MobStacker.config.save();
                CommandFeedback.success(source, () -> Component.literal(name + " no longer sees " + region.getName())
                        .withStyle(ChatFormatting.GREEN), true);
                return 1;
            }
        }
        source.sendFailure(Component.literal(region.getName() + " is not shared with '" + who + "'").withStyle(ChatFormatting.RED));
        return 0;
    }

    /** Operators get every region, an owner only their own. */
    private static CompletableFuture<Suggestions> suggestSharableRegions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (StackRegion region : MobStacker.config.getRegions()) {
            if (mayManageSharing(context.getSource(), region) && region.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(region.getName());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSharedPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        StackRegion region = MobStacker.config.getRegion(StringArgumentType.getString(context, "name"));
        if (region != null && mayManageSharing(context.getSource(), region)) {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : region.getSharedWith().entrySet()) {
                String name = playerName(context.getSource().getServer(), entry.getKey(), entry.getValue());
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        }
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

    // ============================================================ mob lists

    /**
     * The {@code deny} / {@code allow} half of the list commands, with an entity and a mod branch
     * under it. Built once and hung in two places — under {@code /mobstacker list} for the global
     * lists and under {@code /mobstacker region mobs <name>} for a region's own — so the two scopes
     * cannot grow different spellings, different validation or different messages.
     *
     * @param regionArg the name of the command argument holding the region, or null for global
     */
    private static LiteralArgumentBuilder<CommandSourceStack> listHalf(String name, MobListKind.Half half,
                                                                      String regionArg) {
        return literal(name)
                .then(listFlavour(MobListKind.of(half, MobListKind.Flavour.ENTITY), regionArg))
                .then(listFlavour(MobListKind.of(half, MobListKind.Flavour.MOD), regionArg));
    }

    /** One list's {@code add} / {@code remove} / {@code list} (and, for a region, {@code inherit}). */
    private static LiteralArgumentBuilder<CommandSourceStack> listFlavour(MobListKind kind, String regionArg) {
        boolean entities = kind.flavour() == MobListKind.Flavour.ENTITY;
        String argName = entities ? "entityId" : "modId";
        LiteralArgumentBuilder<CommandSourceStack> node = literal(entities ? "entity" : "mod")
                // An entity entry is one mob (minecraft:cow) or, since 1.12.0, a tag of them
                // (#minecraft:raiders): vanilla's resource-or-tag argument reads both.
                .then(literal("add")
                        .then((entities
                                ? argument(argName, ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ENTITY_TYPE))
                                        .suggests(MobStackerCommands::suggestEntitiesAndTags)
                                : argument(argName, StringArgumentType.word()).suggests(MobStackerCommands::suggestMods))
                                .executes(ctx -> editList(ctx, kind, regionArg, true))))
                .then(literal("remove")
                        .then((entities
                                ? argument(argName, ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ENTITY_TYPE))
                                : argument(argName, StringArgumentType.word()))
                                .suggests((ctx, builder) -> suggestListed(ctx, builder, kind, regionArg))
                                .executes(ctx -> editList(ctx, kind, regionArg, false))))
                .then(literal("list").executes(ctx -> showList(ctx, kind, regionArg)));
        if (regionArg != null) {
            // Only a region has the two states; the global list is what everything falls back to.
            node = node.then(literal("inherit").executes(ctx -> inheritList(ctx, kind, regionArg)))
                    .then(literal("override").executes(ctx -> overrideList(ctx, kind, regionArg)));
        }
        return node;
    }

    /**
     * The holder the command is editing: a named region, or the global config.
     *
     * @return null when a region was named and does not exist, after telling the player so
     */
    private static MobLists.Holder listHolder(CommandContext<CommandSourceStack> context, String regionArg) {
        if (regionArg == null) {
            return MobStacker.config;
        }
        String regionName = StringArgumentType.getString(context, regionArg);
        StackRegion region = MobStacker.config.getRegion(regionName);
        if (region == null) {
            context.getSource().sendFailure(Component.literal("Region '" + regionName + "' does not exist"));
        }
        return region;
    }

    /** "globally" or "in region 'x'", so every message below reads the same in both scopes. */
    private static String scopeOf(CommandContext<CommandSourceStack> context, String regionArg) {
        return regionArg == null
                ? "globally"
                : "in region '" + StringArgumentType.getString(context, regionArg) + "'";
    }

    private static String listEntry(CommandContext<CommandSourceStack> context, MobListKind kind) {
        // asPrintable() is "minecraft:cow" for a mob and "#minecraft:raiders" for a tag - the very
        // spelling a list stores.
        return kind.flavour() == MobListKind.Flavour.ENTITY
                ? context.getArgument("entityId", ResourceOrTagKeyArgument.Result.class).asPrintable()
                : StringArgumentType.getString(context, "modId");
    }

    private static int editList(CommandContext<CommandSourceStack> context, MobListKind kind,
                                String regionArg, boolean add) {
        MobLists.Holder holder = listHolder(context, regionArg);
        if (holder == null) {
            return 0;
        }
        String entry = MobLists.normalise(kind, listEntry(context, kind));
        String scope = scopeOf(context, regionArg);
        // Only on the way in: an entry already stored is left alone whatever it names, so a list
        // written while a mod was installed can still be tidied up after it is gone.
        if (add) {
            String problem = MobLists.entryProblem(kind, entry);
            if (problem != null) {
                context.getSource().sendFailure(Component.literal(problem));
                return 0;
            }
        }
        if (regionArg != null && !holder.hasList(kind)) {
            // The region is inheriting. Adding here would start an override holding only this one
            // entry, quietly dropping the global list it was showing a moment ago - so say what to
            // run instead, the same refusal the GUI makes by greying the row out.
            String regionName = StringArgumentType.getString(context, regionArg);
            context.getSource().sendFailure(Component.literal(
                    kind.id() + " " + scope + " follows the global list. Run '/mobstacker region mobs "
                            + regionName + " " + (kind.half() == MobListKind.Half.ALLOW ? "allow" : "deny")
                            + " " + (kind.flavour() == MobListKind.Flavour.ENTITY ? "entity" : "mod")
                            + " override' first to give the region its own copy."));
            return 0;
        }
        boolean changed = add ? holder.addToList(kind, entry) : holder.removeFromList(kind, entry);
        if (!changed) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                            "'" + entry + "' is " + (add ? "already on " : "not on ") + kind.id() + " " + scope)
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        // A region's lists live inside the config object, so the region path has to ask for the save
        // that the global path already did for itself.
        MobStacker.config.save();
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                        (add ? "Added '" : "Removed '") + entry + (add ? "' to " : "' from ") + kind.id() + " " + scope)
                .withStyle(add ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
        if (add) {
            String note = MobLists.entryNote(kind, entry);
            if (note != null) {
                CommandFeedback.success(context.getSource(), () -> Component.literal(" " + note)
                        .withStyle(ChatFormatting.YELLOW), false);
            }
            noteIfNotRead(context, kind, regionArg, scope);
        }
        return 1;
    }

    /**
     * Says so, right after the edit, when the list just added to is the half {@code mobListMode} is
     * not reading - and says which command would change that.
     *
     * <p>{@code list} already reports this, but nobody runs {@code list} straight after an add. The
     * entry really was stored, so this is a note rather than a failure: the list is being built for
     * a mode that is not on yet, which is a perfectly ordinary thing to be doing.
     */
    private static void noteIfNotRead(CommandContext<CommandSourceStack> context, MobListKind kind,
                                      String regionArg, String scope) {
        MobListMode mode = regionArg == null
                ? MobStacker.config.getMobListMode()
                : regionMode(StringArgumentType.getString(context, regionArg));
        if ((mode == MobListMode.WHITELIST) == (kind.half() == MobListKind.Half.ALLOW)) {
            return;
        }
        String want = kind.half() == MobListKind.Half.ALLOW ? "whitelist" : "blacklist";
        String fix = regionArg == null
                ? "/mobstacker set mobListMode " + want
                : "/mobstacker region set " + StringArgumentType.getString(context, regionArg)
                        + " mobListMode " + want;
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                " mobListMode " + scope + " is " + mode + ", so this list is not being read")
                .withStyle(ChatFormatting.RED), false);
        CommandFeedback.success(context.getSource(), () -> Component.literal(" change it with " + fix)
                .withStyle(ChatFormatting.GRAY), false);
    }

    private static int inheritList(CommandContext<CommandSourceStack> context, MobListKind kind, String regionArg) {
        MobLists.Holder holder = listHolder(context, regionArg);
        if (holder == null) {
            return 0;
        }
        String scope = scopeOf(context, regionArg);
        if (!holder.hasList(kind)) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    kind.id() + " " + scope + " already follows the global list").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        holder.clearList(kind);
        MobStacker.config.save();
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                kind.id() + " " + scope + " now follows the global list").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int overrideList(CommandContext<CommandSourceStack> context, MobListKind kind, String regionArg) {
        MobLists.Holder holder = listHolder(context, regionArg);
        if (holder == null) {
            return 0;
        }
        String scope = scopeOf(context, regionArg);
        if (holder.hasList(kind)) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    kind.id() + " " + scope + " is already this region's own").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        // Seeded with what was being inherited, so taking a list over does not silently empty it.
        holder.setList(kind, MobStacker.config.getList(kind));
        MobStacker.config.save();
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                kind.id() + " " + scope + " is now this region's own copy").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int showList(CommandContext<CommandSourceStack> context, MobListKind kind, String regionArg) {
        MobLists.Holder holder = listHolder(context, regionArg);
        if (holder == null) {
            return 0;
        }
        String scope = scopeOf(context, regionArg);
        boolean own = holder.hasList(kind);
        List<String> entries = own ? holder.getList(kind) : MobStacker.config.getList(kind);
        String heading = kind.id() + " " + scope + (own ? "" : " (inherited)");
        if (entries.isEmpty()) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(heading + ": empty").withStyle(ChatFormatting.YELLOW), false);
        } else {
            CommandFeedback.success(context.getSource(), () -> Component.literal(heading + " (" + entries.size() + "):")
                    .withStyle(ChatFormatting.AQUA), false);
            for (String entry : entries) {
                CommandFeedback.success(context.getSource(), () -> Component.literal(" - " + entry).withStyle(ChatFormatting.GRAY), false);
            }
        }
        // The mode decides whether this list is read at all, so say which one is in force rather
        // than let somebody carefully fill a list that nothing is looking at.
        MobListMode mode = regionArg == null
                ? MobStacker.config.getMobListMode()
                : regionMode(StringArgumentType.getString(context, regionArg));
        boolean active = (mode == MobListMode.WHITELIST) == (kind.half() == MobListKind.Half.ALLOW);
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                        "mobListMode " + scope + " is " + mode + ", so this list is "
                                + (active ? "in use" : "not being read"))
                .withStyle(active ? ChatFormatting.DARK_GRAY : ChatFormatting.RED), false);
        return 1;
    }

    /** The list mode in force inside a region, answered by the same code the verdict itself uses. */
    private static MobListMode regionMode(String regionName) {
        return MobLists.modeIn(MobStacker.config.getRegion(regionName));
    }

    private static CompletableFuture<Suggestions> suggestListed(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder,
                                                                MobListKind kind, String regionArg) {
        MobLists.Holder holder = regionArg == null
                ? MobStacker.config
                : MobStacker.config.getRegion(StringArgumentType.getString(context, regionArg));
        if (holder == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        holder.getList(kind).stream()
                .filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    // ============================================================ per-type stack ceilings

    private static int setMaxStack(CommandContext<CommandSourceStack> context, String regionArg, Integer size) {
        String entityId = MobLists.normaliseEntityId(
                ResourceLocationArgument.getId(context, "entityId").toString());
        String scope = scopeOf(context, regionArg);
        String problem = size == null ? null : MobLists.entityProblem(entityId);
        if (problem != null) {
            context.getSource().sendFailure(Component.literal(problem));
            return 0;
        }
        if (regionArg == null) {
            MobStacker.config.setMaxStackSize(entityId, size);
        } else {
            String regionName = StringArgumentType.getString(context, regionArg);
            StackRegion region = MobStacker.config.getRegion(regionName);
            if (region == null) {
                context.getSource().sendFailure(Component.literal("Region '" + regionName + "' does not exist"));
                return 0;
            }
            region.setMaxStackSize(entityId, size);
            MobStacker.config.save();
        }
        CommandFeedback.success(context.getSource(), () -> Component.literal(size == null
                        ? "'" + entityId + "' " + scope + " follows maxStackSize again"
                        : "'" + entityId + "' stacks up to " + size + " " + scope)
                .withStyle(size == null ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        String note = size == null ? null : MobLists.entryNote(MobListKind.DENY_ENTITIES, entityId);
        if (note != null) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(" " + note)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int showMaxStacks(CommandContext<CommandSourceStack> context, String regionArg) {
        Map<String, Integer> sizes;
        String scope = scopeOf(context, regionArg);
        if (regionArg == null) {
            sizes = MobStacker.config.getMaxStackSizes();
        } else {
            String regionName = StringArgumentType.getString(context, regionArg);
            StackRegion region = MobStacker.config.getRegion(regionName);
            if (region == null) {
                context.getSource().sendFailure(Component.literal("Region '" + regionName + "' does not exist"));
                return 0;
            }
            sizes = region.getMaxStackSizes();
        }
        if (sizes.isEmpty()) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                            "No per-type ceilings " + scope + "; everything follows maxStackSize")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                "Per-type ceilings " + scope + " (" + sizes.size() + "):").withStyle(ChatFormatting.AQUA), false);
        sizes.forEach((id, size) -> CommandFeedback.success(context.getSource(), 
                () -> Component.literal(" - " + id + " -> " + size).withStyle(ChatFormatting.GRAY), false));
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
                CommandFeedback.success(context.getSource(), () -> Component.literal("That mob's stack size is already " + newSize).withStyle(ChatFormatting.YELLOW), false);
            } else {
                MobStacker.setStackSize(mob, newSize);
                CommandFeedback.success(context.getSource(), () -> Component.literal("Set the targeted mob's stack size to " + newSize).withStyle(ChatFormatting.GREEN), true);
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error setting stack size: " + e.getMessage()).withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    // ============================================================ reload

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        MobStacker.reloadConfig();
        CommandFeedback.success(context.getSource(), () -> Component.literal("MobStacker config reloaded from disk").withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    /**
     * Runs the automated settings self-test against a sandbox config (the live per-world config is
     * untouched) and reports pass/fail counts, listing any failures. Handy for verifying that every
     * setting still parses/validates/round-trips after changes to the registry.
     */
    private static int runSelfTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ConfigSelfTest.Report report = ConfigSelfTest.run(source);
        if (report.passed()) {
            CommandFeedback.success(source, () -> Component.literal(
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
                && MobStacker.config.getRegions().stream().noneMatch(r -> r.isAllow() && r.isEnabled());
    }

    /** Emits a yellow heads-up when the current config would stack nowhere (see {@link #stacksNowhere()}). */
    private static void warnIfStacksNowhere(CommandSourceStack source) {
        if (stacksNowhere()) {
            CommandFeedback.success(source, () -> Component.literal(
                    "Note: stackMode is REGIONS but no ALLOW region is defined (or every one is off) - nothing will stack anywhere. "
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
        String dimension = Compat.keyId(context.getSource().getLevel().dimension()).toString();

        return reportRegionEdit(context, RegionEdit.apply(name, type, dimension,
                corner1.getX(), corner1.getY(), corner1.getZ(),
                corner2.getX(), corner2.getY(), corner2.getZ()));
    }

    /**
     * The {@code dimension [<dimension>]} tail of {@code region add} and {@code region bounds}: the
     * region becomes the whole of the dimension named, or of the one the command is run in.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> wholeDimension(
            com.mojang.brigadier.Command<CommandSourceStack> command) {
        return literal("dimension")
                .executes(command)
                .then(argument("dimension", DimensionArgument.dimension()).executes(command));
    }

    /** The dimension named after {@code dimension}, or the one the command was run in. */
    private static String dimensionArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            return Compat.keyId(DimensionArgument.getDimension(context, "dimension").dimension()).toString();
        } catch (IllegalArgumentException notGiven) {
            // Brigadier's answer to asking for an argument the command was typed without.
            return Compat.keyId(context.getSource().getLevel().dimension()).toString();
        }
    }

    private static int addWholeDimension(CommandContext<CommandSourceStack> context, StackRegion.Type type)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (MobStacker.config.getRegion(name) != null) {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' already exists. "
                    + "Use /mobstacker region bounds " + name + " dimension to make it a whole dimension, "
                    + "which keeps its settings.").withStyle(ChatFormatting.RED));
            return 0;
        }
        return reportRegionEdit(context, RegionEdit.applyWholeDimension(name, type, dimensionArgument(context)));
    }

    /** Makes an existing region its whole dimension; the box it had is kept for going back to one. */
    private static int setRegionWholeDimension(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        return reportRegionEdit(context, RegionEdit.applyWholeDimension(region.getName(), region.getType(),
                dimensionArgument(context)));
    }

    // --- biome filters (1.12.0) -------------------------------------------------------------------

    private static int showRegionBiomes(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        CommandFeedback.success(context.getSource(), () -> region.hasBiomeFilter()
                ? Component.literal(region.getName() + " covers only " + RegionBiomes.join(region.getBiomes()))
                        .withStyle(ChatFormatting.AQUA)
                : Component.literal(region.getName() + " covers every biome. Narrow it with /mobstacker region biomes "
                        + region.getName() + " add <biome|#tag>").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int editRegionBiomes(CommandContext<CommandSourceStack> context, boolean add) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        String entry = context.getArgument("biome", ResourceOrTagKeyArgument.Result.class).asPrintable();
        RegionEdit.Result result = RegionEdit.editBiomes(region.getName(), List.of(entry), add);
        if (!result.ok() && MobStacker.config.getRegion(region.getName()) != null
                && RegionBiomes.entryProblem(entry) == null) {
            // Nothing to change: said in yellow, like every other "already".
            CommandFeedback.success(context.getSource(), () -> Component.literal(result.message())
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        int outcome = reportRegionEdit(context, result);
        if (outcome == 1 && add) {
            String note = RegionBiomes.entryNote(entry, context.getSource().getServer().registryAccess()
                    .registryOrThrow(Registries.BIOME));
            if (note != null) {
                CommandFeedback.success(context.getSource(), () -> Component.literal(" " + note)
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }
        return outcome;
    }

    private static int clearRegionBiomes(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        if (!region.hasBiomeFilter()) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    region.getName() + " already covers every biome").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        return reportRegionEdit(context, RegionEdit.editBiomes(region.getName(), region.getBiomes(), false));
    }

    /** Every biome and biome tag this server knows, tags first, matched with or without a namespace. */
    private static CompletableFuture<Suggestions> suggestBiomes(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        var biomes = context.getSource().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        List<String> all = new ArrayList<>();
        Compat.tagKeys(biomes).forEach(tag -> all.add(RegionBiomes.TAG_PREFIX + tag.location()));
        biomes.keySet().forEach(id -> all.add(id.toString()));
        for (String entry : all) {
            String bare = entry.startsWith(RegionBiomes.TAG_PREFIX) ? entry.substring(1) : entry;
            if (entry.startsWith(remaining) || bare.substring(bare.indexOf(':') + 1).startsWith(remaining)) {
                builder.suggest(entry);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRegionBiomes(CommandContext<CommandSourceStack> context,
                                                                      SuggestionsBuilder builder) {
        StackRegion region = MobStacker.config.getRegion(StringArgumentType.getString(context, "name"));
        if (region != null) {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            region.getBiomes().stream().filter(entry -> entry.startsWith(remaining)).forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    /** {@code region enable|disable <name> show}: the region's page is drawn again after the switch. */
    private static final String REDRAW_SHOW = "show";
    /** {@code region enable|disable <name> list <page>}: that page of the list is drawn again. */
    private static final String REDRAW_LIST = "list";
    /** The chat panel the region list and a region's page share (CommandFeedback#beginPanel). */
    private static final String REGIONS_PANEL = "regions";

    /**
     * Switches a region on or off. Clicked in the chat ({@code redraw} not null), the answer is the
     * panel it was clicked in, drawn again with the switch the other way round: on a game with the
     * mod it takes the old panel's place (round 12: the old one stayed, saying "[switch off]").
     */
    private static int setRegionEnabled(CommandContext<CommandSourceStack> context, boolean enabled,
                                        String redraw, int page) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        boolean opened = redraw != null && CommandFeedback.beginPanel(REGIONS_PANEL);
        try {
            int result;
            if (region.isEnabled() == enabled) {
                CommandFeedback.success(context.getSource(), () -> Component.literal(
                        "Region '" + region.getName() + "' is already " + (enabled ? "on" : "off")).withStyle(ChatFormatting.YELLOW), false);
                result = 0;
            } else {
                result = reportRegionEdit(context, RegionEdit.setEnabled(region.getName(), enabled));
            }
            if (REDRAW_SHOW.equals(redraw)) {
                showRegion(context);
            } else if (REDRAW_LIST.equals(redraw)) {
                listRegions(context, page);
            } else {
                warnIfStacksNowhere(context.getSource());
            }
            return result;
        } finally {
            if (opened) {
                CommandFeedback.endPanel();
            }
        }
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
        String dimension = Compat.keyId(context.getSource().getLevel().dimension()).toString();
        boolean moved = !dimension.equals(region.getDimension());

        int result = reportRegionEdit(context, RegionEdit.apply(name, region.getType(), dimension,
                corner1.getX(), corner1.getY(), corner1.getZ(),
                corner2.getX(), corner2.getY(), corner2.getZ()));
        if (result == 1 && moved) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(
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
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    "Region '" + name + "' is already " + type).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        // The corners as they were given, so changing the type does not quietly sort them - and a
        // whole-dimension region stays one.
        int[] c = region.getCorners();
        RegionEdit.Result result = RegionEdit.apply(name, type, region.getDimension(), region.isWholeDimension(),
                c[0], c[1], c[2], c[3], c[4], c[5], null, false);
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal(result.message()).withStyle(ChatFormatting.RED));
            return 0;
        }
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                "Region '" + name + "' is now " + type).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Turns a {@link RegionEdit} outcome into the usual green/red command feedback. */
    private static int reportRegionEdit(CommandContext<CommandSourceStack> context, RegionEdit.Result result) {
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal(result.message()).withStyle(ChatFormatting.RED));
            return 0;
        }
        CommandFeedback.success(context.getSource(), () -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** The "priority 5, 3 settings" tail shown after a region's bounds, omitted when there is none. */
    private static String describeRegionExtras(StackRegion region) {
        StringBuilder out = new StringBuilder();
        if (region.hasBiomeFilter()) {
            int biomes = region.getBiomes().size();
            out.append("  in ").append(biomes).append(biomes == 1 ? " biome" : " biomes");
        }
        if (region.getPriority() != 0) {
            out.append("  priority ").append(region.getPriority());
        }
        int overrides = region.getSettings().size();
        if (overrides > 0) {
            out.append("  ").append(overrides).append(overrides == 1 ? " setting" : " settings");
        }
        if (region.getOwner() != null) {
            out.append("  owner ").append(region.getOwnerName());
        }
        int shared = region.getSharedWith().size();
        if (shared > 0) {
            out.append("  shared with ").append(shared);
        }
        if (!MobStacker.regionIsPrivate(region)) {
            out.append("  public");
        }
        return out.toString();
    }

    private static int removeRegion(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (MobStacker.config.removeRegion(name)) {
            MobStacker.config.save();
            CommandFeedback.success(context.getSource(), () -> Component.literal("Removed region '" + name + "'").withStyle(ChatFormatting.GOLD), true);
        } else {
            context.getSource().sendFailure(Component.literal("Region '" + name + "' does not exist").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    /** How many regions one page of {@code region list} shows. */
    private static final int REGIONS_PER_PAGE = 10;

    /**
     * The regions, a page at a time, each line clickable: the name opens {@code region show}, and
     * the switch at the end turns the region off or on. The arrows at the bottom turn the page. Click
     * events do nothing in the server console, which still reads the same text.
     */
    private static int listRegions(CommandContext<CommandSourceStack> context, int requestedPage) {
        boolean opened = CommandFeedback.beginPanel(REGIONS_PANEL);
        try {
            return printRegionList(context, requestedPage);
        } finally {
            if (opened) {
                CommandFeedback.endPanel();
            }
        }
    }

    private static int printRegionList(CommandContext<CommandSourceStack> context, int requestedPage) {
        List<StackRegion> regions = MobStacker.config.getRegions();
        String mode = String.valueOf(MobStacker.config.getStackMode());
        CommandSourceStack source = context.getSource();

        if (regions.isEmpty()) {
            CommandFeedback.success(source, () -> Component.literal("No regions defined (current mode: " + mode + ")").withStyle(ChatFormatting.YELLOW), false);
            warnIfStacksNowhere(source);
            return 1;
        }

        int pages = (regions.size() + REGIONS_PER_PAGE - 1) / REGIONS_PER_PAGE;
        int page = Math.min(requestedPage, pages);
        String pageNote = pages > 1 ? ", page " + page + " of " + pages : "";
        CommandFeedback.success(source, () -> Component.literal("Stacking regions (mode: " + mode + pageNote + "):")
                .withStyle(ChatFormatting.AQUA), false);
        int from = (page - 1) * REGIONS_PER_PAGE;
        for (StackRegion region : regions.subList(from, Math.min(regions.size(), from + REGIONS_PER_PAGE))) {
            CommandFeedback.success(source, () -> regionListLine(region, page), false);
        }
        if (pages > 1) {
            MutableComponent nav = Component.literal(" ");
            nav.append(page > 1
                    ? clickable("\u00ab previous", ChatFormatting.YELLOW, "/mobstacker region list " + (page - 1), "Page " + (page - 1))
                    : Component.literal("\u00ab previous").withStyle(ChatFormatting.DARK_GRAY));
            nav.append(Component.literal("   " + page + " / " + pages + "   ").withStyle(ChatFormatting.GRAY));
            nav.append(page < pages
                    ? clickable("next \u00bb", ChatFormatting.YELLOW, "/mobstacker region list " + (page + 1), "Page " + (page + 1))
                    : Component.literal("next \u00bb").withStyle(ChatFormatting.DARK_GRAY));
            CommandFeedback.success(source, () -> nav, false);
        }
        warnIfStacksNowhere(source);
        return 1;
    }

    /**
     * One region in {@code region list}: its name (click: its page), what it is, and its on/off switch.
     * What can be clicked is underlined and in colour; what is only said is grey (round 12: all of it
     * was one green, and the links could not be told from the text).
     */
    private static Component regionListLine(StackRegion region, int page) {
        boolean on = region.isEnabled();
        ChatFormatting color = !on ? ChatFormatting.GRAY : region.isDeny() ? ChatFormatting.RED : ChatFormatting.GREEN;
        MutableComponent line = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY);
        line.append(clickable(region.getName(), color, "/mobstacker region show " + region.getName(),
                "Show everything about " + region.getName()));
        line.append(Component.literal(" [" + region.getType() + "] " + region.getDimension() + " "
                + region.describeBounds() + describeRegionExtras(region))
                .withStyle(on ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        line.append(Component.literal("  "));
        line.append(on
                ? clickable("[on]", ChatFormatting.GREEN, "/mobstacker region disable " + region.getName() + " list " + page,
                        "Click to switch " + region.getName() + " off (it is kept with everything it carries)")
                : clickable("[off]", ChatFormatting.GRAY, "/mobstacker region enable " + region.getName() + " list " + page,
                        "Click to switch " + region.getName() + " back on"));
        return line;
    }

    /** The page of {@code region list} a region is on. */
    private static int listPageOf(StackRegion region) {
        int index = MobStacker.config.getRegions().indexOf(region);
        return index < 0 ? 1 : index / REGIONS_PER_PAGE + 1;
    }

    /**
     * A command clicked in a chat panel, from 1.21.6 where the click is a custom action
     * ({@code Compat.runCommand}): run as the player who clicked, exactly as if typed, so the
     * player's own permissions decide. Only the mod's own commands: a click never runs anything else.
     */
    public static void runClicked(ServerPlayer player, String command) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        if (line.startsWith("mobstacker ")) {
            player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), line);
        }
    }

    /**
     * Text that runs {@code command} when clicked in chat, with {@code hover} as its tooltip:
     * underlined, the way a link looks, so it stands out from the text around it.
     */
    private static MutableComponent clickable(String text, ChatFormatting color, String command, String hover) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withUnderlined(true)
                .withClickEvent(Compat.runCommand(command))
                .withHoverEvent(Compat.showText(Component.literal(hover))));
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
        if (canonical.equalsIgnoreCase(option.storedValue())) {
            // What the server already says is not an override, as in the region screen: the region
            // follows the global value again. For privateRegions this matters - it is never pruned
            // on save, so `region set farm privateRegions true` on a private server used to pin the
            // region private, and switching the server public later left it hidden (round 10).
            if (previous != null) {
                region.clearSetting(option.id());
                MobStacker.config.save();
            }
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    region.getName() + ": " + option.id() + " follows the global config (" + canonical
                            + ") - a region keeps a value of its own only while it differs").withStyle(ChatFormatting.GREEN),
                    previous != null);
            return 1;
        }
        if (canonical.equals(previous)) {
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    region.getName() + ": " + option.id() + " is already " + canonical).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        region.setSetting(option.id(), canonical);
        MobStacker.config.save();
        String from = previous != null ? previous : option.currentValue() + " (global)";
        CommandFeedback.success(context.getSource(), () -> Component.literal(
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
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    region.getName() + " does not override " + id).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        MobStacker.config.save();
        String now = option != null ? option.currentValue() : "the global value";
        CommandFeedback.success(context.getSource(), () -> Component.literal(
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
            CommandFeedback.success(context.getSource(), () -> Component.literal(
                    region.getName() + ": priority is already " + priority).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        region.setPriority(priority);
        MobStacker.config.save();
        CommandFeedback.success(context.getSource(), () -> Component.literal(
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
        CommandFeedback.success(context.getSource(), () -> Component.literal(
                region.getName() + ": overlay colour " + label).withStyle(shown.format()), true);
        return 1;
    }

    private static int showRegion(CommandContext<CommandSourceStack> context) {
        StackRegion region = requireRegion(context);
        if (region == null) {
            return 0;
        }
        boolean opened = CommandFeedback.beginPanel(REGIONS_PANEL);
        try {
            return printRegion(context.getSource(), region);
        } finally {
            if (opened) {
                CommandFeedback.endPanel();
            }
        }
    }

    private static int printRegion(CommandSourceStack source, StackRegion region) {
        ChatFormatting typeColor = region.isDeny() ? ChatFormatting.RED : ChatFormatting.GREEN;
        CommandFeedback.success(source, () -> Component.literal("Region '" + region.getName() + "'   ").withStyle(ChatFormatting.AQUA)
                .append(clickable("\u00ab all regions", ChatFormatting.YELLOW, "/mobstacker region list " + listPageOf(region),
                        "Back to the list of regions")), false);
        CommandFeedback.success(source, () -> region.isEnabled()
                ? Component.literal(" switched: on  ").withStyle(ChatFormatting.GREEN)
                        .append(clickable("[switch off]", ChatFormatting.YELLOW, "/mobstacker region disable " + region.getName() + " show",
                                "Keeps the region with everything it carries, but it does nothing until switched on"))
                : Component.literal(" switched: OFF - it does nothing until switched on  ").withStyle(ChatFormatting.GRAY)
                        .append(clickable("[switch on]", ChatFormatting.YELLOW, "/mobstacker region enable " + region.getName() + " show",
                                "Switch " + region.getName() + " back on")), false);
        CommandFeedback.success(source, () -> Component.literal(" type: " + region.getType()).withStyle(typeColor), false);
        CommandFeedback.success(source, () -> Component.literal(
                " at: " + region.getDimension() + " " + region.describeBounds()).withStyle(ChatFormatting.GRAY), false);
        CommandFeedback.success(source, () -> region.hasBiomeFilter()
                ? Component.literal(" biomes: only " + RegionBiomes.join(region.getBiomes())).withStyle(ChatFormatting.GOLD)
                : Component.literal(" biomes: every one").withStyle(ChatFormatting.DARK_GRAY), false);
        CommandFeedback.success(source, () -> Component.literal(
                " priority: " + region.getPriority() + " (higher wins where regions overlap)").withStyle(ChatFormatting.GRAY), false);
        StackColor color = region.effectiveColor();
        String colorNote = region.getColor() == null ? " (from its type)" : "";
        CommandFeedback.success(source, () -> Component.literal(
                " overlay colour: " + color.name().toLowerCase(Locale.ROOT) + colorNote).withStyle(color.format()), false);
        for (Component line : regionAccessLines(source.getServer(), region)) {
            CommandFeedback.success(source, () -> line, false);
        }

        showRegionLists(source, region);

        Map<String, String> overrides = region.getSettings();
        if (overrides.isEmpty()) {
            CommandFeedback.success(source, () -> Component.literal(
                    " settings: none - everything follows the global config").withStyle(ChatFormatting.DARK_GRAY), false);
            CommandFeedback.success(source, () -> Component.literal(
                    " set one with /mobstacker region set " + region.getName() + " <setting> <value>")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        CommandFeedback.success(source, () -> Component.literal(
                " settings (" + overrides.size() + " overridden here):").withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            ConfigOption option = MobStackerSettings.byId(entry.getKey());
            String global = option != null ? option.currentValue() : "?";
            CommandFeedback.success(source, () -> Component.literal("  " + entry.getKey() + " = ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(entry.getValue()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("   [global " + global + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    /**
     * The mob lists and per-type ceilings this region carries, summarised in {@code region show}.
     *
     * <p>They have their own commands and their own GUI tab, but {@code show} is where people look
     * to answer "what does this region actually do", and a region quietly running a whitelist is
     * exactly the kind of thing that should not need a second command to notice.
     */
    private static void showRegionLists(CommandSourceStack source, StackRegion region) {
        MobListMode mode = MobLists.modeIn(region);
        boolean ownMode = region.getSetting("mobListMode") != null;
        CommandFeedback.success(source, () -> Component.literal(
                        " mob lists: " + mode + (ownMode ? " (set here)" : " (from the global config)"))
                .withStyle(ownMode ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY), false);

        for (MobListKind kind : MobListKind.values()) {
            // Only the half the mode actually reads; printing the other two every time would bury
            // the one line that matters under three that do nothing.
            boolean read = (mode == MobListMode.WHITELIST) == (kind.half() == MobListKind.Half.ALLOW);
            if (!read) {
                continue;
            }
            boolean own = region.hasList(kind);
            int size = MobLists.effective(kind, region).size();
            CommandFeedback.success(source, () -> Component.literal(
                            "  " + kind.id() + ": " + size + (own ? " (this region's own)" : " (inherited)"))
                    .withStyle(own ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY), false);
        }

        Map<String, Integer> ceilings = region.getMaxStackSizes();
        if (!ceilings.isEmpty()) {
            CommandFeedback.success(source, () -> Component.literal(
                    "  stack ceilings set here (" + ceilings.size() + "):").withStyle(ChatFormatting.GOLD), false);
            ceilings.forEach((id, size) -> CommandFeedback.success(source, 
                    () -> Component.literal("   " + id + " -> " + size).withStyle(ChatFormatting.GRAY), false));
        }
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
