package com.frikinjay.mobstacker.client;

import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.StackColor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A button that steps through a list of values: the left button forward, the right one back.
 * <p>
 * A vanilla button answers only the left button, so a colour button that cycles through seventeen
 * stops was a long way round to go back one. The region screen's colour button got its own
 * right-click, and every other cycling button (the config screen's colours and modes) did not (the
 * Forge round). Every cycling button on the mod's screens is one of these now. Enter and Space step
 * forward, like a click. The buttons are {@link InputConstants}' numbers: 26.x counts them as SDL does
 * (left 1, right 3), not as GLFW did (left 0, right 1).
 */
//? if >=26.1 {
/*public final class StepButton extends Button.Plain {
*///?} else {
public final class StepButton extends Button {
//?}
    private static final List<String> COLOR_NAMES = new ArrayList<>();

    static {
        for (StackColor color : StackColor.values()) {
            COLOR_NAMES.add(color.name());
        }
    }

    private final Step step;
    private boolean backwards;

    /** What a press does: {@code direction} is +1 for the left button (or a key), -1 for the right one. */
    @FunctionalInterface
    public interface Step {
        void step(StepButton button, int direction);
    }

    public StepButton(int x, int y, int width, int height, Component message, Step step) {
        super(x, y, width, height, message, b -> { }, DEFAULT_NARRATION);
        this.step = step;
    }

    //? if >=26.1 {
    /*@Override
    protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo info) {
        return info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        backwards = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            backwards = false;
        }
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        step.step(this, backwards ? -1 : 1);
    }
    *///?} else {
    @Override
    protected boolean isValidClickButton(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        backwards = button == InputConstants.MOUSE_BUTTON_RIGHT;
        try {
            return super.mouseClicked(mouseX, mouseY, button);
        } finally {
            backwards = false;
        }
    }

    @Override
    public void onPress() {
        step.step(this, backwards ? -1 : 1);
    }
    //?}

    /** {@code index} moved {@code direction} steps round a ring of {@code size}. */
    public static int stepIndex(int index, int direction, int size) {
        return Math.floorMod(index + direction, size);
    }

    /** Whether this setting's values are the sixteen text colours, so its button shows each in itself. */
    public static boolean isColorOption(ConfigOption option) {
        return option.type() == ConfigOption.Type.ENUM && COLOR_NAMES.equals(option.enumValues());
    }

    /**
     * How an enum setting's value is written on its button: a colour in that colour (as the region
     * screen's colour button always did), anything else in aqua.
     */
    public static Component enumLabel(ConfigOption option, String value) {
        if (isColorOption(option)) {
            try {
                StackColor color = StackColor.valueOf(value.toUpperCase(Locale.ROOT));
                return Component.literal(value).withStyle(color.format());
            } catch (IllegalArgumentException ignored) {
                // Not a colour after all: fall through to the usual look.
            }
        }
        return Component.literal(value).withStyle(ChatFormatting.AQUA);
    }
}
