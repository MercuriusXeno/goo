package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.workbench.ManagerConnection;
import com.mercuriusxeno.goo.client.workbench.RegistryEntrySource;
import com.mercuriusxeno.goo.client.workbench.ValuesManagerScreen;
import com.mercuriusxeno.goo.network.OpenValuesManagerPayload;
import com.mercuriusxeno.goo.network.WorkbenchReplyPayload;
import com.mercuriusxeno.goo.network.WorkbenchRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handlers of the values manager's payloads: the open payload opens the screen, and each reply part
 * reaches the one connection the screen requests through, on the client thread
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class ValuesManagerHandlers {
    private static final ManagerConnection CONNECTION = new ManagerConnection(ValuesManagerHandlers::sendToServer);

    private ValuesManagerHandlers() {
    }

    /**
     * @param payload the open payload
     * @param context the network context
     */
    public static void handleOpen(OpenValuesManagerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ValuesManagerScreen(CONNECTION,
                new RegistryEntrySource())));
    }

    /**
     * @param payload one reply part
     * @param context the network context
     */
    public static void handleReply(WorkbenchReplyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CONNECTION.accept(payload));
    }

    private static void sendToServer(WorkbenchRequestPayload part) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(part));
        }
    }
}
