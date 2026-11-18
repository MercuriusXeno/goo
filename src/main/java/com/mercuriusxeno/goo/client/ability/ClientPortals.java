package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.network.PortalsSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;
import java.util.stream.Stream;

/**
 * The local player's Quantum portals as the server last told them: their
 * pair's blue and orange portals where each stands, and every pair linked in
 * their level, theirs among them.
 * player-holds-one-pair-of-portals
 * portals-link-by-looking-through-portal-vision
 */
public final class ClientPortals {

    private static List<Portal> portals = List.of();
    private static List<QuantumPortals.Link> links = List.of();
    /** How far each portal has grown open since it was first told of. */
    private static final GrowOpen<Gate> OPENING = new GrowOpen<>();
    /** The level the portals were last told of in, so a portal standing as a level is joined shows full size. */
    private static ClientLevel toldIn;

    private ClientPortals() {
    }

    /**
     * Takes the portals the server sent.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(PortalsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            portals = List.copyOf(payload.portals());
            links = List.copyOf(payload.links());
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                OPENING.seen(Stream.concat(portals.stream(), links.stream()
                                .flatMap(pair -> Stream.of(pair.entry(), pair.exit())))
                        .map(Portal::gate).distinct().toList(), mc.level.getGameTime(), mc.level != toldIn);
                toldIn = mc.level;
            }
            ClientGates.refresh();
        });
    }

    /**
     * How far a portal has grown open, from nothing as it is placed to its
     * full size a moment later, as a hole's mouth grows.
     * portals-link-by-looking-through-portal-vision
     *
     * @param gate        the portal's frame
     * @param partialTick the frame's partial tick
     * @return its share of its full size, one once open
     */
    public static float grownOpen(Gate gate, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 1F : OPENING.grown(gate, mc.level.getGameTime(), partialTick);
    }

    /**
     * @return the local player's standing portals, blue first
     */
    public static List<Portal> portals() {
        return portals;
    }

    /**
     * @return every pair any player linked in the local player's level
     * linked-partner-chunks-stream-to-any-viewer
     */
    public static List<QuantumPortals.Link> links() {
        return links;
    }
}
