package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.ExitTilt;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The exit tilt each body the client draws came out of a linked pair with,
 * from the game time it emerged, until the tilt eases to nothing.
 * bodies-emerge-along-their-travel-and-ease-upright
 */
public final class ClientExitTilts {

    private ClientExitTilts() {
    }

    /**
     * Records the tilt a body emerged with.
     *
     * @param entityId the body's id
     * @param tilt     the tilt
     * @param gameTime the game time it emerged
     */
    public static void record(int entityId, ExitTilt tilt, long gameTime) {
        if (tilt.tilts()) {
            TILTS.put(entityId, new Emerged(tilt, gameTime));
        } else {
            TILTS.remove(entityId);
        }
    }

    /**
     * The tilt a body still holds.
     *
     * @param entityId the body's id
     * @param time     the game time now, partial ticks and all
     * @return its axis and the degrees still turned, empty once eased to nothing
     */
    public static Optional<ExitTilt> tiltOf(int entityId, double time) {
        Emerged emerged = TILTS.get(entityId);
        if (emerged == null) {
            return Optional.empty();
        }
        ExitTilt left = emerged.tilt().at(time - emerged.since());
        if (!left.tilts()) {
            TILTS.remove(entityId);
            return Optional.empty();
        }
        return Optional.of(left);
    }

    /** A tilt and the game time its body emerged. */
    private record Emerged(ExitTilt tilt, long since) {
    }

    private static final Map<Integer, Emerged> TILTS = new HashMap<>();
}
