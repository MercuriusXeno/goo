package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The afterimages playing on this client. Each is a snapshot of an entity's
 * render state, frozen in its pose, standing at a point, and plays as a
 * ripple: PULSES silhouettes leave PULSE_GAP_TICKS apart, each growing
 * outward from the body by up to GROWTH_BLOCKS and fading over its life.
 * Decision afterimage-is-one-shared-effect.
 *
 * @param <S> the snapshot an afterimage draws from
 */
public final class Afterimages<S> {

    /** The list the client's afterimage handler and renderer share. */
    public static final Afterimages<EntityRenderState> CLIENT = new Afterimages<>();

    /** Silhouettes one ripple sends out. */
    public static final int PULSES = 4;

    /** Game ticks between one silhouette leaving and the next. */
    public static final int PULSE_GAP_TICKS = 2;

    /** Blocks a silhouette has grown out from the body when it has faded. */
    public static final float GROWTH_BLOCKS = 0.4f;

    /** Opacity in the vertex color's alpha byte. */
    private static final int FULL_ALPHA = 0xFF;

    private final List<Afterimage<S>> live = new ArrayList<>();

    /**
     * One silhouette of a ripple as a frame draws it.
     *
     * @param growth   blocks it stands out from the body
     * @param alpha    its opacity as the vertex color's alpha byte
     * @param progress how far through its life it is, 0 to 1
     */
    public record Pulse(float growth, int alpha, float progress) {
    }

    /**
     * One afterimage playing.
     *
     * @param snapshot  the echoed entity's render state as it was left
     * @param position  the world point the ripple stands at
     * @param rgb       the goo type's color, 0xRRGGBB
     * @param startTick the game time it was left
     * @param lifeTicks the game ticks each silhouette takes to grow and fade
     * @param <S>       the snapshot type
     */
    public record Afterimage<S>(S snapshot, Vec3 position, int rgb, long startTick, int lifeTicks) {

        /**
         * The silhouettes standing at a game time, oldest first: each leaves
         * PULSE_GAP_TICKS after the one before, grows out on an ease-out and
         * fades linearly over its life.
         *
         * @param gameTime the game time including the partial tick
         * @return the standing silhouettes
         */
        public List<Pulse> pulses(float gameTime) {
            List<Pulse> standing = new ArrayList<>();
            for (int pulse = 0; pulse < PULSES; pulse++) {
                float progress = (gameTime - startTick - pulse * PULSE_GAP_TICKS) / lifeTicks;
                if (progress >= 0f && progress < 1f) {
                    float remaining = 1f - progress;
                    standing.add(new Pulse(GROWTH_BLOCKS * (1f - remaining * remaining),
                            Math.round(remaining * FULL_ALPHA), progress));
                }
            }
            return standing;
        }

        /**
         * @param now the game time
         * @return true once the last silhouette has faded
         */
        boolean isOver(long now) {
            return now - startTick >= (long) (PULSES - 1) * PULSE_GAP_TICKS + lifeTicks;
        }
    }

    /**
     * Leaves an afterimage.
     *
     * @param snapshot  the echoed entity's render state
     * @param position  the world point it stands at
     * @param rgb       the goo type's color
     * @param now       the game time it is left
     * @param lifeTicks the game ticks each silhouette takes to grow and fade
     */
    public void add(S snapshot, Vec3 position, int rgb, long now, int lifeTicks) {
        live.add(new Afterimage<>(snapshot, position, rgb, now, Math.max(1, lifeTicks)));
    }

    /**
     * Drops every afterimage whose ripple has played out and answers the rest.
     *
     * @param now the game time
     * @return the afterimages still playing, in the order they were left
     */
    public List<Afterimage<S>> live(long now) {
        live.removeIf(afterimage -> afterimage.isOver(now));
        return List.copyOf(live);
    }

    /** Drops every afterimage, as the client leaves a level. */
    public void clear() {
        live.clear();
    }
}
