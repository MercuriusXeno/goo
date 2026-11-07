package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.network.PortalsSyncPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The local player's Quantum portals as the server last told them: every
 * portal they placed, the pair they linked and the portal they are choosing
 * a partner for.
 * portals-link-by-looking-through-portal-vision
 */
public final class ClientPortals {

    /** How far off the look, in degrees, a distant portal may stand and still be the one right clicked. */
    public static final double PICK_CONE_DEGREES = 6.0;

    private static List<Portal> portals = List.of();
    private static Optional<QuantumPortals.Link> link = Optional.empty();
    private static Optional<Portal> choosing = Optional.empty();
    private static List<QuantumPortals.Link> links = List.of();

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
            link = payload.link();
            choosing = payload.choosing();
            links = List.copyOf(payload.links());
            ClientGates.refresh();
        });
    }

    /**
     * @return every portal the local player placed
     */
    public static List<Portal> portals() {
        return portals;
    }

    /**
     * @return the pair the local player linked, empty for none
     */
    public static Optional<QuantumPortals.Link> link() {
        return link;
    }

    /**
     * @return every pair any player linked in the local player's level
     * linked-partner-chunks-stream-to-any-viewer
     */
    public static List<QuantumPortals.Link> links() {
        return links;
    }

    /**
     * @return the portal the local player is choosing a partner for, empty while they choose none
     */
    public static Optional<Portal> choosing() {
        return choosing;
    }

    /**
     * The distant portal a look picks while choosing: the one nearest the
     * look's line, within the pick cone, however far or walled off it stands,
     * among the portals in the chosen-from portal's own dimension.
     *
     * @param among  the portals to pick from
     * @param except the portal being chosen for, never picked
     * @param eye    the eye
     * @param look   the unit look vector
     * @return the portal, empty where none stands within the cone
     */
    public static Optional<Portal> pickedOnLook(List<Portal> among, Portal except, Vec3 eye, Vec3 look) {
        double cosine = Math.cos(Math.toRadians(PICK_CONE_DEGREES));
        return among.stream()
                .filter(portal -> !portal.equals(except)
                        && portal.block().dimension().equals(except.block().dimension()))
                .filter(portal -> alignment(portal, eye, look) >= cosine)
                .max(Comparator.comparingDouble(portal -> alignment(portal, eye, look)));
    }

    private static double alignment(Portal portal, Vec3 eye, Vec3 look) {
        Vec3 toward = portal.faceCenter().subtract(eye);
        double length = toward.length();
        return length == 0 ? 1 : toward.dot(look) / length;
    }
}
