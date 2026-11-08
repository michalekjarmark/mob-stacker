package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.command.MobStackerCommands;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers MobStacker's commands when the server builds its command tree. This replaces the
 * command hook the Almanac library used to provide, so the mod no longer depends on it.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mobstacker$registerCommands(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
        MobStackerCommands.register(this.dispatcher);
    }
}
