package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.ConjuredDoor;
import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The doors this client has been told players channeling Outsider stand,
 * by caster, each drawn between its last two updates so it grows and moves
 * smoothly. A door no longer sent collapses to nothing over a few ticks, its
 * channel let go; a door whose caster it carried off goes with them.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 */
public final class ClientConjuredDoors {

    /** Client ticks a door outlasts its last update before it starts to collapse. */
    static final int OUTLASTS = 2;
    /** Client ticks a collapsing door takes to shrink to nothing. */
    static final int COLLAPSE_TICKS = 5;

    private static final Map<Integer, Seen> DOORS = new HashMap<>();

    private ClientConjuredDoors() {
    }

    /**
     * A door as its last two updates told it.
     *
     * @param before the door the update before stood
     * @param now    the door the last update stood
     * @param at     the client game tick the last update came
     */
    record Seen(ConjuredDoor before, ConjuredDoor now, long at) {
    }

    /**
     * A door as a frame draws it.
     *
     * @param at   the middle of its foot
     * @param yaw  the way its caster faced, in degrees
     * @param size its size, 0 for nothing to 1 for full
     */
    public record Drawn(Vec3 at, float yaw, double size) {
    }

    /**
     * Takes a door the server sent.
     *
     * @param payload the door's payload
     * @param context the network context
     */
    public static void handle(ConjuredDoorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                take(payload, mc.level.getGameTime());
            }
        });
    }

    /**
     * Holds a door, its last update kept as the one before it.
     *
     * @param payload  the door's payload
     * @param gameTime the client game tick it came
     */
    static void take(ConjuredDoorPayload payload, long gameTime) {
        Seen was = DOORS.get(payload.casterId());
        ConjuredDoor before = was == null ? payload.door() : was.now();
        DOORS.put(payload.casterId(), new Seen(before, payload.door(), gameTime));
    }

    /**
     * Forgets every door, as the client leaves a level.
     */
    static void forget() {
        DOORS.clear();
    }

    /**
     * Each caster's door as a frame draws it, dropping each door done collapsing.
     *
     * @param time the client game tick and the frame's partial tick
     * @return the doors drawn, by caster
     */
    static Map<Integer, Drawn> drawnAt(double time) {
        Map<Integer, Drawn> drawn = new HashMap<>();
        DOORS.entrySet().removeIf(entry -> {
            Optional<Drawn> door = drawnAt(entry.getValue(), time);
            door.ifPresent(shown -> drawn.put(entry.getKey(), shown));
            return door.isEmpty();
        });
        return drawn;
    }

    /**
     * How a held door draws at a moment: between its last two updates while
     * they come, then shrinking to nothing once they stop. A door that
     * carried its caster off goes with them, the caster leaving this level,
     * which the renderer reads.
     *
     * @param seen the door's last two updates
     * @param time the client game tick and the frame's partial tick
     * @return the door drawn, empty once it is gone
     */
    static Optional<Drawn> drawnAt(Seen seen, double time) {
        double since = time - seen.at();
        ConjuredDoor now = seen.now();
        if (since <= OUTLASTS) {
            double step = Math.min(1, Math.max(0, since));
            ConjuredDoor before = seen.before();
            return Optional.of(new Drawn(before.at().lerp(now.at(), step), now.yaw(),
                    before.size() + (now.size() - before.size()) * step));
        }
        double left = 1 - (since - OUTLASTS) / COLLAPSE_TICKS;
        return left <= 0 ? Optional.empty() : Optional.of(new Drawn(now.at(), now.yaw(), now.size() * left));
    }
}
