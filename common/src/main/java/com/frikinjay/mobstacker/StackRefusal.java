package com.frikinjay.mobstacker;

/**
 * Why a mob may not stack, or why two mobs may not merge.
 *
 * <p>{@link MobStacker#stackRefusal} and {@link MobStacker#mergeRefusal} answer with one of these
 * instead of a bare boolean, so the rules exist once: the scan asks whether the answer is null, and
 * {@code /mobstacker inspect} tells a player which rule it was. Half of the "stacking is broken"
 * reports in earlier test rounds were a setting read the wrong way, and a player could not see which.
 *
 * <p>Constants, not formatted strings, because the scan asks this for every mob it looks at and a
 * refusal is the usual answer.
 */
public enum StackRefusal {
    // --- A mob on its own (MobStacker#stackRefusal) ---
    CAN_STACK_OFF("its CanStack flag is off"),
    BABY_ANIMAL("it is a baby, and enableAnimalBabyStacking is off here"),
    BABY_HOSTILE("it is a baby, and enableHostileBabyStacking is off here"),
    DYING("it is dying"),
    EQUIPPED("it holds or wears something, and stackEquippedMobs is off here"),
    PLAYER_BOUND("a player has a claim on it: tamed, owned, saddled, leashed, riding or ridden, "
            + "trusting, or halfway through being tamed"),
    SEPARATION_GRACE("it was handed to a player a moment ago (separationCooldown)"),
    STACKING_OFF("stackMode is OFF"),
    DENY_REGION("it stands in a DENY region"),
    OUTSIDE_ALLOW_REGION("stackMode is REGIONS and it stands outside every ALLOW region"),
    NO_PLAYER_NEAR("stackMode is PLAYERS and no player is within playerStackRadius"),
    DENY_LISTED("its kind is on a deny list here"),
    NOT_ALLOW_LISTED("mobListMode is WHITELIST here and its kind is on no allow list"),
    ORIGIN("mobs that came into the world the way it did do not stack here (the Spawn origin settings)"),
    PLAYER_NAMED("a player named it, and stackNamedMobs is off here"),
    FULL("its stack has reached the ceiling for its kind here"),

    // --- Two mobs (MobStacker#mergeRefusal) ---
    DIFFERENT_KIND("they are different kinds of mob"),
    TOO_BIG_TOGETHER("together they would pass the ceiling for their kind"),
    BABY_AND_ADULT("one is a baby and the other is not"),
    DIFFERENT_NAME("a player gave one of them a name the other does not share"),
    DIFFERENT_VARIANT("they differ in something a stack cannot keep apart"),
    OTHER_MOD("another mod's merge rule says no");

    private final String reason;

    StackRefusal(String reason) {
        this.reason = reason;
    }

    /** A plain sentence fragment, e.g. "it is dying", for a player to read. */
    public String reason() {
        return reason;
    }
}
