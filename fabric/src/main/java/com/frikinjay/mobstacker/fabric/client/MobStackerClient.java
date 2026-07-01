package com.frikinjay.mobstacker.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
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
    // Set by the client command; the screen is opened on the next tick so the chat screen (which
    // closes right after a command runs) doesn't immediately override it.
    private static boolean openRequested;

    @Override
    public void onInitializeClient() {
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mobstacker.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default; the player assigns it in Controls
                "key.categories.mobstacker"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.setScreen(new MobStackerConfigScreen(client.screen));
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
