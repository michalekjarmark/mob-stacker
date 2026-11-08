package com.frikinjay.mobstacker;

import com.frikinjay.mobstacker.mixin.EntityAccessor;
import com.frikinjay.mobstacker.network.MobStackerNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * A stack's label - "Cow x12" - since 1.12.0 (ROADMAP #46 + #56): <b>never stored on the mob</b>, and
 * drawn by each player's own game the way it can.
 *
 * <p>The mob keeps its own name, a player's or none, so an uninstalled mod leaves no label behind and
 * vanilla's "jeb_" and "Dinnerbone" work on a stack. What a player sees is decided per player, in
 * the packets that tell their game about the mob's name:
 * <ul>
 *   <li>a game with the mod gets the label on its own channel ({@link #CHANNEL}) and draws it as a
 *       line of its own above the mob's name (the client's {@code MobStackerClientLabels}), in its
 *       own language, coloured by the server's settings for where the mob stands;</li>
 *   <li>a game without it gets the label put into the mob's name, as before 1.12.0 - in that
 *       player's packets only. Its kind is sent as a translatable name, so it is in the player's
 *       language too.</li>
 * </ul>
 * Packets are personalised in {@code ServerGamePacketListenerImplMixin} (entity data with a name in
 * it) and {@code ServerEntityMixin} (what a player is told when the mob comes into view).
 */
public final class StackLabels {
    /** S2C: one mob's label for a game that draws its own: id, count (0 = none), format, colour, always. */
    public static final ResourceLocation CHANNEL = new ResourceLocation(MobStacker.MOD_ID, "stack_label");

    /** How long after joining a game may still say it speaks the label channel; after that it does not. */
    private static final int JOIN_WATCH = 200;

    /** What each mob's label was last sent as, so the periodic display update sends nothing new. */
    private static final Map<Mob, String> LAST = new WeakHashMap<>();
    /** Marks a player whose labels were sent again already. */
    private static final int DONE = Integer.MAX_VALUE;
    /** Players online, and the tick to send their labels again at (DONE once that happened). */
    private static final Map<UUID, Integer> JOINED = new HashMap<>();

    private StackLabels() {
    }

    /** Whether this mob shows a label at all: a stack of two or more, with showStackLabels on where it is. */
    public static boolean shows(Mob mob) {
        return MobStacker.getStackSize(mob) > 1 && MobStacker.getShowStackLabels(mob);
    }

    /** Whether this player's game draws the label itself, as a line of its own. */
    private static boolean drawsOwn(ServerPlayer player, Mob mob) {
        return MobStackerNetworking.canSend(player, CHANNEL) && MobStacker.getSeparateStackLabel(mob);
    }

    /**
     * The label as it goes into the name, for a game that does not draw its own: the mob's own name -
     * a player's, or one another mod or a command gave it - with the count after it, or the kind of
     * mob in the stack's colour.
     */
    public static Component nameLabel(Mob mob) {
        int count = MobStacker.getStackSize(mob);
        Component own = mob.getCustomName();
        if (own != null) {
            return own.copy().append(" x" + count);
        }
        return MobStacker.getLocalizedEntityName(mob.getType()).copy().append(" x" + count)
                .withStyle(MobStacker.stackNameColor(mob, count));
    }

    /** The mob's real name and its "always shown" flag, as entity data. */
    private static List<SynchedEntityData.DataValue<?>> realNameValues(Mob mob) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(2);
        values.add(SynchedEntityData.DataValue.create(EntityAccessor.mobstacker$customNameData(),
                Optional.ofNullable(mob.getCustomName())));
        values.add(SynchedEntityData.DataValue.create(EntityAccessor.mobstacker$customNameVisibleData(),
                mob.isCustomNameVisible()));
        return values;
    }

    /** The name and its "always shown" flag a game without the mod gets: the label in the name. */
    private static List<SynchedEntityData.DataValue<?>> labelNameValues(Mob mob) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(2);
        values.add(SynchedEntityData.DataValue.create(EntityAccessor.mobstacker$customNameData(),
                Optional.of(nameLabel(mob))));
        values.add(SynchedEntityData.DataValue.create(EntityAccessor.mobstacker$customNameVisibleData(),
                mob.isCustomNameVisible() || MobStacker.getAlwaysShowStackLabels(mob)));
        return values;
    }

    /** The label for a game that draws its own; count 0 takes it away. */
    private static Packet<ClientGamePacketListener> labelPacket(Mob mob, boolean show) {
        FriendlyByteBuf buf = MobStackerNetworking.newBuffer();
        buf.writeVarInt(mob.getId());
        int count = MobStacker.getStackSize(mob);
        buf.writeVarInt(show ? count : 0);
        buf.writeUtf(MobStacker.getStackLabelFormat(mob));
        buf.writeVarInt(MobStacker.stackNameColor(mob, count).getId());
        buf.writeBoolean(MobStacker.getAlwaysShowStackLabels(mob));
        return new ClientboundCustomPayloadPacket(CHANNEL, buf);
    }

    private static boolean carriesName(ClientboundSetEntityDataPacket packet) {
        int name = EntityAccessor.mobstacker$customNameData().getId();
        int visible = EntityAccessor.mobstacker$customNameVisibleData().getId();
        for (SynchedEntityData.DataValue<?> value : packet.packedItems()) {
            if (value.id() == name || value.id() == visible) {
                return true;
            }
        }
        return false;
    }

    /**
     * One outgoing entity-data packet, made right for this player. Only a packet that carries the
     * name is touched: for a game without the mod the label goes into the name, and a game with it is
     * sent the label on the side. A new packet is made every time - the same packet object goes to
     * every player watching, and in singleplayer it is never even serialised.
     */
    public static Packet<?> personalise(ServerPlayer player, Packet<?> packet) {
        if (!(packet instanceof ClientboundSetEntityDataPacket data) || !player.server.isSameThread()
                || !carriesName(data)) {
            return packet;
        }
        Entity entity = player.serverLevel().getEntity(data.id());
        if (!(entity instanceof Mob mob)) {
            return packet;
        }
        return personalise(player, mob, data);
    }

    /** The same, for a mob already found (and for the self-test, with a mob made for it). */
    public static Packet<?> personalise(ServerPlayer player, Mob mob, ClientboundSetEntityDataPacket data) {
        boolean show = shows(mob);
        if (MobStackerNetworking.canSend(player, CHANNEL)) {
            boolean own = show && MobStacker.getSeparateStackLabel(mob);
            player.connection.send(labelPacket(mob, own));
            if (own || !show) {
                return data;
            }
        } else if (!show) {
            return data;
        }
        return withLabelInName(mob, data);
    }

    /** A copy of the packet with the name and its flag replaced by the label (added when absent). */
    public static ClientboundSetEntityDataPacket withLabelInName(Mob mob, ClientboundSetEntityDataPacket data) {
        List<SynchedEntityData.DataValue<?>> label = labelNameValues(mob);
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(data.packedItems().size() + 2);
        for (SynchedEntityData.DataValue<?> value : data.packedItems()) {
            if (value.id() != label.get(0).id() && value.id() != label.get(1).id()) {
                out.add(value);
            }
        }
        out.addAll(label);
        return new ClientboundSetEntityDataPacket(data.id(), out);
    }

    /**
     * What a player is told about the mob when it comes into view, after vanilla's own pairing data:
     * the label, in whichever of the two forms their game takes.
     */
    public static void addPairing(ServerPlayer player, Mob mob, Consumer<Packet<ClientGamePacketListener>> out) {
        if (!shows(mob)) {
            return;
        }
        if (drawsOwn(player, mob)) {
            out.accept(labelPacket(mob, true));
        } else {
            out.accept(new ClientboundSetEntityDataPacket(mob.getId(), labelNameValues(mob)));
        }
    }

    /**
     * Tells every player watching the mob about its label again, if it changed since the last time:
     * its count, its colour, where it stands, a setting. Called from every display update, which runs
     * often, so an unchanged label sends nothing.
     */
    public static void refresh(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        String key = shows(mob)
                ? MobStacker.getStackSize(mob) + "|" + MobStacker.stackNameColor(mob, MobStacker.getStackSize(mob))
                + "|" + MobStacker.getStackLabelFormat(mob) + "|" + MobStacker.getAlwaysShowStackLabels(mob)
                + "|" + MobStacker.getSeparateStackLabel(mob) + "|" + mob.getCustomName() + "|" + mob.isCustomNameVisible()
                : "none|" + mob.getCustomName() + "|" + mob.isCustomNameVisible();
        String before = LAST.put(mob, key);
        if (key.equals(before) || (before == null && !shows(mob))) {
            return;
        }
        // The real name; every player's own connection turns it into what that player should see.
        level.getChunkSource().broadcast(mob, new ClientboundSetEntityDataPacket(mob.getId(), realNameValues(mob)));
    }

    /**
     * Server tick: a player who just joined is sent the labels around them again as soon as their game
     * says it has the mod. On Fabric a game says which channels it speaks only after it has joined, by
     * when the mobs around it were already sent with the label in their names - which a game with the
     * mod would show twice. On Forge it is known at login, and this sends the same labels once more.
     */
    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            JOINED.putIfAbsent(player.getUUID(), now + JOIN_WATCH);
        }
        JOINED.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true; // logged out: the next login starts over
            }
            if (entry.getValue() == DONE) {
                return false;
            }
            if (MobStackerNetworking.canSend(player, CHANNEL)) {
                // The moment the game says it has the mod: everything it was shown with the label in
                // the name is sent again, as the label on its own.
                resendAround(player);
                entry.setValue(DONE);
            } else if (now >= entry.getValue()) {
                entry.setValue(DONE); // a game without the mod: what it was sent was right
            }
            return false;
        });
    }

    /** Sends one player every stacked mob's real name around them, which their connection personalises. */
    private static void resendAround(ServerPlayer player) {
        double range = player.server.getPlayerList().getViewDistance() * 16.0 + 16.0;
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
                candidate -> MobStacker.getStackSize(candidate) > 1)) {
            player.connection.send(new ClientboundSetEntityDataPacket(mob.getId(), realNameValues(mob)));
        }
    }

    /** The server stopped: nothing about this one's players or mobs applies to the next. */
    public static void onServerStopped() {
        JOINED.clear();
        LAST.clear();
    }
}
