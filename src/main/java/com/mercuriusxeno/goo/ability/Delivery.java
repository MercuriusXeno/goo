package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * How an ability leaves the glove: its kind and that kind's params, read
 * from the ability JSON's delivery block, so the glove, the flight and the
 * server dispatch read the ability rather than the goo type
 * (decision delivery-block-in-ability-json).
 *
 * @param kind           the delivery kind
 * @param blocksPerTick  a beam's speed in blocks per tick
 * @param range          a punch's or stream's reach in blocks; zero where the kind takes the player's reach
 * @param coneDegrees    a stream's cone, apex to rim, in degrees
 * @param ticksPerCharge a stream's ticks of hold one cost pays for
 * @param grannyAllowed  whether an arc may lob onto a top face
 */
public record Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees,
                       int ticksPerCharge, boolean grannyAllowed) {

    /** A beam's speed where the JSON names none. */
    public static final double DEFAULT_BLOCKS_PER_TICK = 2.5;
    /** A stream's cone where the JSON names none. */
    public static final double DEFAULT_CONE_DEGREES = 20;
    /** A stream's ticks per cost where the JSON names none. */
    public static final int DEFAULT_TICKS_PER_CHARGE = 20;

    /** An arc with every param at its default. */
    public static final Delivery ARC = of(DeliveryKind.ARC);
    /** A beam with every param at its default. */
    public static final Delivery BEAM = of(DeliveryKind.BEAM);

    /**
     * Codec for the delivery block: the kind is required, each param optional.
     */
    public static final Codec<Delivery> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DeliveryKind.CODEC.fieldOf("kind").forGetter(Delivery::kind),
            Codec.DOUBLE.optionalFieldOf("blocks_per_tick", DEFAULT_BLOCKS_PER_TICK)
                    .forGetter(Delivery::blocksPerTick),
            Codec.DOUBLE.optionalFieldOf("range", 0.0).forGetter(Delivery::range),
            Codec.DOUBLE.optionalFieldOf("cone", DEFAULT_CONE_DEGREES).forGetter(Delivery::coneDegrees),
            Codec.INT.optionalFieldOf("ticks_per_charge", DEFAULT_TICKS_PER_CHARGE)
                    .forGetter(Delivery::ticksPerCharge),
            Codec.BOOL.optionalFieldOf("granny", true).forGetter(Delivery::grannyAllowed)
    ).apply(inst, Delivery::new));

    /**
     * Stream codec carrying the delivery on the ability sync and the flight
     * broadcast, so the client reads no registry.
     */
    public static final StreamCodec<ByteBuf, Delivery> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(ordinal -> DeliveryKind.values()[ordinal], DeliveryKind::ordinal),
            Delivery::kind,
            ByteBufCodecs.DOUBLE, Delivery::blocksPerTick,
            ByteBufCodecs.DOUBLE, Delivery::range,
            ByteBufCodecs.DOUBLE, Delivery::coneDegrees,
            ByteBufCodecs.VAR_INT, Delivery::ticksPerCharge,
            ByteBufCodecs.BOOL, Delivery::grannyAllowed,
            Delivery::new);

    /**
     * A delivery of the kind with every param at its default.
     *
     * @param kind the delivery kind
     * @return the delivery
     */
    public static Delivery of(DeliveryKind kind) {
        return new Delivery(kind, DEFAULT_BLOCKS_PER_TICK, 0, DEFAULT_CONE_DEGREES, DEFAULT_TICKS_PER_CHARGE, true);
    }

    /**
     * Whether the flight flies a straight line rather than an arc.
     *
     * @return true for a beam
     */
    public boolean fliesStraight() {
        return kind == DeliveryKind.BEAM;
    }

    /**
     * Whether the glove aims a line at a target, the arc or the beam; a
     * punch aims at reach and a self ability at nothing.
     *
     * @return true for an arc or a beam
     */
    public boolean aimsALine() {
        return kind == DeliveryKind.ARC || kind == DeliveryKind.BEAM;
    }

    /**
     * The ticks a flight takes to cover a distance: a beam at its speed, an
     * arc by the thrown type's flight time.
     *
     * @param distance       world-space distance in blocks
     * @param levity         the thrown type's multiplier on the root of distance
     * @param baseFlightTime the thrown type's ticks of flight before distance adds any
     * @return travel ticks, always at least 1
     */
    public int travelTicks(double distance, float levity, int baseFlightTime) {
        if (fliesStraight()) {
            return (int) Math.max(1, Math.ceil(distance / blocksPerTick));
        }
        return (int) ThrowArc.travelTicks(distance, levity, baseFlightTime);
    }

    /**
     * The peak a flight flies between its start and its endpoint at the
     * throw: zero for a beam, the lob peak for a granny throw the delivery
     * allows, the base peak otherwise.
     *
     * @param start     the flight's start
     * @param end       the flight's endpoint at the throw
     * @param grannyArc whether the throw is a lob onto a top face
     * @return the peak height in blocks
     */
    public double peak(Vec3 start, Vec3 end, boolean grannyArc) {
        if (fliesStraight()) {
            return 0;
        }
        return grannyArc && grannyAllowed ? ThrowArc.lobPeak(start, end) : ThrowArc.basePeak(start.distanceTo(end));
    }
}
