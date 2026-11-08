package com.frikinjay.mobstacker.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Everything the client side does, for every loader alike; a loader's client entrypoint only hooks it
 * up. Runs solely on the client, so a dedicated server never loads these classes and the mod stays
 * fully functional server-side without a client install.
 * <p>
 * Two ways to open the config GUI — an (unbound by default) key binding and the client command
 * {@code /mobstackerconfig} — and two more keys for the region boxes. The loader registers
 * {@link #keys()}, the command ({@link #CONFIG_COMMAND} running {@link #requestConfigScreen()}), and
 * calls {@link #onEndClientTick} at the end of every client tick.
 */
public final class MobStackerClient {

    /** The client command that opens the config screen. */
    public static final String CONFIG_COMMAND = "mobstackerconfig";

    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.mobstacker.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, // unbound by default; the player assigns it in Controls
            "key.categories.mobstacker");

    private static final KeyMapping TOGGLE_OVERLAY = new KeyMapping(
            "key.mobstacker.toggle_region_overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, // unbound by default, like the config key
            "key.categories.mobstacker");

    private static final KeyMapping TOGGLE_XRAY = new KeyMapping(
            "key.mobstacker.toggle_region_xray",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, // unbound by default, like the other two
            "key.categories.mobstacker");

    // Set by the client command; the screen is opened on the next tick so the chat screen (which
    // closes right after a command runs) doesn't immediately override it.
    private static boolean openRequested;

    private MobStackerClient() {
    }

    /**
     * Called once by the loader's client entrypoint, with the loader's way of talking to the server.
     */
    public static void init(MobStackerClientNetworking.Transport transport) {
        // Client side of the config-sync protocol (S2C snapshot receiver + disconnect cleanup).
        MobStackerClientNetworking.init(transport);
        // In-world region boxes. Purely a client-side view of the world, so nothing here is sent
        // anywhere and a server never knows or cares whether a player has them switched on.
        MobStackerRegionOverlay.init();
    }

    /** The key bindings, for the loader to register. */
    public static List<KeyMapping> keys() {
        return List.of(OPEN_CONFIG, TOGGLE_OVERLAY, TOGGLE_XRAY);
    }

    /** What {@code /mobstackerconfig} does: open the screen on the next tick, after the chat has closed. */
    public static int requestConfigScreen() {
        openRequested = true;
        return 1;
    }

    public static void onEndClientTick(Minecraft client) {
        // First, as it was when the picker had a tick hook of its own registered before this one.
        MobStackerRegionPicker.onClientTick(client);
        MobStackerClientNetworking.tickJoin(client);
        while (OPEN_CONFIG.consumeClick()) {
            client.setScreen(new MobStackerConfigScreen(client.screen));
        }
        while (TOGGLE_OVERLAY.consumeClick()) {
            boolean on = MobStackerRegionOverlay.toggleAll();
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable(
                        on ? "message.mobstacker.overlay_on" : "message.mobstacker.overlay_off"), true);
            }
        }
        while (TOGGLE_XRAY.consumeClick()) {
            String message;
            if (!MobStackerRegionOverlay.throughWallsAllowed()) {
                message = "message.mobstacker.xray_not_allowed";
            } else {
                message = MobStackerRegionOverlay.toggleThroughWalls()
                        ? "message.mobstacker.xray_on" : "message.mobstacker.xray_off";
            }
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable(message), true);
            }
        }
        if (openRequested) {
            openRequested = false;
            client.setScreen(new MobStackerConfigScreen(null));
        }
    }
}
