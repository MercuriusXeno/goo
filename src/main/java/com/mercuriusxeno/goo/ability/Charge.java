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
 * leave the hand. A release with no charge slings the fewest flecks across
 * the narrowest cone, and both ramp linearly to the full charge's. A charge
 * naming a largest size grows the cast's size with the hold, from the
 * smallest a cast opens at to that cap.
 * decision nova-ring-grows-with-the-hold
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-cone-five-to-forty-five-and-three-to-twenty-seven-knives
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 *
 * @param maxTicks         the ticks of hold a full charge takes, 0 for a delivery that does not charge
 * @param flecks           the flecks a full charge slings, 0 for a charge slinging none
 * @param spreadDegrees    the cone a full charge fans across, edge to edge, in degrees
 * @param sweepTicks       the ticks between the sweep's first fleck and its last
 * @param fewestFlecks     the flecks a release with no charge slings
 * @param narrowestDegrees the cone a release with no charge fans across, edge to edge, in degrees
 * @param minSize          the size a charge opens at with no hold, in blocks, never under the smallest a cast opens at
 * @param maxSize          the size a full charge reaches, in blocks, {@link #NO_SIZE} for a charge sizing nothing
 */
public record Charge(int maxTicks, int flecks, double spreadDegrees, int sweepTicks, int fewestFlecks,
                     double narrowestDegrees, double minSize, double maxSize) {

    /** The largest size of a charge that sizes nothing. */
    public static final double NO_SIZE = 0;
    /** Slack under a whole size a float share's rounding may leave, so 2.9999999 reads 3. */
    private static final double WHOLE_SLACK = 1e-6;

    /** A delivery that does not charge. */
    public static final Charge NONE = new Charge(0, 0, 0, 0, 0, 0);

    /** Codec for the charge block, {@code "charge": {"max_ticks": 30, "flecks": 12, ...}}. */
    public static final Codec<Charge> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("max_ticks").forGetter(Charge::maxTicks),
            Codec.INT.optionalFieldOf("flecks", 0).forGetter(Charge::flecks),
            Codec.DOUBLE.optionalFieldOf("spread_degrees", 0.0).forGetter(Charge::spreadDegrees),
            Codec.INT.optionalFieldOf("sweep_ticks", 0).forGetter(Charge::sweepTicks),
            Codec.INT.optionalFieldOf("fewest_flecks", 1).forGetter(Charge::fewestFlecks),
            Codec.DOUBLE.optionalFieldOf("narrowest_degrees", 0.0).forGetter(Charge::narrowestDegrees),
            // blast-charges-from-two-to-seven-blocks: a sizing charge names the size it opens at
            Codec.DOUBLE.optionalFieldOf("min_size", ChargeSize.MIN_RADIUS).forGetter(Charge::minSize),
            Codec.DOUBLE.optionalFieldOf("max_size", NO_SIZE).forGetter(Charge::maxSize)
    ).apply(inst, Charge::new));

    /**
     * Stream codec carrying the charge on the ability sync and the flight
     * broadcast, floors included, so the client's aim lines fan the count and
     * cone the server slings and its preview grows to the size the server casts.
     */
    public static final StreamCodec<ByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Charge::maxTicks,
            ByteBufCodecs.VAR_INT, Charge::flecks,
            ByteBufCodecs.DOUBLE, Charge::spreadDegrees,
            ByteBufCodecs.VAR_INT, Charge::sweepTicks,
            ByteBufCodecs.VAR_INT, Charge::fewestFlecks,
            ByteBufCodecs.DOUBLE, Charge::narrowestDegrees,
            ByteBufCodecs.DOUBLE, Charge::minSize,
            ByteBufCodecs.DOUBLE, Charge::maxSize,
            Charge::new);

    /**
     * A charge sizing nothing.
     *
     * @param maxTicks         the ticks of hold a full charge takes
     * @param flecks           the flecks a full charge slings
     * @param spreadDegrees    the cone a full charge fans across, in degrees
     * @param sweepTicks       the ticks between the sweep's first fleck and its last
     * @param fewestFlecks     the flecks a release with no charge slings
     * @param narrowestDegrees the cone a release with no charge fans across, in degrees
     */
    public Charge(int maxTicks, int flecks, double spreadDegrees, int sweepTicks, int fewestFlecks,
                  double narrowestDegrees) {
        this(maxTicks, flecks, spreadDegrees, sweepTicks, fewestFlecks, narrowestDegrees, ChargeSize.MIN_RADIUS,
                NO_SIZE);
    }

    /**
     * A charge that slings nothing, charging over the ticks given.
     *
     * @param maxTicks the ticks of hold a full charge takes
     * @return the charge
     */
    public static Charge of(int maxTicks) {
        return maxTicks <= 0 ? NONE : new Charge(maxTicks, 0, 0, 0, 0, 0);
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
     * Whether the hold sizes the cast.
     *
     * @return true for a charge naming a largest size
     */
    public boolean sizes() {
        return maxSize > NO_SIZE;
    }

    /**
     * The size a charge has grown to at a share of a full charge: the size it
     * opens at with no charge, growing linearly to the largest size at a full
     * charge, never past it.
     *
     * @param share the share of a full charge the hold reached, 0 to 1
     * @return the size in blocks, between whole stages part way through one
     */
    public double sizeAt(float share) {
        double from = openingSize();
        return from + (Math.max(maxSize, from) - from) * share;
    }

    /**
     * The whole stage a hold has reached, the size a release casts at: the
     * size it opens at with no hold, a block more for each share of the hold
     * one block of growth takes, and no larger than the largest size past a
     * full charge; Blast opens at 2 and gains a block every 20 ticks to 7.
     * decision blast-charges-from-two-to-seven-blocks
     * decision blast-eases-between-stages-at-a-second-each
     *
     * @param heldTicks the ticks the use key was held
     * @return the stage's size in blocks
     */
    public double stageAt(int heldTicks) {
        float share = maxTicks <= 0 ? 1f : Math.clamp((float) heldTicks / maxTicks, 0f, 1f);
        return Math.floor(sizeAt(share) + WHOLE_SLACK);
    }

    /**
     * The size the charge opens at, never under the smallest a cast opens at.
     *
     * @return the size in blocks
     */
    public double openingSize() {
        return Math.max(minSize, ChargeSize.MIN_RADIUS);
    }

    /**
     * The flecks a release at a share of the charge slings: the fewest at no
     * charge, rising linearly to the full charge's count, and never fewer
     * than one.
     *
     * @param share the share of a full charge the hold reached, 0 to 1
     * @return the fleck count
     */
    public int fleckCount(float share) {
        return Math.clamp(Math.round(fewestFlecks + (flecks - fewestFlecks) * share), 1, Math.max(1, flecks));
    }

    /**
     * The cone a release at a share of the charge rains across: the
     * narrowest at no charge, opening linearly to the whole cone at a full
     * charge.
     *
     * @param share the share of a full charge the hold reached, 0 to 1
     * @return the cone, edge to edge, in degrees
     */
    public double spreadDegrees(float share) {
        return narrowestDegrees + (spreadDegrees - narrowestDegrees) * share;
    }
}
