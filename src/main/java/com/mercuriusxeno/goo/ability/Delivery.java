package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * How an ability leaves the glove: its kind and that kind's params, read
 * from the ability JSON's delivery block, so the glove, the flight and the
 * server dispatch read the ability rather than the goo type
 * (decision delivery-block-in-ability-json).
 *
 * @param kind           the delivery kind
 * @param blocksPerTick  a beam's speed in blocks per tick
 * @param range          a stream's reach in blocks
 * @param coneDegrees    a stream's cone, apex to rim, in degrees
 * @param ticksPerCharge a stream's ticks of hold one cost pays for
 * @param grannyAllowed  whether an arc may lob onto a top face
 * @param particle       the particle a stream sprays along its cone, empty for none
 * @param transformAt    the share of the flight by which the blob has taken its traveling form
 * @param sound          the sound a stream makes while held, empty for none; the server plays it, so the
 *                       network copy carries none
 * @param chargeTicks    the ticks of hold a charged ability takes to charge fully, 0 for one that does not charge
 */
public record Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees,
                       int ticksPerCharge, boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                       Optional<StreamSound> sound, int chargeTicks) {

    /**
     * A delivery that does not charge.
     *
     * @param kind           the delivery kind
     * @param blocksPerTick  a beam's speed in blocks per tick
     * @param range          a stream's reach in blocks
     * @param coneDegrees    a stream's cone, apex to rim, in degrees
     * @param ticksPerCharge a stream's ticks of hold one cost pays for
     * @param grannyAllowed  whether an arc may lob onto a top face
     * @param particle       the particle a stream sprays along its cone, empty for none
     * @param transformAt    the share of the flight by which the blob has taken its traveling form
     * @param sound          the sound a stream makes while held, empty for none
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                NO_CHARGE);
    }

    /**
     * A delivery making no stream sound.
     *
     * @param kind           the delivery kind
     * @param blocksPerTick  a beam's speed in blocks per tick
     * @param range          a stream's reach in blocks
     * @param coneDegrees    a stream's cone, apex to rim, in degrees
     * @param ticksPerCharge a stream's ticks of hold one cost pays for
     * @param grannyAllowed  whether an arc may lob onto a top face
     * @param particle       the particle a stream sprays along its cone, empty for none
     * @param transformAt    the share of the flight by which the blob has taken its traveling form
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt,
                Optional.empty());
    }

    /** The charge ticks of a delivery that does not charge. */
    public static final int NO_CHARGE = 0;

    /** Codec for the charge block, {@code "charge": {"max_ticks": 60}}. */
    private static final Codec<Integer> CHARGE_CODEC = Codec.INT.fieldOf("max_ticks").codec();

    /** A beam's speed where the JSON names none. */
    public static final double DEFAULT_BLOCKS_PER_TICK = 2.5;
    /** A stream's cone where the JSON names none. */
    public static final double DEFAULT_CONE_DEGREES = 20;
    /** A stream's ticks per cost where the JSON names none. */
    public static final int DEFAULT_TICKS_PER_CHARGE = 20;
    /**
     * The share of the flight by which a blob has taken its traveling form
     * where the JSON names none (decision traveling-form-transforms-in-flight).
     */
    public static final double DEFAULT_TRANSFORM_AT = 0.4;

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
            Codec.BOOL.optionalFieldOf("granny", true).forGetter(Delivery::grannyAllowed),
            Identifier.CODEC.optionalFieldOf("particle").forGetter(Delivery::particle),
            Codec.DOUBLE.optionalFieldOf("transform_at", DEFAULT_TRANSFORM_AT).forGetter(Delivery::transformAt),
            // mycosis-spore-stream-buds-and-poisons
            StreamSound.CODEC.codec().optionalFieldOf("sound").forGetter(Delivery::sound),
            // nova-ring-grows-with-the-hold
            CHARGE_CODEC.optionalFieldOf("charge", NO_CHARGE).forGetter(Delivery::chargeTicks)
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
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), Delivery::particle,
            ByteBufCodecs.DOUBLE, Delivery::transformAt,
            ByteBufCodecs.VAR_INT, Delivery::chargeTicks,
            (kind, blocksPerTick, range, cone, ticksPerCharge, granny, particle, transformAt, chargeTicks) ->
                    new Delivery(kind, blocksPerTick, range, cone, ticksPerCharge, granny, particle, transformAt,
                            Optional.empty(), chargeTicks));

    /**
     * A delivery of the kind with every param at its default.
     *
     * @param kind the delivery kind
     * @return the delivery
     */
    public static Delivery of(DeliveryKind kind) {
        return new Delivery(kind, DEFAULT_BLOCKS_PER_TICK, 0, DEFAULT_CONE_DEGREES, DEFAULT_TICKS_PER_CHARGE, true,
                Optional.empty(), DEFAULT_TRANSFORM_AT);
    }

    /**
     * Whether the ability charges while held and fires once on release, its
     * strength the share of the charge the hold reached.
     *
     * @return true for a delivery naming a charge
     */
    public boolean charges() {
        return chargeTicks > NO_CHARGE;
    }

    /**
     * The share of a full charge a hold reached.
     *
     * @param heldTicks the ticks the use key was held
     * @return 0 to 1; 1 for a delivery that does not charge
     */
    public float chargeShare(int heldTicks) {
        return charges() ? Math.clamp((float) heldTicks / chargeTicks, 0f, 1f) : 1f;
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
     * Whether the throw's path is a straight line with no peak: a beam's, or
     * a rolling goo's, which flies with no gravity, so its aim previews
     * straight (decision orb-carries-a-swirling-nova).
     *
     * @return true for a beam or a rolling delivery
     */
    public boolean aimsStraight() {
        return fliesStraight() || rolls();
    }

    /**
     * Whether the glove aims a line at a target, the arc or the beam; a
     * stream aims a cone and a self ability at nothing.
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
     * Whether a throw rolls through the air rather than flying an arc: an arc
     * naming a range and a speed below a beam's default rolls in a straight
     * line at that speed for that range, with no gravity, the way Frost's Orb
     * does (decision orb-carries-a-swirling-nova).
     *
     * @return true for a slow arc naming a range
     */
    public boolean rolls() {
        return kind == DeliveryKind.ARC && range > 0 && blocksPerTick < DEFAULT_BLOCKS_PER_TICK;
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
        if (aimsStraight()) {
            return 0;
        }
        return grannyArc && grannyAllowed ? ThrowArc.lobPeak(start, end) : ThrowArc.basePeak(start.distanceTo(end));
    }
}
