package com.frikinjay.mobstacker.fabric.network;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

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
            buf.writeUtf(option.currentValue());
        }
        ServerPlayNetworking.send(player, SYNC, buf);
    }
}
