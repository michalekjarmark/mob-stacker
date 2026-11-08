package com.frikinjay.mobstacker.client;

import com.frikinjay.mobstacker.config.StackLabelFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The stack labels the server told this game about (1.12.0, ROADMAP #46/#56), drawn as a line of
 * their own above the mob's name by {@code mixin.client.EntityRendererMixin}. The mob's own name is
 * never touched: a stack of sheep called "jeb_" shows "jeb_" - rainbow and all - and "Sheep x12"
 * above it.
 *
 * <p>Only a game with the mod gets these; one without it is sent the count in the mob's name instead
 * (the server's {@code StackLabels}). Client thread only.
 */
public final class MobStackerClientLabels {

    private record Label(int count, String format, ChatFormatting color, boolean always) {
    }

    private static final Map<Integer, Label> LABELS = new HashMap<>();

    private MobStackerClientLabels() {
    }

    /** A label arrived: read on the network thread, kept on the client thread. */
    public static void onLabel(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int count = buf.readVarInt();
        String format = buf.readUtf();
        ChatFormatting color = ChatFormatting.getById(buf.readVarInt());
        boolean always = buf.readBoolean();
        Minecraft.getInstance().execute(() -> {
            if (count <= 1) {
                LABELS.remove(id);
            } else {
                LABELS.put(id, new Label(count, format, color == null ? ChatFormatting.WHITE : color, always));
            }
        });
    }

    /** The mob left this game's world. */
    public static void forget(int id) {
        LABELS.remove(id);
    }

    /** A new world or server: nothing of the last one applies. */
    public static void clear() {
        LABELS.clear();
    }

    /**
     * The label to draw over this entity now, or null: when the server sent one, and the stack is
     * looked at or its labels are always shown - and only where vanilla would draw a name at all.
     */
    public static Component labelFor(Entity entity, Entity lookedAt) {
        if (LABELS.isEmpty()) {
            return null;
        }
        Label label = LABELS.get(entity.getId());
        if (label == null || (!label.always() && entity != lookedAt)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!Minecraft.renderNames() || entity == minecraft.getCameraEntity() || entity.isVehicle()
                || (minecraft.player != null && entity.isInvisibleTo(minecraft.player))) {
            return null;
        }
        return StackLabelFormat.build(label.format(), entity.getType().getDescription(), label.count())
                .withStyle(label.color());
    }
}
