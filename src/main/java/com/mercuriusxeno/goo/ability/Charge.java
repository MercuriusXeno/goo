package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A delivery's charge block: how long a hold takes to charge fully, and, for
 * a held throw that stacks what it throws while held, Knives' knives or
 * Dart's javelins, the most the hold stacks, the ticks each takes to form and
 * A charge naming a largest size grows the cast's size with the hold, from
 * the smallest a cast opens at to that cap.
 * decision nova-ring-grows-with-the-hold
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision dart-stacks-three-javelins-one-per-ten-ticks
 * decision knives-throw-one-per-two-ticks-while-left-click-is-held
 * decision dart-throws-one-javelin-per-five-ticks-while-left-click-is-held
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 *
 * @param maxTicks   the ticks of hold a full charge takes, 0 for a delivery that does not charge
 * @param stack      the most the hold stacks, 0 for a charge stacking nothing
 * @param stackTicks the ticks each one stacked takes to form
 * @param throwTicks the ticks between the throws a held left click makes, 0 for a hold throwing one per press
 * @param minSize    the size a charge opens at with no hold, in blocks, never under the smallest a cast opens at
 * @param maxSize    the size a full charge reaches, in blocks, {@link #NO_SIZE} for a charge sizing nothing
 */
public record Charge(int maxTicks, int stack, int stackTicks, int throwTicks, double minSize, double maxSize) {

    /** The largest size of a charge that sizes nothing. */
    public static final double NO_SIZE = 0;
    /** Slack under a whole size a float share's rounding may leave, so 2.9999999 reads 3. */
    private static final double WHOLE_SLACK = 1e-6;

    /** A delivery that does not charge. */
    public static final Charge NONE = new Charge(0, 0, 0);

    /** Codec for the charge block, {@code "charge": {"max_ticks": 30, "stack": 10, "stack_ticks": 3, "throw_ticks": 2}}. */
    public static final Codec<Charge> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("max_ticks").forGetter(Charge::maxTicks),
            Codec.INT.optionalFieldOf("stack", 0).forGetter(Charge::stack),
            Codec.INT.optionalFieldOf("stack_ticks", 0).forGetter(Charge::stackTicks),
            Codec.INT.optionalFieldOf("throw_ticks", 0).forGetter(Charge::throwTicks),
            // blast-charges-from-two-to-seven-blocks: a sizing charge names the size it opens at
            Codec.DOUBLE.optionalFieldOf("min_size", ChargeSize.MIN_RADIUS).forGetter(Charge::minSize),
            Codec.DOUBLE.optionalFieldOf("max_size", NO_SIZE).forGetter(Charge::maxSize)
    ).apply(inst, Charge::new));

    /**
     * Stream codec carrying the charge on the ability sync and the flight
     * broadcast, floors included, so the client's preview grows to the size
     * the server casts and its stack forms at the server's rate.
     */
    public static final StreamCodec<ByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Charge::maxTicks,
            ByteBufCodecs.VAR_INT, Charge::stack,
            ByteBufCodecs.VAR_INT, Charge::stackTicks,
            ByteBufCodecs.VAR_INT, Charge::throwTicks,
            ByteBufCodecs.DOUBLE, Charge::minSize,
            ByteBufCodecs.DOUBLE, Charge::maxSize,
            Charge::new);

    /**
     * A charge sizing nothing.
     *
     * @param maxTicks   the ticks of hold a full charge takes
     * @param stack      the most the hold stacks
     * @param stackTicks the ticks each one stacked takes to form
 * @param throwTicks the ticks between the throws a held left click makes, 0 for a hold throwing one per press
     */
    public Charge(int maxTicks, int stack, int stackTicks) {
        this(maxTicks, stack, stackTicks, 0, ChargeSize.MIN_RADIUS, NO_SIZE);
    }

    /**
     * A charge that stacks nothing, charging over the ticks given.
     *
     * @param maxTicks the ticks of hold a full charge takes
     * @return the charge
     */
    public static Charge of(int maxTicks) {
        return maxTicks <= 0 ? NONE : new Charge(maxTicks, 0, 0);
    }

    /**
     * Whether the hold stacks what it throws rather than charging one cast.
     *
     * @return true for a charge naming a stack
     */
    public boolean stacks() {
        return stack > 0;
    }

    /**
     * What a hold has stacked some ticks in.
     * decision knives-charge-one-per-three-ticks-up-to-ten
     *
     * @param heldTicks the ticks the hold has run
     * @return the count formed, 0 for a charge stacking nothing
     */
    public int stackedAt(int heldTicks) {
        return stackedSinceThrow(0, heldTicks);
    }

    /**
     * What the hold holds some ticks after a throw: what the throw left, and
     * one more each time one's ticks go by, never past the most the charge
     * stacks.
     * decision knives-charge-one-per-three-ticks-up-to-ten
     * decision knives-refill-from-the-stage-the-fan-rounds-up-to
     *
     * @param left       what the throw left
     * @param sinceThrow the ticks since the throw
     * @return the count held
     */
    public int stackedSinceThrow(int left, int sinceThrow) {
        if (stackTicks <= 0 || sinceThrow < 0) {
            return left;
        }
        return Math.min(stack, left + sinceThrow / stackTicks);
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
