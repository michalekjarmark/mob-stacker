package com.frikinjay.mobstacker.command;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.mixin.CommandSourceStackAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Every answer the {@code /mobstacker} commands give goes through here instead of
 * {@link CommandSourceStack#sendSuccess}.
 * <p>
 * Vanilla hides a successful command's answer from players while the gamerule
 * {@code sendCommandFeedback} is false. Servers turn it off because of command blocks that spam
 * the chat, which also leaves an admin typing {@code /mobstacker} without any answer. With
 * {@code alwaysShowFeedback} on, the player who typed the command still gets it. A command block
 * or a function stays quiet, including one that runs the command {@code as} a player. Errors were
 * never hidden ({@code sendFailure} ignores the gamerule).
 */
public final class CommandFeedback {
    private CommandFeedback() {
    }

    public static void success(CommandSourceStack source, Supplier<Component> message, boolean broadcast) {
        // Vanilla's own path first: the console log and the other operators, and the player too
        // whenever the gamerule lets it.
        source.sendSuccess(message, broadcast);
        if (MobStacker.config.getAlwaysShowFeedback()
                && ((CommandSourceStackAccessor) source).mobstacker$source() instanceof ServerPlayer player
                && !player.acceptsSuccess()) {
            // Still respects a silenced stack (a function), as vanilla's sendSystemMessage does.
            source.sendSystemMessage(message.get());
        }
    }
}
