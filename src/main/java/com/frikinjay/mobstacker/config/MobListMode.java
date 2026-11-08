package com.frikinjay.mobstacker.config;

/**
 * Which half of the mob lists decides whether a mob may stack.
 *
 * <p>Until 1.9.0 only the blacklist existed, so this is the missing half rather than a change of
 * behaviour: {@link #BLACKLIST} is the default and reads the same lists ({@code ignoredEntities} /
 * {@code ignoredMods}) that every config written before 1.9.0 already contains.
 *
 * <p>Like every other setting, it is resolved where it is read, so a region may run a whitelist
 * while the rest of the world runs a blacklist.
 */
public enum MobListMode {
    /** Everything stacks except what is on the deny lists. The default, and how the mod always worked. */
    BLACKLIST,
    /** Nothing stacks except what is on the allow lists. An empty allow list therefore stacks nothing. */
    WHITELIST
}
