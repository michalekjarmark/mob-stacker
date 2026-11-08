package com.frikinjay.mobstacker.mixin.client;

import com.frikinjay.mobstacker.command.CommandFeedback;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A chat panel the server draws again takes the old one's place (1.12.0, round 12): when a line that
 * starts a panel arrives, the earlier lines of the same panel leave the chat, so clicking "[on]" in
 * the region list leaves one list - showing "[off]" - instead of two. The panels and their marks are
 * {@code CommandFeedback}'s; a message without a mark is never touched.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow @Final private List<GuiMessage> allMessages;

    @Shadow
    public abstract void rescaleChat();

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void mobstacker$replacePanel(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (!CommandFeedback.startsPanel(message)) {
            return;
        }
        String panel = CommandFeedback.panelOf(message);
        if (allMessages.removeIf(old -> panel.equals(CommandFeedback.panelOf(old.content())))) {
            // The lines on screen are made from the messages; vanilla's own rebuild after a resize.
            rescaleChat();
        }
    }
}
