package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A delivery's charge block: how long a hold takes to charge fully, and,
 * for a thrown ability that forms knives while held, how many stages the
 * knives form in and the ticks each stage takes. A charge naming a largest
 * size grows the cast's size with the hold, from the smallest a cast opens
 * at to that cap.
 * decision nova-ring-grows-with-the-hold
 * decision knives-form-in-fibonacci-stages-to-twelve
 * decision knives-stage-takes-twelve-ticks-with-no-cooldown
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 *
 * @param maxTicks   the ticks of hold a full charge takes, 0 for a delivery that does not charge
 * @param stages     the stages the hold forms knives in, 0 for a charge forming none
 * @param stageTicks the ticks each stage takes to form
 * @param minSize    the size a charge opens at with no hold, in blocks, never under the smallest a cast opens at
 * @param maxSize    the size a full charge reaches, in blocks, {@link #NO_SIZE} for a charge sizing nothing
 */
public record Charge(int maxTicks, int stages, int stageTicks, double minSize, double maxSize) {

    /** The largest size of a charge that sizes nothing. */
    public static final double NO_SIZE = 0;
    /** Slack under a whole size a float share's rounding may leave, so 2.9999999 reads 3. */
    private static final double WHOLE_SLACK = 1e-6;

    /** A delivery that does not charge. */
    public static final Charge NONE = new Charge(0, 0, 0);

    /** Codec for the charge block, {@code "charge": {"max_ticks": 60, "stages": 5, "stage_ticks": 12}}. */
    public static final Codec<Charge> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("max_ticks").forGetter(Charge::maxTicks),
            Codec.INT.optionalFieldOf("stages", 0).forGetter(Charge::stages),
            Codec.INT.optionalFieldOf("stage_ticks", 0).forGetter(Charge::stageTicks),
            // blast-charges-from-two-to-seven-blocks: a sizing charge names the size it opens at
            Codec.DOUBLE.optionalFieldOf("min_size", ChargeSize.MIN_RADIUS).forGetter(Charge::minSize),
            Codec.DOUBLE.optionalFieldOf("max_size", NO_SIZE).forGetter(Charge::maxSize)
    ).apply(inst, Charge::new));

    /**
     * Stream codec carrying the charge on the ability sync and the flight
     * broadcast, floors included, so the client's preview grows to the size
     * the server casts and its knives form on the server's stages.
     */
    public static final StreamCodec<ByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Charge::maxTicks,
            ByteBufCodecs.VAR_INT, Charge::stages,
            ByteBufCodecs.VAR_INT, Charge::stageTicks,
            ByteBufCodecs.DOUBLE, Charge::minSize,
            ByteBufCodecs.DOUBLE, Charge::maxSize,
            Charge::new);

    /**
     * A charge sizing nothing.
     *
     * @param maxTicks   the ticks of hold a full charge takes
     * @param stages     the stages the hold forms knives in
     * @param stageTicks the ticks each stage takes to form
     */
    public Charge(int maxTicks, int stages, int stageTicks) {
        this(maxTicks, stages, stageTicks, ChargeSize.MIN_RADIUS, NO_SIZE);
    }

    /**
     * A charge that forms no knives, charging over the ticks given.
     *
     * @param maxTicks the ticks of hold a full charge takes
     * @return the charge
     */
    public static Charge of(int maxTicks) {
        return maxTicks <= 0 ? NONE : new Charge(maxTicks, 0, 0);
    }

    /**
     * Whether the hold forms knives in stages rather than charging one cast.
     * decision knives-form-in-fibonacci-stages-to-twelve
     *
     * @return true for a charge naming stages
     */
    public boolean formsInStages() {
        return stages > 0;
    }

    /**
     * The knives a hold has formed some ticks in.
     * decision knives-form-in-fibonacci-stages-to-twelve
     * decision knives-stage-takes-twelve-ticks-with-no-cooldown
     *
     * @param heldTicks the ticks the hold has run
     * @return the knives formed, 0 for a charge forming none
     */
    public int knivesFormedAt(int heldTicks) {
        return KnifeStages.formedAt(heldTicks, stages, stageTicks);
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
}
