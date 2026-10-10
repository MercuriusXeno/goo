package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A delivery's charge block: how long a hold takes to charge fully, and,
 * for a thrown ability that slings flecks on release, how many flecks a full
 * charge throws, the cone they fan across and the ticks the sweep takes to
 * leave the hand.
 * decision nova-ring-grows-with-the-hold
 * decision shards-sling-then-morph-to-flechettes
 *
 * @param maxTicks      the ticks of hold a full charge takes, 0 for a delivery that does not charge
 * @param flecks        the flecks a full charge slings, 0 for a charge slinging none
 * @param spreadDegrees the cone a full charge fans across, edge to edge, in degrees
 * @param sweepTicks    the ticks between the sweep's first fleck and its last
 */
public record Charge(int maxTicks, int flecks, double spreadDegrees, int sweepTicks) {

    /** A delivery that does not charge. */
    public static final Charge NONE = new Charge(0, 0, 0, 0);

    /** Codec for the charge block, {@code "charge": {"max_ticks": 30, "flecks": 12, ...}}. */
    public static final Codec<Charge> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("max_ticks").forGetter(Charge::maxTicks),
            Codec.INT.optionalFieldOf("flecks", 0).forGetter(Charge::flecks),
            Codec.DOUBLE.optionalFieldOf("spread_degrees", 0.0).forGetter(Charge::spreadDegrees),
            Codec.INT.optionalFieldOf("sweep_ticks", 0).forGetter(Charge::sweepTicks)
    ).apply(inst, Charge::new));

    /** Stream codec carrying the charge on the ability sync and the flight broadcast. */
    public static final StreamCodec<ByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Charge::maxTicks,
            ByteBufCodecs.VAR_INT, Charge::flecks,
            ByteBufCodecs.DOUBLE, Charge::spreadDegrees,
            ByteBufCodecs.VAR_INT, Charge::sweepTicks,
            Charge::new);

    /**
     * A charge that slings nothing, charging over the ticks given.
     *
     * @param maxTicks the ticks of hold a full charge takes
     * @return the charge
     */
    public static Charge of(int maxTicks) {
        return maxTicks <= 0 ? NONE : new Charge(maxTicks, 0, 0, 0);
    }

    /**
     * Whether the release slings flecks rather than running once.
     *
     * @return true for a charge naming flecks
     */
    public boolean slings() {
        return flecks > 0;
    }

    /**
     * The flecks a release at a share of the charge slings: at least one,
     * up to the full charge's count.
     *
     * @param share the share of a full charge the hold reached, 0 to 1
     * @return the fleck count
     */
    public int fleckCount(float share) {
        return Math.clamp(Math.round(flecks * share), 1, Math.max(1, flecks));
    }

    /**
     * The cone a release at a share of the charge fans across.
     *
     * @param share the share of a full charge the hold reached, 0 to 1
     * @return the cone, edge to edge, in degrees
     */
    public double spreadDegrees(float share) {
        return spreadDegrees * share;
    }

    /**
     * The tick after the release a fleck leaves the hand, so the flecks
     * leave one after another across the sweep.
     *
     * @param index the fleck's place in the sweep, 0 first
     * @param count the flecks in the sweep
     * @return ticks after the release, 0 for the first fleck
     */
    public int launchDelay(int index, int count) {
        return count <= 1 ? 0 : Math.round((float) sweepTicks * index / (count - 1));
    }
}
