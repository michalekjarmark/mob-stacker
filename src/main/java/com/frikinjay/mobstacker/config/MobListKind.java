package com.frikinjay.mobstacker.config;

import java.util.Locale;

/**
 * The four mob lists, named once so that the commands, the GUI, the config-sync networking and the
 * verdict itself all address them the same way.
 *
 * <p>There are two halves. The <b>deny</b> lists say "these never stack" and are consulted while
 * {@code mobListMode} is {@code BLACKLIST}; the <b>allow</b> lists say "only these stack" and are
 * consulted while it is {@code WHITELIST}. They are kept apart rather than reinterpreted by the
 * mode, so switching the mode over to have a look does not quietly invert the meaning of a list
 * somebody spent time building — flip it back and your list is still your list.
 *
 * <p>Each half comes in an <b>entity</b> flavour ({@code minecraft:cow}) and a <b>mod</b> flavour
 * ({@code minecraft}, matching everything from that mod), because "all of Alex's Mobs" is a thing
 * people want to say in one line.
 *
 * <p>This is deliberately the same shape as {@link ConfigOption}: declare the list once here and
 * every path that touches lists picks it up, instead of four bespoke command branches and four
 * bespoke screens.
 */
public enum MobListKind {
    DENY_ENTITIES(Half.DENY, Flavour.ENTITY, "ignoredEntities", "never stack"),
    DENY_MODS(Half.DENY, Flavour.MOD, "ignoredMods", "never stack"),
    ALLOW_ENTITIES(Half.ALLOW, Flavour.ENTITY, "allowedEntities", "are the only ones that stack"),
    ALLOW_MODS(Half.ALLOW, Flavour.MOD, "allowedMods", "are the only ones that stack");

    /** Which mode consults this list. */
    public enum Half { DENY, ALLOW }

    /** What the entries in the list are: full entity ids, or mod namespaces. */
    public enum Flavour { ENTITY, MOD }

    private final Half half;
    private final Flavour flavour;
    private final String id;
    private final String verb;

    MobListKind(Half half, Flavour flavour, String id, String verb) {
        this.half = half;
        this.flavour = flavour;
        this.id = id;
        this.verb = verb;
    }

    public Half half() {
        return half;
    }

    public Flavour flavour() {
        return flavour;
    }

    /** The name this list has in the config file, in commands and on the wire. */
    public String id() {
        return id;
    }

    /** How to finish the sentence "entities on this list …", for command and tooltip text. */
    public String verb() {
        return verb;
    }

    /** The list of this half holding {@code flavour} entries. */
    public static MobListKind of(Half half, Flavour flavour) {
        for (MobListKind kind : values()) {
            if (kind.half == half && kind.flavour == flavour) {
                return kind;
            }
        }
        throw new IllegalArgumentException("no list for " + half + "/" + flavour);
    }

    /** The list called {@code id}, or null when nothing is. Case-insensitive, like the settings. */
    public static MobListKind byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (MobListKind kind : values()) {
            if (kind.id.toLowerCase(Locale.ROOT).equals(wanted)) {
                return kind;
            }
        }
        return null;
    }
}
