package com.frikinjay.mobstacker.config;

import net.minecraft.ChatFormatting;

/**
 * The sixteen colours Minecraft can render text in, as a config value.
 * <p>
 * This exists instead of using {@link ChatFormatting} directly so that the settings registry only
 * ever offers actual colours: {@code ChatFormatting} also contains styles such as {@code BOLD} and
 * {@code RESET}, which would show up in tab-completion and in the GUI's cycle button as if they were
 * valid choices.
 */
public enum StackColor {
    BLACK(ChatFormatting.BLACK),
    DARK_BLUE(ChatFormatting.DARK_BLUE),
    DARK_GREEN(ChatFormatting.DARK_GREEN),
    DARK_AQUA(ChatFormatting.DARK_AQUA),
    DARK_RED(ChatFormatting.DARK_RED),
    DARK_PURPLE(ChatFormatting.DARK_PURPLE),
    GOLD(ChatFormatting.GOLD),
    GRAY(ChatFormatting.GRAY),
    DARK_GRAY(ChatFormatting.DARK_GRAY),
    BLUE(ChatFormatting.BLUE),
    GREEN(ChatFormatting.GREEN),
    AQUA(ChatFormatting.AQUA),
    RED(ChatFormatting.RED),
    LIGHT_PURPLE(ChatFormatting.LIGHT_PURPLE),
    YELLOW(ChatFormatting.YELLOW),
    WHITE(ChatFormatting.WHITE);

    private final ChatFormatting format;

    StackColor(ChatFormatting format) {
        this.format = format;
    }

    public ChatFormatting format() {
        return format;
    }
}
