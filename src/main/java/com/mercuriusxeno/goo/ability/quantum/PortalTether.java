package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import java.util.UUID;

/**
 * Tethers a linked pair's chunks: while two portals stand linked, each one's
 * chunk is held loaded for the player who linked them, so the pair carries
 * travellers however far apart they stand and whichever side is visited.
 * The tickets are the player's, saved with the world, and fall when the
 * pair is unlinked.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PortalTether {

    /** The ticket controller every portal tether is held under. */
    public static final TicketController CONTROLLER =
            new TicketController(Identifier.fromNamespaceAndPath(Goo.MODID, "portal_tether"));

    private PortalTether() {
    }

    /**
     * Registers the tether's ticket controller.
     *
     * @param event the ticket controller registration event
     */
    @SubscribeEvent
    public static void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /**
     * Holds or lets go both chunks of a linked pair for its owner.
     *
     * @param server the server
     * @param owner  the player who linked the pair
     * @param link   the pair
     * @param hold   true to hold the chunks loaded, false to let them go
     */
    public static void tie(MinecraftServer server, UUID owner, QuantumPortals.Link link, boolean hold) {
        tie(server, owner, link.entry(), hold);
        tie(server, owner, link.exit(), hold);
    }

    private static void tie(MinecraftServer server, UUID owner, Portal portal, boolean hold) {
        ServerLevel level = server.getLevel(portal.block().dimension());
        if (level != null) {
            ChunkPos chunk = ChunkPos.containing(portal.block().pos());
            CONTROLLER.forceChunk(level, owner, chunk.x(), chunk.z(), hold, true);
        }
    }
}
