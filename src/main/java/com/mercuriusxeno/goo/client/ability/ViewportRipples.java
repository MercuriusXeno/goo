package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The viewport ripples playing on this client: when the local player is the
 * entity an afterimage echoes, its ripple grows out from their own body
 * where they cannot see it, so their viewport plays a copy. The window's
 * frame is the silhouette: Afterimages.PULSES edge rectangles leave on the
 * ripple's gaps, each starting at the screen edge and moving inward,
 * forward from the blinker, at an even pace until it stops at REACH_INSET,
 * fading as it goes. Client-only, drawn in first person alone; no payload
 * of its own.
 * Decision viewport-frame-ripples-on-blink.
 */
public final class ViewportRipples {

    /** The list the afterimage handler starts and the viewport layer draws. */
    public static final ViewportRipples CLIENT = new ViewportRipples();

    /**
     * How far in a line moves each tick, as a share of the shorter side: an
     * even pace, flat on the screen, so lines leaving PULSE_GAP_TICKS apart
     * stand a fiftieth of the short side apart, far enough to read as
     * separate lines.
     */
    static final float INSET_PER_TICK = 0.01f;

    /**
     * How far in from the screen edge a line stops, faded out, as a share of
     * the shorter side: the operator cut the travel to half, keeping the pace,
     * so a line fades over the ticks it takes to get there.
     */
    static final float REACH_INSET = 0.06f;

    /** The ticks a line takes to reach REACH_INSET at INSET_PER_TICK, its whole life. */
    static final int LINE_LIFE_TICKS = Math.round(REACH_INSET / INSET_PER_TICK);

    private final List<Afterimages.Afterimage<Void>> live = new ArrayList<>();

    /**
     * One frame rectangle as a frame draws it.
     *
     * @param inset how far in from the screen edge it stands, as a share of the shorter side
     * @param alpha its opacity as the vertex color's alpha byte
     */
    public record FramePulse(float inset, int alpha) {
    }

    /**
     * Starts a viewport ripple where an afterimage names the local player,
     * and none for any other entity.
     *
     * @param echoedEntityId the entity the afterimage echoes
     * @param localPlayerId  the local player's entity id
     * @param rgb            the goo type's color
     * @param now            the game time the afterimage arrived
     */
    public void onAfterimage(int echoedEntityId, int localPlayerId, int rgb, long now) {
        if (echoedEntityId == localPlayerId) {
            live.add(new Afterimages.Afterimage<>(null, Vec3.ZERO, rgb, now, LINE_LIFE_TICKS));
        }
    }

    /**
     * The ripples still playing, each answering its frame rectangles
     * through framePulses.
     *
     * @param now the game time
     * @return the ripples, in the order they started
     */
    public List<Afterimages.Afterimage<Void>> live(long now) {
        live.removeIf(ripple -> ripple.isOver(now));
        return List.copyOf(live);
    }

    /**
     * A ripple's frame rectangles at a game time: the ripple's own
     * silhouettes, each carried from the screen edge in to REACH_INSET at an
     * even pace through its life, forward from the blinker, its fade as it is.
     *
     * @param ripple   the ripple
     * @param gameTime the game time including the partial tick
     * @return the standing rectangles, oldest first
     */
    public static List<FramePulse> framePulses(Afterimages.Afterimage<?> ripple, float gameTime) {
        return ripple.pulses(gameTime).stream()
                .map(pulse -> new FramePulse(REACH_INSET * pulse.progress(), pulse.alpha()))
                .toList();
    }

    /** Drops every ripple, as the client leaves a level. */
    public void clear() {
        live.clear();
    }
}
