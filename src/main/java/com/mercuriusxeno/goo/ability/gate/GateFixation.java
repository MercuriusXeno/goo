package com.mercuriusxeno.goo.ability.gate;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A caster held in Astral's gate: their gaze fixed on the moon or the blue overworld star, standing still where
 * they cast while it swells to fill their view, for as long as they hold the cast, until the fixation's ticks run
 * out and the gate carries them. A caster who lets go first is released: the fixation stops standing and keeps the
 * game time it was let go, so their client runs the trip back from where it had reached. Synced to the caster's
 * client, which holds the look and draws the trip; the server holds the body and carries it.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision holding-right-click-pulls-the-moon-and-early-release-cancels
 *
 * @param sky        what the caster fixates on, the moon or the overworld star
 * @param direction  the unit direction it stands along
 * @param anchor     where the caster stands held
 * @param startedAt  the game time the fixation began
 * @param duration   the ticks it runs before the gate carries the caster, zero for no fixation
 * @param releasedAt the game time the caster let go, zero while they hold
 */
public record GateFixation(GateSight.Sky sky, Vec3 direction, Vec3 anchor, long startedAt, int duration,
        long releasedAt) {

    /** No fixation. */
    public static final GateFixation NONE = new GateFixation(GateSight.Sky.MOON, Vec3.ZERO, Vec3.ZERO, 0L, 0, 0L);
    private static final long HELD = 0L;

    /** Carries a fixation to its caster's client. */
    public static final StreamCodec<ByteBuf, GateFixation> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> GateSight.Sky.values()[index], GateSight.Sky::ordinal), GateFixation::sky,
            Vec3.STREAM_CODEC, GateFixation::direction,
            Vec3.STREAM_CODEC, GateFixation::anchor,
            ByteBufCodecs.VAR_LONG, GateFixation::startedAt,
            ByteBufCodecs.VAR_INT, GateFixation::duration,
            ByteBufCodecs.VAR_LONG, GateFixation::releasedAt,
            GateFixation::new);

    /**
     * A fixation the caster has just begun to hold.
     *
     * @param sky       what the caster fixates on
     * @param direction the unit direction it stands along
     * @param anchor    where the caster stands held
     * @param startedAt the game time it began
     * @param duration  the ticks it runs before the gate carries the caster
     * @return the held fixation
     */
    public static GateFixation held(GateSight.Sky sky, Vec3 direction, Vec3 anchor, long startedAt, int duration) {
        return new GateFixation(sky, direction, anchor, startedAt, duration, HELD);
    }

    /**
     * @return true while a fixation holds the caster
     */
    public boolean stands() {
        return duration > 0 && releasedAt == HELD;
    }

    /**
     * @return true for a fixation the caster let go of before the gate took them
     */
    public boolean released() {
        return duration > 0 && releasedAt != HELD;
    }

    /**
     * This fixation let go of.
     *
     * @param at the game time the caster let go
     * @return the released fixation
     */
    public GateFixation releaseAt(long at) {
        return new GateFixation(sky, direction, anchor, startedAt, duration, at);
    }

    /**
     * How far the fixation has run, held still from the moment a released one was let go.
     *
     * @param now the game time
     * @return the share of its ticks run, zero to one
     */
    public float progress(long now) {
        if (duration <= 0) {
            return 0F;
        }
        long at = released() ? Math.min(now, releasedAt) : now;
        return Math.clamp((float) (at - startedAt) / duration, 0F, 1F);
    }

    /**
     * @param now the game time
     * @return true once a held fixation's ticks have run out
     */
    public boolean ranOut(long now) {
        return stands() && now - startedAt >= duration;
    }
}
