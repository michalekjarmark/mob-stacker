package com.frikinjay.mobstacker.fabric.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Lets a region's corners be picked by clicking two blocks, instead of typing six numbers.
 *
 * <p>Started from the region editor and handing its answer straight back to it: the two clicks only
 * fill in the same coordinate boxes somebody could have typed, and Save still goes through
 * {@code RegionEdit} exactly as before. Nothing here writes config, so there is no second set of
 * rules about names, dimensions or shapes that could drift away from the first.
 *
 * <p>Client-side and transient — nothing is stored, and a picking session that is not finished
 * simply ends. While the first corner is down the overlay draws the box it would make with the block
 * under the crosshair, which is the whole point: a region's reach is much easier to judge by looking
 * at it than by comparing two coordinate triples.
 */
public final class MobStackerRegionPicker {

    /** How often the reminder is re-sent, since an action-bar line fades after a few seconds. */
    private static final int REMINDER_INTERVAL = 20;

    private static MobStackerRegionEditScreen waiting;
    private static BlockPos first;
    private static int reminderTimer;
    /** The dimension picking started in; corners from two different ones do not describe anything. */
    private static ResourceKey<Level> dimension;

    private MobStackerRegionPicker() {
    }

    public static void register() {
        // Fires before the block or the held item is used, so the click can be taken for picking
        // instead - otherwise choosing a corner would place a torch on it.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!isPicking() || !level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (player.isShiftKeyDown()) {
                cancel("Corner picking cancelled");
                return InteractionResult.FAIL;
            }
            onPicked(hitResult.getBlockPos());
            // FAIL rather than SUCCESS: the click is spent, and there is no arm swing to play for it.
            return InteractionResult.FAIL;
        });

        // A mob standing in front of the block you meant is hit first, and without this the click
        // would shear the sheep instead. It cannot pick anything - there is no block in the hit - so
        // it is swallowed and the prompt repeated rather than quietly doing something else.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!isPicking() || !level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (player.isShiftKeyDown()) {
                cancel("Corner picking cancelled");
                return InteractionResult.FAIL;
            }
            remind();
            return InteractionResult.FAIL;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isPicking()) {
                return;
            }
            if (client.screen != null) {
                return; // chat or a menu is open: picking waits rather than being thrown away
            }
            if (client.player == null || client.level == null) {
                reset(); // no world to pick in any more
                return;
            }
            if (!client.level.dimension().equals(dimension)) {
                // A region's corners only mean anything in one dimension, and a portal is not a
                // reason to guess which one was meant.
                cancel("Corner picking cancelled — you changed dimension");
                return;
            }
            if (--reminderTimer <= 0) {
                remind();
            }
        });
    }

    /**
     * Hands control to the world: the editor closes, and the next two block clicks become its
     * corners. Call it from the screen that wants them back.
     */
    public static void begin(MobStackerRegionEditScreen screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        waiting = screen;
        first = null;
        dimension = client.level == null ? null : client.level.dimension();
        client.setScreen(null);
        remind();
    }

    public static boolean isPicking() {
        return waiting != null;
    }

    private static void onPicked(BlockPos pos) {
        if (first == null) {
            first = pos.immutable();
            remind();
            return;
        }
        MobStackerRegionEditScreen screen = waiting;
        BlockPos start = first;
        reset();
        Minecraft client = Minecraft.getInstance();
        // The editor is the same object it always was, so handing the corners back and showing it
        // again keeps the name, the type and the dimension the player had already chosen.
        screen.applyPickedCorners(start, pos);
        client.setScreen(screen);
    }

    private static void cancel(String reason) {
        MobStackerRegionEditScreen screen = waiting;
        reset();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal(reason).withStyle(ChatFormatting.GRAY), true);
        }
        if (screen != null) {
            client.setScreen(screen);
        }
    }

    private static void reset() {
        waiting = null;
        first = null;
        dimension = null;
        reminderTimer = 0;
    }

    private static void remind() {
        reminderTimer = REMINDER_INTERVAL;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        String text = first == null
                ? "Click the first corner   (sneak + click to cancel)"
                : "Click the opposite corner   (sneak + click to cancel)";
        client.player.displayClientMessage(
                Component.literal(text).withStyle(ChatFormatting.YELLOW), true);
    }

    /**
     * The box being drawn right now — from the corner already picked to the block under the
     * crosshair — or null when nothing is being picked or nothing is in range.
     *
     * <p>Recomputed per frame on purpose: the whole value of the preview is that it follows where
     * the player is looking.
     */
    public static AABB previewBox() {
        if (first == null) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            // Looking at the sky: still show the block already chosen, so the first click is visibly
            // registered rather than leaving the player wondering whether it counted.
            return boxBetween(first, first);
        }
        return boxBetween(first, blockHit.getBlockPos());
    }

    /**
     * The volume two corner blocks enclose. Both blocks are inside the region, so the far side of
     * each maximum block is where it ends - the same +1 the finished region's box uses.
     */
    private static AABB boxBetween(BlockPos a, BlockPos b) {
        return new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1.0,
                Math.max(a.getY(), b.getY()) + 1.0,
                Math.max(a.getZ(), b.getZ()) + 1.0);
    }
}
