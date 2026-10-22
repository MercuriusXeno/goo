package com.mercuriusxeno.goo.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * An exact amount of goo: a reduced fraction whose denominator is positive.
 * Arithmetic overflows loudly rather than wrapping.
 * Derivation divides exactly, so a share of a recipe keeps every part of its input,
 * and a remainder ledger exchanges any value whole.
 * decision values-are-fractions-melt-floors-fabricate-ceils
 *
 * @param numerator   the signed numerator, sharing no factor with the denominator
 * @param denominator the positive denominator
 */
public record GooFraction(long numerator, long denominator) implements Comparable<GooFraction> {

    public static final GooFraction ZERO = new GooFraction(0, 1);

    /** Persistent codec: the numerator and the denominator as longs. */
    public static final Codec<GooFraction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("numerator").forGetter(GooFraction::numerator),
            Codec.LONG.fieldOf("denominator").forGetter(GooFraction::denominator)
    ).apply(instance, GooFraction::new));

    /** Network codec: the numerator then the denominator as variable-length longs. */
    public static final StreamCodec<ByteBuf, GooFraction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, GooFraction::numerator,
            ByteBufCodecs.VAR_LONG, GooFraction::denominator,
            GooFraction::new);

    private static final String ERR_ZERO_DENOMINATOR = "Goo fraction with a zero denominator: ";
    private static final String DIVIDER = "/";

    /**
     * Reduces the fraction and moves its sign onto the numerator.
     *
     * @param numerator   the numerator
     * @param denominator the denominator, never zero
     */
    public GooFraction {
        if (denominator == 0) {
            throw new ArithmeticException(ERR_ZERO_DENOMINATOR + numerator);
        }
        long divisor = greatestCommonDivisor(Math.abs(numerator), Math.abs(denominator));
        long sign = Long.signum(denominator);
        numerator = sign * numerator / divisor;
        denominator = sign * denominator / divisor;
    }

    /**
     * The whole amount as a fraction.
     *
     * @param whole the amount
     * @return the fraction whole / 1
     */
    public static GooFraction of(long whole) {
        return new GooFraction(whole, 1);
    }

    /**
     * The fraction numerator / denominator, reduced.
     *
     * @param numerator   the numerator
     * @param denominator the denominator, never zero
     * @return the reduced fraction
     */
    public static GooFraction of(long numerator, long denominator) {
        return new GooFraction(numerator, denominator);
    }

    /**
     * The exact sum of this and another.
     *
     * @param other the addend
     * @return this + other
     */
    public GooFraction plus(GooFraction other) {
        return new GooFraction(
                Math.addExact(Math.multiplyExact(numerator, other.denominator),
                        Math.multiplyExact(other.numerator, denominator)),
                Math.multiplyExact(denominator, other.denominator));
    }

    /**
     * The exact difference of this and another.
     *
     * @param other the subtrahend
     * @return this - other
     */
    public GooFraction minus(GooFraction other) {
        return plus(other.negated());
    }

    /**
     * The exact product of this and a whole factor.
     *
     * @param factor the multiplier
     * @return this * factor
     */
    public GooFraction times(long factor) {
        return new GooFraction(Math.multiplyExact(numerator, factor), denominator);
    }

    /**
     * The exact quotient of this and a whole divisor.
     *
     * @param divisor the divisor, never zero
     * @return this / divisor
     */
    public GooFraction dividedBy(long divisor) {
        return new GooFraction(numerator, Math.multiplyExact(denominator, divisor));
    }

    /**
     * The fraction with its sign flipped.
     *
     * @return -this
     */
    public GooFraction negated() {
        return new GooFraction(Math.negateExact(numerator), denominator);
    }

    /**
     * True for zero.
     *
     * @return true when the numerator is zero
     */
    public boolean isZero() {
        return numerator == 0;
    }

    /**
     * True for a value below zero.
     *
     * @return true when the numerator is negative
     */
    public boolean isNegative() {
        return numerator < 0;
    }

    /**
     * True for a value above zero.
     *
     * @return true when the numerator is positive
     */
    public boolean isPositive() {
        return numerator > 0;
    }

    /**
     * True for a whole amount.
     *
     * @return true when the denominator is 1
     */
    public boolean isWhole() {
        return denominator == 1;
    }

    /**
     * The greatest integer at or below this fraction.
     *
     * @return the floor as an int
     */
    public int floor() {
        return Math.toIntExact(Math.floorDiv(numerator, denominator));
    }

    /**
     * The least integer at or above this fraction.
     *
     * @return the ceiling as an int
     */
    public int ceiling() {
        return Math.toIntExact(Math.ceilDiv(numerator, denominator));
    }

    /**
     * The nearest double, for display or a ratio that needs no exactness.
     *
     * @return numerator / denominator as a double
     */
    public double toDouble() {
        return (double) numerator / denominator;
    }

    @Override
    public int compareTo(GooFraction other) {
        return Long.compare(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator));
    }

    /**
     * Prints a whole amount as an integer and any other as numerator/denominator.
     */
    @Override
    public String toString() {
        return isWhole() ? Long.toString(numerator) : numerator + DIVIDER + denominator;
    }

    private static long greatestCommonDivisor(long a, long b) {
        long x = a;
        long y = b;
        while (y != 0) {
            long remainder = x % y;
            x = y;
            y = remainder;
        }
        return x == 0 ? 1 : x;
    }
}
