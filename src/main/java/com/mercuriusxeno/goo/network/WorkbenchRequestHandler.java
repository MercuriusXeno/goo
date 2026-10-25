package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.command.ValuesManagers;
import com.mercuriusxeno.goo.workbench.WorkbenchReply;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Server-side handler for values manager requests: reads the sending player as a {@link WorkbenchRequests.Sender}
 * and answers through the server's workbench operations (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class WorkbenchRequestHandler {
    private static final int SERVER_ERROR = 500;
    // The level /goo workbench requires, the one every /goo subcommand reads.
    private static final Permission MANAGES_VALUES = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);

    private WorkbenchRequestHandler() {
    }

    /**
     * Handles one request part on the server thread.
     *
     * @param payload the request part
     * @param context the network context
     */
    public static void handle(WorkbenchRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.level().getServer();
            WorkbenchRequests.Sender sender = new WorkbenchRequests.Sender() {
                @Override
                public String key() {
                    return player.getStringUUID();
                }

                @Override
                public boolean mayManageValues() {
                    return player.permissions().hasPermission(MANAGES_VALUES);
                }

                @Override
                public void send(WorkbenchReplyPayload part) {
                    server.execute(() -> PacketDistributor.sendToPlayer(player, part));
                }
            };
            try {
                ValuesManagers.requestsFor(server).accept(sender, payload, (operation, text, progress) ->
                        answer(server, operation, text, progress));
            } catch (IOException e) {
                WorkbenchReplyPayload.parts(payload.requestId(), SERVER_ERROR, String.valueOf(e.getMessage()))
                        .forEach(sender::send);
            }
        });
    }

    private static WorkbenchRequests.Answer answer(MinecraftServer server, String operation, String text,
                                                   Consumer<String> progress) {
        try {
            WorkbenchReply reply = ValuesManagers.operationsFor(server).answer(operation, text, progress);
            return new WorkbenchRequests.Answer(reply.status(), reply.text());
        } catch (IOException e) {
            return new WorkbenchRequests.Answer(SERVER_ERROR, String.valueOf(e.getMessage()));
        }
    }
}
