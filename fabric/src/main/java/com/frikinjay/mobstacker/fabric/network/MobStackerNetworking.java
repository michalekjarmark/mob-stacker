package com.frikinjay.mobstacker.fabric.network;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import com.frikinjay.mobstacker.config.RegionEdit;
import com.frikinjay.mobstacker.config.StackRegion;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * Server side of the config-sync protocol used by the in-game GUI to edit a dedicated (or LAN)
 * server's config over the network. This class contains no client-only references, so it is safe to
 * load on a dedicated server; it is registered from {@code MobStackerFabric#onInitialize}.
 * <p>
 * The channels are <em>optional</em>: Fabric only delivers our packets to clients that registered
 * these receivers (checked via {@link ServerPlayNetworking#canSend}). A vanilla client — or any
 * client without the mod — is never sent anything and never disconnected, so the mod stays fully
 * server-side. Edits are gated on operator permission ({@value #EDIT_PERMISSION_LEVEL}) on the
 * server, exactly like the {@code /mobstacker} commands, so a client can never change more than the
 * player is allowed to from chat.
 */
public final class MobStackerNetworking {
    /** C2S: client asks for a full snapshot of the server's live config (sent when the GUI opens). */
    public static final ResourceLocation REQUEST = new ResourceLocation(MobStacker.MOD_ID, "cfg_request");
    /** C2S: client asks to change one setting ({@code String id}, {@code String rawValue}). */
    public static final ResourceLocation EDIT = new ResourceLocation(MobStacker.MOD_ID, "cfg_edit");
    /** S2C: full snapshot of every setting's current value, plus an authorization flag and a status line. */
    public static final ResourceLocation SYNC = new ResourceLocation(MobStacker.MOD_ID, "cfg_sync");

    /**
     * Prefix that turns an edit into a per-region override: {@code region:<name>:<setting>}. Reusing
     * the existing edit packet this way keeps the wire format unchanged (still two strings), so a
     * client and server on different versions of the mod still understand each other's global edits.
     */
    public static final String REGION_PREFIX = "region:";
    /** Pseudo-setting naming a region's overlap priority rather than one of its settings. */
    public static final String REGION_PRIORITY = "@priority";
    /**
     * Pseudo-setting carrying a whole region shape ({@link RegionEdit.Definition}) instead of one of
     * its settings, so the GUI can draw a new region or redraw an existing one. The region named in
     * the path is created when it does not exist yet.
     */
    public static final String REGION_DEFINITION = "@region";
    /** Pseudo-setting that removes the named region, with everything it carried. */
    public static final String REGION_DELETE = "@delete";

    /** Operator level required to change settings, matching the {@code /mobstacker} command tree. */
    private static final int EDIT_PERMISSION_LEVEL = 2;

    private MobStackerNetworking() {
    }

    public static void registerServer() {
        // A client opened the GUI and wants the server's current config.
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, responseSender) ->
                server.execute(() -> sendSync(player, "")));

        // A client changed a setting in the GUI. Read on the network thread, act on the main thread.
        ServerPlayNetworking.registerGlobalReceiver(EDIT, (server, player, handler, buf, responseSender) -> {
            String id = buf.readUtf();
            String raw = buf.readUtf();
            server.execute(() -> handleEdit(player, id, raw));
        });
    }

    private static void handleEdit(ServerPlayer player, String id, String raw) {
        // Re-check permission on the server for every edit; never trust the client's own gate.
        if (!player.hasPermissions(EDIT_PERMISSION_LEVEL)) {
            sendSync(player, "You are not allowed to change the config.");
            return;
        }

        if (id.startsWith(REGION_PREFIX)) {
            handleRegionEdit(player, id.substring(REGION_PREFIX.length()), raw);
            return;
        }

        ConfigOption option = MobStackerSettings.byId(id);
        if (option == null) {
            sendSync(player, "Unknown setting: " + id);
            return;
        }

        ConfigOption.Result result = option.apply(raw);
        String status;
        switch (result.status) {
            case CHANGED -> {
                // Persist immediately so a GUI edit survives a restart (unlike a live-only tweak).
                MobStacker.config.save();
                status = "Set " + option.id() + ": " + result.oldValue + " -> " + result.newValue
                        + (result.message != null ? " (" + result.message + ")" : "");
            }
            case UNCHANGED -> status = option.id() + " is already " + result.oldValue;
            default -> status = result.message;
        }
        // Echo the authoritative config back so the client corrects any optimistic/rejected value.
        sendSync(player, status);
    }

    /**
     * Applies one per-region change: a setting override, its removal (an empty value), or the
     * region's priority. Values are validated exactly as a global edit would be.
     */
    private static void handleRegionEdit(ServerPlayer player, String path, String raw) {
        int split = path.lastIndexOf(':');
        if (split <= 0 || split == path.length() - 1) {
            sendSync(player, "Malformed region edit: " + path);
            return;
        }
        String regionName = path.substring(0, split);
        String settingId = path.substring(split + 1);

        if (REGION_DEFINITION.equals(settingId)) {
            RegionEdit.Result result = RegionEdit.apply(regionName, RegionEdit.Definition.decode(raw));
            sendSync(player, result.message());
            return;
        }

        if (REGION_DELETE.equals(settingId)) {
            sendSync(player, RegionEdit.delete(regionName).message());
            return;
        }

        StackRegion region = MobStacker.config.getRegion(regionName);
        if (region == null) {
            sendSync(player, "Unknown region: " + regionName);
            return;
        }

        if (REGION_PRIORITY.equals(settingId)) {
            try {
                region.setPriority(Integer.parseInt(raw.trim()));
            } catch (NumberFormatException e) {
                sendSync(player, "Priority must be a whole number");
                return;
            }
            MobStacker.config.save();
            sendSync(player, regionName + ": priority " + region.getPriority());
            return;
        }

        ConfigOption option = MobStackerSettings.byId(settingId);
        if (option == null || !MobStackerSettings.isRegionOverridable(option.id())) {
            sendSync(player, "'" + settingId + "' cannot differ per region");
            return;
        }

        if (raw.isEmpty()) {
            region.clearSetting(option.id());
            MobStacker.config.save();
            sendSync(player, regionName + ": " + option.id() + " follows the global config");
            return;
        }

        try {
            String canonical = option.canonicalize(raw);
            // Resolved against the region, so a region that enables the setting another one depends
            // on may use it, exactly as the game resolves them at the mob.
            String problem = MobStackerSettings.regionEditProblem(option, region, canonical);
            if (problem != null) {
                sendSync(player, problem);
                return;
            }
            region.setSetting(option.id(), canonical);
            MobStacker.config.save();
            sendSync(player, regionName + ": " + option.id() + " = " + canonical);
        } catch (IllegalArgumentException e) {
            sendSync(player, e.getMessage());
        }
    }

    /**
     * Sends the full config snapshot to one player, but only if their client speaks our protocol.
     * The status line carries a short human-readable result of the last edit (or {@code ""}).
     */
    public static void sendSync(ServerPlayer player, String status) {
        if (!ServerPlayNetworking.canSend(player, SYNC)) {
            return; // vanilla / non-mod client: send nothing, stay invisible to it
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(player.hasPermissions(EDIT_PERMISSION_LEVEL));
        buf.writeUtf(status == null ? "" : status);
        List<ConfigOption> options = MobStackerSettings.all();
        buf.writeVarInt(options.size());
        for (ConfigOption option : options) {
            buf.writeUtf(option.id());
            // As stored, not as forced: the client re-applies a lock in whatever scope it is showing,
            // so a region that turns the forcing setting off is not handed the world's locked value.
            buf.writeUtf(option.storedValue());
        }

        // Regions follow the settings, so the GUI can edit each region's own values too.
        List<StackRegion> regions = MobStacker.config.getRegions();
        buf.writeVarInt(regions.size());
        for (StackRegion region : regions) {
            buf.writeUtf(region.getName());
            buf.writeUtf(region.getType().name());
            buf.writeUtf(region.getDimension() == null ? "" : region.getDimension());
            // The corners themselves, not a pretty string: the region editor puts them straight into
            // its coordinate boxes, and the client formats the label the same way the server would.
            buf.writeInt(region.getMinX());
            buf.writeInt(region.getMinY());
            buf.writeInt(region.getMinZ());
            buf.writeInt(region.getMaxX());
            buf.writeInt(region.getMaxY());
            buf.writeInt(region.getMaxZ());
            buf.writeInt(region.getPriority());
            Map<String, String> overrides = region.getSettings();
            buf.writeVarInt(overrides.size());
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }
        ServerPlayNetworking.send(player, SYNC, buf);
    }
}
