package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /goodebug flames}: pins a looping Flames jet at the glove, down the
 * look, so the operator can walk around it and judge it from any side, the
 * side view among them; {@code /goodebug flames off} takes it away. The
 * operator's ask: a debug function to show the animation from the side.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FireDebugCommand {

    private static final String ROOT = "goodebug";
    private static final String FLAMES = "flames";
    private static final String OFF = "off";
    private static final String PINNED = "Pinned a Flames jet at the glove; /goodebug flames off removes it.";
    private static final String NO_PLAYER = "No player to pin a Flames jet at.";
    private static final String REMOVED = "Removed the pinned Flames jet.";

    private FireDebugCommand() {
    }

    /**
     * Registers {@code /goodebug flames} on the client.
     *
     * @param event the client command registration event
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ROOT)
                .then(Commands.literal(FLAMES)
                        .executes(FireDebugCommand::pin)
                        .then(Commands.literal(OFF).executes(FireDebugCommand::unpin))));
    }

    private static int pin(CommandContext<CommandSourceStack> context) {
        boolean pinnedOne = FireStream.pinAtGlove();
        context.getSource().sendSystemMessage(Component.literal(pinnedOne ? PINNED : NO_PLAYER));
        return pinnedOne ? 1 : 0;
    }

    private static int unpin(CommandContext<CommandSourceStack> context) {
        FireStream.unpin();
        context.getSource().sendSystemMessage(Component.literal(REMOVED));
        return 1;
    }
}
