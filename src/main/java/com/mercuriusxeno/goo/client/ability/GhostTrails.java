package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The ghost trails playing on this client. A trail is GHOSTS translucent
 * echoes of an entity, frozen in the pose it held the tick it blinked,
 * standing along the segment from where it left to where it landed: packed
 * at the source, sparse across the middle, packed again at the
 * destination, the last on the entity. Every ghost appears the tick the
 * trail lands, and each fades with a start offset growing toward the
 * destination, so the source end fades first and the trail collapses onto
 * the entity.
 * Decision ghost-trail-spans-the-blink.
 *
 * @param <S> the snapshot a ghost draws from
 */
public final class GhostTrails<S> {

    /** The list the client's ghost trail handler and renderer share. */
    public static final GhostTrails<EntityRenderState> CLIENT = new GhostTrails<>();

    /** Ghosts along one trail, the source's and the destination's among them. */
    public static final int GHOSTS = 10;

    /**
     * The share of the trail's life the destination ghost waits before it
     * starts fading; a ghost between waits its fraction of it, the source
     * ghost none.
     */
    static final float FADE_STAGGER = 0.5f;

    /** A fresh ghost's opacity: translucent from its first frame. */
    static final float FRESH_OPACITY = 0.55f;

    /** The smoothstep cubic's terms: 3t^2 - 2t^3. */
    private static final float SMOOTHSTEP_SQUARE = 3f;
    private static final float SMOOTHSTEP_CUBE = 2f;

    /** Opacity in the vertex color's alpha byte. */
    private static final int FULL_ALPHA = 0xFF;

    private final List<GhostTrail<S>> live = new ArrayList<>();

    /**
     * One ghost as a frame draws it.
     *
     * @param position the world point it stands at
     * @param alpha    its opacity as the vertex color's alpha byte
     */
    public record Ghost(Vec3 position, int alpha) {
    }

    /**
     * Where each ghost stands along the trail, as fractions from the source
     * (0) to the destination (1): an ease-in-out sampled at even steps, so
     * the ghosts pack at both ends and spread across the middle.
     *
     * @param ghosts how many ghosts the trail holds, two or more
     * @return the fractions, rising from 0 to 1
     */
    static float[] fractions(int ghosts) {
        float[] fractions = new float[ghosts];
        for (int ghost = 0; ghost < ghosts; ghost++) {
            float even = (float) ghost / (ghosts - 1);
            fractions[ghost] = even * even * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * even);
        }
        return fractions;
    }

    /**
     * A ghost's opacity at an age: FRESH_OPACITY until its fade starts, its
     * fraction of FADE_STAGGER of the life in, then falling linearly to
     * nothing over the rest of the life's share every ghost fades across,
     * so the source ghost is gone halfway through and the destination ghost
     * last, at the end of the life.
     *
     * @param fraction  the ghost's fraction along the trail, 0 at the source
     * @param ageTicks  ticks since the trail landed, with the partial tick
     * @param lifeTicks the trail's life
     * @return the alpha byte in [0, 255]
     */
    static int alpha(float fraction, float ageTicks, int lifeTicks) {
        float fadeStart = fraction * FADE_STAGGER * lifeTicks;
        float fadeSpan = (1f - FADE_STAGGER) * lifeTicks;
        float faded = Math.clamp((ageTicks - fadeStart) / fadeSpan, 0f, 1f);
        return Math.round((1f - faded) * FRESH_OPACITY * FULL_ALPHA);
    }

    /**
     * One ghost trail playing.
     *
     * @param snapshot    the entity's render state the tick it blinked
     * @param source      the world point it left
     * @param destination the world point it landed
     * @param rgb         the goo type's color, 0xRRGGBB
     * @param startTick   the game time the trail landed
     * @param lifeTicks   the game ticks the trail takes to fade out
     * @param <S>         the snapshot type
     */
    public record GhostTrail<S>(S snapshot, Vec3 source, Vec3 destination, int rgb, long startTick, int lifeTicks) {

        /**
         * The ghosts still showing at a game time, source end first.
         *
         * @param gameTime the game time including the partial tick
         * @return the ghosts with an opacity above nothing
         */
        public List<Ghost> ghosts(float gameTime) {
            List<Ghost> showing = new ArrayList<>();
            for (float fraction : fractions(GHOSTS)) {
                int alpha = alpha(fraction, gameTime - startTick, lifeTicks);
                if (alpha > 0) {
                    showing.add(new Ghost(source.lerp(destination, fraction), alpha));
                }
            }
            return showing;
        }

        /**
         * @param now the game time
         * @return true once every ghost has faded
         */
        boolean isOver(long now) {
            return now - startTick >= lifeTicks;
        }
    }

    /**
     * Lays a trail.
     *
     * @param snapshot    the entity's render state
     * @param source      the world point it left
     * @param destination the world point it landed
     * @param rgb         the goo type's color
     * @param now         the game time the trail lands
     * @param lifeTicks   the game ticks it takes to fade out
     */
    public void add(S snapshot, Vec3 source, Vec3 destination, int rgb, long now, int lifeTicks) {
        live.add(new GhostTrail<>(snapshot, source, destination, rgb, now, Math.max(1, lifeTicks)));
    }

    /**
     * Drops every trail that has faded and answers the rest.
     *
     * @param now the game time
     * @return the trails still playing, in the order they landed
     */
    public List<GhostTrail<S>> live(long now) {
        live.removeIf(trail -> trail.isOver(now));
        return List.copyOf(live);
    }

    /** Drops every trail, as the client leaves a level. */
    public void clear() {
        live.clear();
    }
}
