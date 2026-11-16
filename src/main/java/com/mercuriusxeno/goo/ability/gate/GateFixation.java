package com.mercuriusxeno.goo.ability.gate;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A caster held in Astral's gate: their gaze fixed on the moon or the blue overworld star, standing still where
 * they cast while the view closes in, until the fixation's ticks run out and the gate carries them. Synced to the
 * caster's client, which zooms the view and holds the look; the server holds the body and carries it.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 *
 * @param sky       what the caster fixates on, the moon or the overworld star
 * @param direction the unit direction it stands along
 * @param anchor    where the caster stands held
 * @param startedAt the game time the fixation began
 * @param duration  the ticks it runs before the gate carries the caster, zero for no fixation
 */
public record GateFixation(GateSight.Sky sky, Vec3 direction, Vec3 anchor, long startedAt, int duration) {

    /** No fixation. */
    public static final GateFixation NONE = new GateFixation(GateSight.Sky.MOON, Vec3.ZERO, Vec3.ZERO, 0L, 0);

    /** Carries a fixation to its caster's client. */
    public static final StreamCodec<ByteBuf, GateFixation> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> GateSight.Sky.values()[index], GateSight.Sky::ordinal), GateFixation::sky,
            Vec3.STREAM_CODEC, GateFixation::direction,
            Vec3.STREAM_CODEC, GateFixation::anchor,
            ByteBufCodecs.VAR_LONG, GateFixation::startedAt,
            ByteBufCodecs.VAR_INT, GateFixation::duration,
            GateFixation::new);

    /**
     * @return true while a fixation holds the caster
     */
    public boolean stands() {
        return duration > 0;
    }

    /**
     * How far the fixation has run.
     *
     * @param now the game time
     * @return the share of its ticks run, zero to one
     */
    public float progress(long now) {
        return stands() ? Math.clamp((float) (now - startedAt) / duration, 0F, 1F) : 0F;
    }

    /**
     * @param now the game time
     * @return true once the fixation's ticks have run out
     */
    public boolean ranOut(long now) {
        return stands() && now - startedAt >= duration;
    }
}
