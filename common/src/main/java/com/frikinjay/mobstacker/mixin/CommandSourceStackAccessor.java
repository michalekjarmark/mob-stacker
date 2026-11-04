package com.frikinjay.mobstacker.mixin;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Who a command's answer goes back to: the player who typed it, or a command block, a function or the
 * console running it - which the stack's entity cannot tell apart, since {@code execute as <player>}
 * from a command block has the player as its entity too. Read by {@code CommandFeedback}.
 */
@Mixin(CommandSourceStack.class)
public interface CommandSourceStackAccessor {

    @Accessor("source")
    CommandSource mobstacker$source();
}
