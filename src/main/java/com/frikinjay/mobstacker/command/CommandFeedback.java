package com.frikinjay.mobstacker.command;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.mixin.CommandSourceStackAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
 *
 * <p><b>Panels</b> (1.12.0): an answer a player clicks through - the region list, a region's page -
 * is printed as a panel. Every line of it carries an empty piece of text whose shift-click insertion
 * names the panel ({@link #PANEL_MARK}), the first line with {@link #PANEL_START} on it, and a game
 * with the mod takes the panel's earlier lines out of the chat when a new one starts
 * ({@code mixin.client.ChatComponentMixin}): clicking "[on]" redraws the list in place of the old one
 * instead of piling a new answer under it. Vanilla cannot change a message it was sent, so a game
 * without the mod shows the new panel under the old one - nothing it can see is different, as the
 * mark is empty and too narrow to click.
 */
public final class CommandFeedback {
    /** What the mark's insertion starts with; the panel's name follows. */
    public static final String PANEL_MARK = "mobstacker:panel/";
    /** After the name on a panel's first line: the earlier lines of that panel go. */
    public static final String PANEL_START = "!start";

    /** The panel being printed (server thread), or null. */
    private static String panel;
    /** Whether the next line printed is the panel's first. */
    private static boolean panelStart;

    private CommandFeedback() {
    }

    /**
     * Prints what follows, up to {@link #endPanel}, as the panel {@code id}. Inside a panel already -
     * a switch that redraws the list, which prints a panel of its own - it does nothing and answers
     * false, so the outer one stays and its first line stays the start.
     */
    public static boolean beginPanel(String id) {
        if (panel != null) {
            return false;
        }
        panel = id;
        panelStart = true;
        return true;
    }

    /** Ends the panel {@link #beginPanel} opened (call it only when that answered true). */
    public static void endPanel() {
        panel = null;
        panelStart = false;
    }

    /** The panel a chat line belongs to, or null - read by the client. */
    public static String panelOf(Component line) {
        String mark = markOf(line);
        if (mark == null) {
            return null;
        }
        return mark.endsWith(PANEL_START) ? mark.substring(0, mark.length() - PANEL_START.length()) : mark;
    }

    /** Whether this line starts a panel - read by the client. */
    public static boolean startsPanel(Component line) {
        String mark = markOf(line);
        return mark != null && mark.endsWith(PANEL_START);
    }

    private static String markOf(Component line) {
        if (line == null) {
            return null;
        }
        for (Component part : line.getSiblings()) {
            String insertion = part.getStyle().getInsertion();
            if (insertion != null && insertion.startsWith(PANEL_MARK) && part.getContents() != null
                    && part.getString().isEmpty()) {
                return insertion.substring(PANEL_MARK.length());
            }
        }
        return null;
    }

    /** The line with the panel's mark after it: the same text, and nothing more to see. */
    public static Component mark(Component line, String panelId, boolean start) {
        return Component.empty().append(line).append(Component.literal("")
                .withStyle(Style.EMPTY.withInsertion(PANEL_MARK + panelId + (start ? PANEL_START : ""))));
    }

    public static void success(CommandSourceStack source, Supplier<Component> plain, boolean broadcast) {
        Supplier<Component> message = plain;
        if (panel != null) {
            String id = panel;
            boolean start = panelStart;
            panelStart = false;
            message = () -> mark(plain.get(), id, start);
        }
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
