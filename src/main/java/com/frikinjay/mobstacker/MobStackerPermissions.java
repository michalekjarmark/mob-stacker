package com.frikinjay.mobstacker;

import net.minecraft.world.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Permission nodes, for servers that run a permissions mod (LuckPerms and the like).
 *
 * <p>Those mods answer through lucko's fabric-permissions-api, which LuckPerms ships inside itself
 * and many other mods do too. It is looked up here by name rather than compiled against, so this mod
 * needs nothing extra installed and nothing extra in its jar: where the API is missing, a node simply
 * falls back to the vanilla operator level, which is what the API itself does for a node nobody set.
 */
public final class MobStackerPermissions {

    /** Sees every region whatever {@code privateRegions} says, like an operator (default: op level 2). */
    public static final String VIEW_ALL_REGIONS = "mobstacker.regions.viewall";

    // Permissions.check(Entity, String, int). The class literal in the method type is remapped with
    // the rest of the jar, so it names the same Entity class at runtime that the API was built against.
    private static final MethodHandle CHECK = find();

    private MobStackerPermissions() {
    }

    private static MethodHandle find() {
        try {
            Class<?> api = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            return MethodHandles.publicLookup().findStatic(api, "check",
                    MethodType.methodType(boolean.class, Entity.class, String.class, int.class));
        } catch (Throwable notThere) {
            return null;
        }
    }

    /** Whether a permissions mod is there to be asked. */
    public static boolean available() {
        return CHECK != null;
    }

    /**
     * Whether {@code entity} has {@code node}: as the permissions mod says, or, when there is none or
     * nobody set the node, whether it has operator level {@code defaultLevel}.
     */
    public static boolean check(Entity entity, String node, int defaultLevel) {
        if (CHECK != null) {
            try {
                return (boolean) CHECK.invokeExact(entity, node, defaultLevel);
            } catch (Throwable e) {
                MobStacker.logger.warn("MobStacker: asking the permissions mod about {} failed", node, e);
            }
        }
        return entity.hasPermissions(defaultLevel);
    }
}
