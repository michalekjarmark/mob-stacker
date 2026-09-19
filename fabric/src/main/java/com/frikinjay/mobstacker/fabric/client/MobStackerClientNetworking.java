package com.frikinjay.mobstacker.fabric.client;

import com.frikinjay.mobstacker.config.StackColor;
import com.frikinjay.mobstacker.fabric.network.MobStackerNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    // The server's regions, with whatever settings each one overrides.
    private static final List<RegionInfo> REGIONS = new ArrayList<>();
    private static boolean authorized;
    private static String status = "";

    /** One region as the server described it, for the region editor screen. */
    public record RegionInfo(String name, String type, String dimension,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             int priority, String color, Map<String, String> settings) {
        /** The same corner text the server's own {@code /mobstacker region list} prints. */
        public String bounds() {
            return "[" + minX + ", " + minY + ", " + minZ + "] -> [" + maxX + ", " + maxY + ", " + maxZ + "]";
        }

        /** The colour the player picked, or null when they have not picked one. */
        public StackColor chosenColor() {
            if (color == null || color.isEmpty()) {
                return null;
            }
            try {
                return StackColor.valueOf(color);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /**
         * What the overlay draws this region in — the same allow/deny fallback
         * {@code StackRegion.effectiveColor} applies, so both ends agree.
         */
        public StackColor overlayColor() {
            StackColor chosen = chosenColor();
            if (chosen != null) {
                return chosen;
            }
            return "DENY".equalsIgnoreCase(type) ? StackColor.RED : StackColor.GREEN;
        }
    }

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
            int regionCount = buf.readVarInt();
            List<RegionInfo> incomingRegions = new ArrayList<>();
            for (int i = 0; i < regionCount; i++) {
                String name = buf.readUtf();
                String type = buf.readUtf();
                String dimension = buf.readUtf();
                int minX = buf.readInt();
                int minY = buf.readInt();
                int minZ = buf.readInt();
                int maxX = buf.readInt();
                int maxY = buf.readInt();
                int maxZ = buf.readInt();
                int priority = buf.readInt();
                String color = buf.readUtf();
                int overrideCount = buf.readVarInt();
                Map<String, String> overrides = new LinkedHashMap<>();
                for (int o = 0; o < overrideCount; o++) {
                    overrides.put(buf.readUtf(), buf.readUtf());
                }
                incomingRegions.add(new RegionInfo(name, type, dimension,
                        minX, minY, minZ, maxX, maxY, maxZ, priority, color, overrides));
            }
            client.execute(() -> {
                authorized = incomingAuth;
                status = incomingStatus;
                SNAPSHOT.clear();
                SNAPSHOT.putAll(incoming);
                REGIONS.clear();
                REGIONS.addAll(incomingRegions);
                if (client.screen instanceof MobStackerConfigScreen screen) {
                    screen.onConfigSynced();
                } else if (client.screen instanceof MobStackerRegionScreen screen) {
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

    /** The server's regions as of the last snapshot. */
    public static List<RegionInfo> regions() {
        return Collections.unmodifiableList(REGIONS);
    }

    /** The value {@code region} gives {@code id}, or null when it follows the global config. */
    public static String regionValue(String region, String id) {
        for (RegionInfo info : REGIONS) {
            if (info.name().equals(region)) {
                return info.settings().get(id);
            }
        }
        return null;
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

    /** Optimistically apply a region edit locally; the server echo confirms or corrects it. */
    public static void rememberRegionLocal(String region, String id, String value) {
        for (int i = 0; i < REGIONS.size(); i++) {
            RegionInfo info = REGIONS.get(i);
            if (!info.name().equals(region)) {
                continue;
            }
            Map<String, String> settings = new LinkedHashMap<>(info.settings());
            if (value == null || value.isEmpty()) {
                settings.remove(id);
            } else {
                settings.put(id, value);
            }
            REGIONS.set(i, new RegionInfo(info.name(), info.type(), info.dimension(),
                    info.minX(), info.minY(), info.minZ(), info.maxX(), info.maxY(), info.maxZ(),
                    info.priority(), info.color(), settings));
            return;
        }
    }

    private static void clear() {
        SNAPSHOT.clear();
        REGIONS.clear();
        authorized = false;
        status = "";
    }
}
