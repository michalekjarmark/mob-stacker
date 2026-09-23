package com.frikinjay.mobstacker.fabric.network;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.config.ConfigOption;
import com.frikinjay.mobstacker.config.MobListKind;
import com.frikinjay.mobstacker.config.MobLists;
import com.frikinjay.mobstacker.config.MobStackerSettings;
import com.frikinjay.mobstacker.config.RegionEdit;
import com.frikinjay.mobstacker.config.StackColor;
import com.frikinjay.mobstacker.config.StackRegion;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
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

    /** Pseudo-setting naming the colour the region's overlay is drawn in. Empty value = automatic. */
    public static final String REGION_COLOR = "@color";

    /** Pseudo-setting renaming the region; the value is the new name. */
    public static final String REGION_RENAME = "@rename";
    /**
     * Pseudo-setting carrying a whole region shape ({@link RegionEdit.Definition}) instead of one of
     * its settings, so the GUI can draw a new region or redraw an existing one. The region named in
     * the path is created when it does not exist yet.
     */
    public static final String REGION_DEFINITION = "@region";
    /**
     * The same shape, from the "New region…" screen rather than from "Edit area…". Separate from
     * {@link #REGION_DEFINITION} because only the sender knows which of the two it meant, and the
     * answer decides whether a name that is already taken is a reshape or a refusal.
     */
    public static final String REGION_CREATE = "@newregion";
    /** Pseudo-setting that removes the named region, with everything it carried. */
    public static final String REGION_DELETE = "@delete";

    /**
     * Pseudo-setting editing one mob list: {@code @list:<listId>:<add|remove|inherit>}, with the
     * entry itself in the packet's value rather than in the id. Entity ids contain a colon, and an
     * id that has to be split apart is exactly where a wire format starts guessing.
     */
    public static final String LIST_PREFIX = "@list:";

    /** Pseudo-setting setting one type's ceiling: {@code @maxstack:<entityId>}, empty value = unset. */
    public static final String MAXSTACK_PREFIX = "@maxstack:";

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

        if (id.startsWith(LIST_PREFIX)) {
            sendSync(player, applyListEdit(MobStacker.config, "globally",
                    id.substring(LIST_PREFIX.length()), raw));
            return;
        }

        if (id.startsWith(MAXSTACK_PREFIX)) {
            sendSync(player, applyMaxStack(null, "globally", id.substring(MAXSTACK_PREFIX.length()), raw));
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
        // The FIRST colon, not the last: a region name can never contain one (RegionEdit.nameProblem
        // allows letters, digits, _ . - only), while what follows it now can - @list:ignoredEntities:add
        // and @maxstack:minecraft:cow both do. Splitting from the right would tear those apart.
        int split = path.indexOf(':');
        if (split <= 0 || split == path.length() - 1) {
            sendSync(player, "Malformed region edit: " + path);
            return;
        }
        String regionName = path.substring(0, split);
        String settingId = path.substring(split + 1);

        if (REGION_DEFINITION.equals(settingId) || REGION_CREATE.equals(settingId)) {
            RegionEdit.Result result = RegionEdit.apply(regionName, RegionEdit.Definition.decode(raw),
                    REGION_CREATE.equals(settingId));
            sendSync(player, result.message());
            return;
        }

        if (REGION_RENAME.equals(settingId)) {
            sendSync(player, RegionEdit.rename(regionName, raw).message());
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

        if (settingId.startsWith(LIST_PREFIX)) {
            String message = applyListEdit(region, "in region '" + regionName + "'",
                    settingId.substring(LIST_PREFIX.length()), raw);
            sendSync(player, message);
            return;
        }

        if (settingId.startsWith(MAXSTACK_PREFIX)) {
            sendSync(player, applyMaxStack(region, "in region '" + regionName + "'",
                    settingId.substring(MAXSTACK_PREFIX.length()), raw));
            return;
        }

        if (REGION_COLOR.equals(settingId)) {
            String value = raw.trim();
            if (value.isEmpty()) {
                region.setColor(null);
            } else {
                try {
                    region.setColor(StackColor.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    sendSync(player, "Unknown colour: " + value);
                    return;
                }
            }
            MobStacker.config.save();
            sendSync(player, regionName + ": overlay colour " + region.effectiveColor().name().toLowerCase(Locale.ROOT));
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
     * Applies one mob-list edit to whichever holder asked for it, global or region.
     *
     * <p>Both scopes go through this one method for the same reason the command tree is built once
     * and hung in two places: the validation, the normalising and the wording of the answer are the
     * things that quietly come apart when there are two copies.
     *
     * @param spec {@code <listId>:<add|remove|inherit>}
     * @return the status line to echo back
     */
    private static String applyListEdit(MobLists.Holder holder, String scope, String spec, String raw) {
        int split = spec.lastIndexOf(':');
        if (split <= 0 || split == spec.length() - 1) {
            return "Malformed list edit: " + spec;
        }
        MobListKind kind = MobListKind.byId(spec.substring(0, split));
        if (kind == null) {
            return "Unknown mob list: " + spec.substring(0, split);
        }
        String op = spec.substring(split + 1);
        if ("inherit".equals(op)) {
            if (holder == MobStacker.config) {
                return "The global lists are what everything else inherits from";
            }
            holder.clearList(kind);
            MobStacker.config.save();
            return kind.id() + " " + scope + " now follows the global list";
        }
        if ("override".equals(op)) {
            if (holder == MobStacker.config) {
                return "The global lists have nothing to override";
            }
            // Seeded with what was being inherited, so taking a list over does not empty it.
            holder.setList(kind, MobStacker.config.getList(kind));
            MobStacker.config.save();
            return kind.id() + " " + scope + " is now this region's own";
        }

        String entry = MobLists.normalise(kind, raw);
        if (entry.isEmpty()) {
            return "Nothing to " + op;
        }
        boolean add = "add".equals(op);
        if (!add && !"remove".equals(op)) {
            return "Unknown list operation: " + op;
        }
        if (add) {
            String problem = MobLists.entryProblem(kind, entry);
            if (problem != null) {
                return problem;
            }
        }
        if (holder != MobStacker.config && !holder.hasList(kind)) {
            // Inheriting: an add here would start an override holding only this entry and drop the
            // global list it was showing. The screen greys the row out rather than send this, but a
            // packet is whatever the other end chose to put in it.
            return kind.id() + " " + scope + " follows the global list; give the region its own copy first";
        }
        boolean changed = add ? holder.addToList(kind, entry) : holder.removeFromList(kind, entry);
        if (!changed) {
            return "'" + entry + "' is " + (add ? "already on " : "not on ") + kind.id() + " " + scope;
        }
        MobStacker.config.save();
        return (add ? "Added '" : "Removed '") + entry + (add ? "' to " : "' from ") + kind.id() + " " + scope;
    }

    /** Applies one per-type ceiling, globally when {@code region} is null. Empty value clears it. */
    private static String applyMaxStack(StackRegion region, String scope, String entityId, String raw) {
        String id = entityId.trim();
        if (id.isEmpty()) {
            return "Malformed ceiling edit";
        }
        Integer size = null;
        if (!raw.trim().isEmpty()) {
            size = ConfigOption.parseSize(raw);
            if (size == null) {
                return "A stack ceiling must be a whole number, or '" + ConfigOption.MAX_KEYWORD + "'";
            }
            if (size < 1) {
                return "A stack ceiling must be at least 1";
            }
        }
        String canonical = MobLists.normaliseEntityId(id);
        if (size != null) {
            String problem = MobLists.entityProblem(canonical);
            if (problem != null) {
                return problem;
            }
        }
        if (region == null) {
            MobStacker.config.setMaxStackSize(canonical, size);
        } else {
            region.setMaxStackSize(canonical, size);
            MobStacker.config.save();
        }
        return size == null
                ? "'" + canonical + "' " + scope + " follows maxStackSize again"
                : "'" + canonical + "' stacks up to " + size + " " + scope;
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

        // The global mob lists, in MobListKind order so both ends agree without naming them.
        for (MobListKind kind : MobListKind.values()) {
            List<String> entries = MobStacker.config.getList(kind);
            buf.writeVarInt(entries.size());
            for (String entry : entries) {
                buf.writeUtf(entry);
            }
        }
        Map<String, Integer> globalCeilings = MobStacker.config.getMaxStackSizes();
        buf.writeVarInt(globalCeilings.size());
        for (Map.Entry<String, Integer> entry : globalCeilings.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
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
            // As they were given, not sorted - the client works out the box's extent for itself.
            for (int corner : region.getCorners()) {
                buf.writeInt(corner);
            }
            buf.writeInt(region.getPriority());
            // Empty means "nothing chosen"; the client applies the same allow/deny fallback the
            // server does, so both ends agree on what an uncoloured region looks like.
            buf.writeUtf(region.getColor() == null ? "" : region.getColor().name());
            Map<String, String> overrides = region.getSettings();
            buf.writeVarInt(overrides.size());
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
            // A flag per list, because a region with no list of its own is not the same as one with
            // an empty list: the first inherits, the second means "nothing".
            for (MobListKind kind : MobListKind.values()) {
                boolean own = region.hasList(kind);
                buf.writeBoolean(own);
                if (own) {
                    List<String> entries = region.getList(kind);
                    buf.writeVarInt(entries.size());
                    for (String entry : entries) {
                        buf.writeUtf(entry);
                    }
                }
            }
            Map<String, Integer> ceilings = region.getMaxStackSizes();
            buf.writeVarInt(ceilings.size());
            for (Map.Entry<String, Integer> entry : ceilings.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeVarInt(entry.getValue());
            }
        }
        // Whether boxes may be drawn through walls here (MobStacker.XRAY_PROPERTY). Last, so an
        // older client simply leaves it unread, and a newer client talking to an older server finds
        // nothing there and reads that as "not allowed".
        buf.writeBoolean(MobStacker.regionXrayAllowed());
        ServerPlayNetworking.send(player, SYNC, buf);
    }
}
