package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.fabric.network.MobStackerNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client side of the config-sync protocol. Holds the last snapshot the server sent and exposes the
 * small verbs the GUI needs: whether the current server speaks our protocol, whether this player may
 * edit, the snapshot values, and the request/edit sends.
 * <p>
 * Client-only: nothing here is referenced from common or the dedicated-server entrypoint. When a
 * config snapshot arrives it updates the cache and, if the config screen is open, tells it to
 * refresh so button states track the authoritative values.
 */
public final class MobStackerClientNetworking {
    // Latest snapshot from the server (id -> canonical value string). Kept in registry order.
    private static final Map<String, String> SNAPSHOT = new LinkedHashMap<>();
    private static boolean authorized;
    private static String status = "";

    private MobStackerClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MobStackerNetworking.SYNC, (client, handler, buf, responseSender) -> {
            boolean incomingAuth = buf.readBoolean();
            String incomingStatus = buf.readUtf();
            int count = buf.readVarInt();
            Map<String, String> incoming = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf();
                String value = buf.readUtf();
                incoming.put(id, value);
            }
            client.execute(() -> {
                authorized = incomingAuth;
                status = incomingStatus;
                SNAPSHOT.clear();
                SNAPSHOT.putAll(incoming);
                if (client.screen instanceof MobStackerConfigScreen screen) {
                    screen.onConfigSynced();
                }
            });
        });

        // Don't carry one server's config over to the next connection.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    /** True when the connected server registered our protocol, i.e. it has the mod installed. */
    public static boolean serverHasMod() {
        return ClientPlayNetworking.canSend(MobStackerNetworking.EDIT);
    }

    /** True when the server told us this player is an operator (may edit). */
    public static boolean authorized() {
        return authorized;
    }

    /** True once at least one snapshot has arrived from the server. */
    public static boolean hasSnapshot() {
        return !SNAPSHOT.isEmpty();
    }

    /** Short human-readable result of the last edit, or {@code ""}. */
    public static String status() {
        return status;
    }

    /** The last value the server reported for {@code id}, or {@code null} if not yet known. */
    public static String value(String id) {
        return SNAPSHOT.get(id);
    }

    /** Optimistically remember a value the user just set, so a rebuild before the echo keeps it. */
    public static void rememberLocal(String id, String value) {
        SNAPSHOT.put(id, value);
    }

    public static void requestSync() {
        if (serverHasMod()) {
            ClientPlayNetworking.send(MobStackerNetworking.REQUEST, PacketByteBufs.create());
        }
    }

    public static void sendEdit(String id, String raw) {
        if (!serverHasMod()) {
            return;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(id);
        buf.writeUtf(raw);
        ClientPlayNetworking.send(MobStackerNetworking.EDIT, buf);
    }

    private static void clear() {
        SNAPSHOT.clear();
        authorized = false;
        status = "";
    }
}
