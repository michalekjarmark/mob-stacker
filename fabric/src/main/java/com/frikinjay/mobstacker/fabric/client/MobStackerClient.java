package com.frikinjay.mobstacker.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only entrypoint. Everything here runs solely on the client, so a dedicated server never
 * loads these classes and the mod stays fully functional server-side without a client install.
 * <p>
 * Registers two ways to open the config GUI — an (unbound by default) key binding and the client
 * command {@code /mobstackerconfig}. The screen itself only edits settings in singleplayer / on the
 * LAN host for now; server-side editing over the network is a later phase.
 */
public final class MobStackerClient implements ClientModInitializer {
    private static KeyMapping openConfigKey;
    private static KeyMapping toggleOverlayKey;
    private static KeyMapping toggleXrayKey;
    // Set by the client command; the screen is opened on the next tick so the chat screen (which
    // closes right after a command runs) doesn't immediately override it.
    private static boolean openRequested;

    @Override
    public void onInitializeClient() {
        // Client side of the config-sync protocol (S2C snapshot receiver + disconnect cleanup).
        MobStackerClientNetworking.register();
        // In-world region boxes. Purely a client-side view of the world, so nothing here is sent
        // anywhere and a server never knows or cares whether a player has them switched on.
        MobStackerRegionOverlay.register();
        MobStackerRegionPicker.register();

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mobstacker.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default; the player assigns it in Controls
                "key.categories.mobstacker"));

        toggleOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mobstacker.toggle_region_overlay",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default, like the config key
                "key.categories.mobstacker"));

        toggleXrayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mobstacker.toggle_region_xray",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default, like the other two
                "key.categories.mobstacker"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.setScreen(new MobStackerConfigScreen(client.screen));
            }
            while (toggleOverlayKey.consumeClick()) {
                boolean on = MobStackerRegionOverlay.toggleAll();
                if (client.player != null) {
                    client.player.displayClientMessage(Component.translatable(
                            on ? "message.mobstacker.overlay_on" : "message.mobstacker.overlay_off"), true);
                }
            }
            while (toggleXrayKey.consumeClick()) {
                boolean on = MobStackerRegionOverlay.toggleThroughWalls();
                if (client.player != null) {
                    client.player.displayClientMessage(Component.translatable(
                            on ? "message.mobstacker.xray_on" : "message.mobstacker.xray_off"), true);
                }
            }
            if (openRequested) {
                openRequested = false;
                client.setScreen(new MobStackerConfigScreen(null));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("mobstackerconfig").executes(context -> {
                    // Open on the next tick (see openRequested), after the chat screen has closed.
                    openRequested = true;
                    return 1;
                })));
    }
}
