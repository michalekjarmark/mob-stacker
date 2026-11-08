package com.frikinjay.mobstacker.config;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The separate stack label's wording ({@code stackLabelFormat}): {@code {type}} is the kind of mob,
 * left translatable so each player reads it in their own language, and {@code {count}} the number.
 * Built on the client that draws the label; checked on the server that stores the setting.
 */
public final class StackLabelFormat {
    public static final String TYPE = "{type}";
    public static final String COUNT = "{count}";
    public static final int MAX_LENGTH = 64;

    private StackLabelFormat() {
    }

    /** Why this format cannot be used, or null. Empty means the default. */
    public static String problem(String format) {
        if (format == null || format.isBlank()) {
            return null;
        }
        if (format.length() > MAX_LENGTH) {
            return "A stack label can be " + MAX_LENGTH + " characters at most";
        }
        if (!format.contains(COUNT)) {
            return "The label needs " + COUNT + " in it, or it would not say how many";
        }
        return null;
    }

    /** The label: the format with the kind and the count put in. */
    public static MutableComponent build(String format, Component type, int count) {
        MutableComponent out = Component.empty();
        int at = 0;
        while (at < format.length()) {
            int typeAt = format.indexOf(TYPE, at);
            int countAt = format.indexOf(COUNT, at);
            int next = typeAt < 0 ? countAt : countAt < 0 ? typeAt : Math.min(typeAt, countAt);
            if (next < 0) {
                out.append(format.substring(at));
                break;
            }
            if (next > at) {
                out.append(format.substring(at, next));
            }
            if (next == typeAt) {
                out.append(type.copy());
                at = next + TYPE.length();
            } else {
                out.append(String.valueOf(count));
                at = next + COUNT.length();
            }
        }
        return out;
    }
}
