package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.ConjuredDoor;
import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The doors this client has been told players channeling Outsider stand,
 * by caster, each drawn between its last two updates so it grows and moves
 * smoothly. A door no longer sent collapses to nothing over a few ticks, its
 * channel let go; a door whose caster it carried off goes with them.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 * The local caster's own door stands from the look of the frame drawn, and
 * another caster's turns between updates as it moves.
 * decision conjured-door-follows-the-look-smoothly
 */
public final class ClientConjuredDoors {

    /** An entity id no player holds, for a frame drawn with no local caster. */
    private static final int NO_LOCAL_CASTER = Integer.MIN_VALUE;
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
     * The look of the frame being drawn: where the local player's feet
     * stand and the way they face at the frame's partial tick.
     *
     * @param feet the local player's interpolated feet
     * @param yaw  the local player's view yaw, in degrees
     */
    public record Look(Vec3 feet, float yaw) {
    }

    /**
     * Each caster's door as a frame draws it, dropping each door done collapsing.
     *
     * @param time the client game tick and the frame's partial tick
     * @return the doors drawn, by caster
     */
    static Map<Integer, Drawn> drawnAt(double time) {
        return drawnAt(time, NO_LOCAL_CASTER, null);
    }

    /**
     * Each caster's door as a frame draws it, the local player's own stood
     * from the frame's look, dropping each door done collapsing.
     *
     * @param time        the client game tick and the frame's partial tick
     * @param localCaster the local player's entity id
     * @param localLook   the local player's look this frame, null while none stands
     * @return the doors drawn, by caster
     */
    static Map<Integer, Drawn> drawnAt(double time, int localCaster, @Nullable Look localLook) {
        Map<Integer, Drawn> drawn = new HashMap<>();
        DOORS.entrySet().removeIf(entry -> {
            Optional<Drawn> door = entry.getKey() == localCaster && localLook != null
                    ? drawnAt(entry.getValue(), time, localLook) : drawnAt(entry.getValue(), time);
            door.ifPresent(shown -> drawn.put(entry.getKey(), shown));
            return door.isEmpty();
        });
        return drawn;
    }

    /**
     * How the local caster's door draws at a moment: its size and share
     * crossed as the server's updates tell them, stood before the feet the
     * way the frame looks, so it turns as the camera turns rather than in
     * tick steps.
     *
     * @param seen the door's last two updates
     * @param time the client game tick and the frame's partial tick
     * @param look the local player's look this frame
     * @return the door drawn, empty once it is gone
     */
    static Optional<Drawn> drawnAt(Seen seen, double time, Look look) {
        double since = time - seen.at();
        double step = stepBetween(since);
        double crossed = Mth.lerp(step, seen.before().crossed(), seen.now().crossed());
        return sizeAt(seen, since).map(size ->
                new Drawn(ConjuredDoor.placedAt(look.feet(), look.yaw(), crossed), look.yaw(), size));
    }

    /**
     * How another caster's door draws at a moment: between its last two
     * updates while they come, turning along the shorter arc, then shrinking
     * to nothing once they stop. A door that carried its caster off goes
     * with them, the caster leaving this level, which the renderer reads.
     *
     * @param seen the door's last two updates
     * @param time the client game tick and the frame's partial tick
     * @return the door drawn, empty once it is gone
     */
    static Optional<Drawn> drawnAt(Seen seen, double time) {
        double since = time - seen.at();
        double step = stepBetween(since);
        ConjuredDoor before = seen.before();
        ConjuredDoor now = seen.now();
        Vec3 at = before.at().lerp(now.at(), step);
        float yaw = Mth.rotLerp((float) step, before.yaw(), now.yaw());
        return sizeAt(seen, since).map(size -> new Drawn(at, yaw, size));
    }

    /**
     * The share of the way from the update before to the last one a moment stands.
     *
     * @param since client ticks since the last update came
     * @return the share, 0 to 1
     */
    private static double stepBetween(double since) {
        return Math.clamp(since, 0, 1);
    }

    /**
     * A held door's size at a moment: between its last two updates while
     * they come, then shrinking to nothing once they stop.
     *
     * @param seen  the door's last two updates
     * @param since client ticks since the last update came
     * @return the size, empty once the door is gone
     */
    private static Optional<Double> sizeAt(Seen seen, double since) {
        double now = seen.now().size();
        if (since <= OUTLASTS) {
            return Optional.of(Mth.lerp(stepBetween(since), seen.before().size(), now));
        }
        double left = 1 - (since - OUTLASTS) / COLLAPSE_TICKS;
        return left <= 0 ? Optional.empty() : Optional.of(now * left);
    }
}
