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
 * @param blocksPerTick  a beam's speed in blocks per tick, and a scan's wave speed
 * @param range          a stream's reach in blocks, and how far a scan reads along the look
 * @param coneDegrees    a stream's cone, apex to rim, in degrees
 * @param ticksPerCharge a stream's ticks of hold one cost pays for
 * @param grannyAllowed  whether an arc may lob onto a top face
 * @param particle       the particle a stream sprays along its cone, empty for none
 * @param transformAt    the share of the flight by which the blob has taken its traveling form
 * @param sound          the sound a stream makes while held, empty for none; the server plays it, and the
 *                       holder's client loops it where it loops
 * @param charge         the charge block: how long a hold charges and what a slinging release throws
 * @param form           the form the goo takes in flight, by the share transformAt names
 * @param topSpeed       the fastest an arc flies in blocks per tick, slowing a throw its type would fly faster;
 *                       {@link #NO_TOP_SPEED} for none
 * @param seekRange      how far a stream reaches the living it strikes, past the range it works blocks within;
 *                       {@link #NO_SEEK_RANGE} for the range itself
 * @param drainsOnPlacement whether a stream pays its cost by what it lays rather than by the ticks it is held
 * @param throwSpeed     the speed a stream throws its drips at, blocks per tick, each flying its arc until it
 *                       strikes; {@link #NO_THROW_SPEED} for a stream reaching by its range
 */
public record Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees,
                       int ticksPerCharge, boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                       Optional<StreamSound> sound, Charge charge, TravelForm form, double topSpeed,
                       double seekRange, boolean drainsOnPlacement, double throwSpeed) {

    /**
     * A delivery reaching by its range, throwing no drips.
     *
     * @param kind              the delivery kind
     * @param blocksPerTick     a beam's speed in blocks per tick
     * @param range             a stream's reach in blocks
     * @param coneDegrees       a stream's cone, apex to rim, in degrees
     * @param ticksPerCharge    a stream's ticks of hold one cost pays for
     * @param grannyAllowed     whether an arc may lob onto a top face
     * @param particle          the particle a stream sprays along its cone, empty for none
     * @param transformAt       the share of the flight by which the blob has taken its traveling form
     * @param sound             the sound a stream makes while held, empty for none
     * @param charge            the charge block
     * @param form              the form the goo takes in flight
     * @param topSpeed          the fastest an arc flies in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double topSpeed, double seekRange,
                    boolean drainsOnPlacement) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, topSpeed, seekRange, drainsOnPlacement, NO_THROW_SPEED);
    }

    /**
     * A delivery draining by the ticks it is held.
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
     * @param charge         the charge block
     * @param form           the form the goo takes in flight
     * @param topSpeed       the fastest an arc flies in blocks per tick
     * @param seekRange      how far a stream reaches the living it strikes
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double topSpeed, double seekRange) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, topSpeed, seekRange, false);
    }

    /**
     * A delivery whose stream strikes the living within its range.
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
     * @param charge         the charge block
     * @param form           the form the goo takes in flight
     * @param topSpeed       the fastest an arc flies in blocks per tick
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double topSpeed) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, topSpeed, NO_SEEK_RANGE);
    }

    /**
     * A delivery naming no top speed.
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
     * @param charge         the charge block
     * @param form           the form the goo takes in flight
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, NO_TOP_SPEED);
    }

    /**
     * A delivery whose charge slings nothing and whose goo stays a blob.
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
     * @param chargeTicks    the ticks of hold a charged ability takes to charge fully, 0 for one that does not charge
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, int chargeTicks) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                Charge.of(chargeTicks), TravelForm.BLOB);
    }

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
    /** The top speed of a delivery that names none: its flight keeps its type's time. */
    public static final double NO_TOP_SPEED = 0;
    /** The seek range of a stream that names none: it strikes the living within its range. */
    public static final double NO_SEEK_RANGE = 0;
    /** The throw speed of a stream that names none: it reaches by its range and throws no drips. */
    public static final double NO_THROW_SPEED = 0;

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
            Charge.CODEC.optionalFieldOf("charge", Charge.NONE).forGetter(Delivery::charge),
            // traveling-form-transforms-in-flight
            TravelForm.CODEC.optionalFieldOf("form", TravelForm.BLOB).forGetter(Delivery::form),
            // dart-flies-at-a-speed-you-can-follow
            Codec.DOUBLE.optionalFieldOf("top_speed", NO_TOP_SPEED).forGetter(Delivery::topSpeed),
            // decay-gnats-degrade-each-block-once
            Codec.DOUBLE.optionalFieldOf("seek_range", NO_SEEK_RANGE).forGetter(Delivery::seekRange),

            // goop-drains-per-cubic-meter-of-slime-placed
            Codec.BOOL.optionalFieldOf("drains_on_placement", false).forGetter(Delivery::drainsOnPlacement),
            // goop-and-slick-coat-lands-where-each-drip-arc-strikes
            Codec.DOUBLE.optionalFieldOf("throw_speed", NO_THROW_SPEED).forGetter(Delivery::throwSpeed)
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
            ByteBufCodecs.optional(StreamSound.STREAM_CODEC), Delivery::sound,
            Charge.STREAM_CODEC, Delivery::charge,
            TravelForm.STREAM_CODEC, Delivery::form,
            WireTail.STREAM_CODEC,
            delivery -> new WireTail(delivery.topSpeed(), delivery.seekRange(), delivery.drainsOnPlacement(),
                    delivery.throwSpeed()),
            (kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
             charge, form, tail) -> new Delivery(kind, blocksPerTick, range, coneDegrees, ticksPerCharge,
                    grannyAllowed, particle, transformAt, sound, charge, form, tail.topSpeed(), tail.seekRange(),
                    tail.drainsOnPlacement(), tail.throwSpeed()));

    /**
     * The delivery's last fields on the wire as one, since a stream codec
     * composes at most twelve.
     *
     * @param topSpeed         the fastest an arc flies in blocks per tick
     * @param seekRange        how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed       the speed a stream throws its drips at
     */
    private record WireTail(double topSpeed, double seekRange, boolean drainsOnPlacement, double throwSpeed) {

        private static final StreamCodec<ByteBuf, WireTail> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, WireTail::topSpeed,
                ByteBufCodecs.DOUBLE, WireTail::seekRange,
                ByteBufCodecs.BOOL, WireTail::drainsOnPlacement,
                ByteBufCodecs.DOUBLE, WireTail::throwSpeed,
                WireTail::new);
    }

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
        return charge.maxTicks() > NO_CHARGE;
    }

    /**
     * The ticks of hold a full charge takes.
     *
     * @return the ticks, 0 for a delivery that does not charge
     */
    public int chargeTicks() {
        return charge.maxTicks();
    }

    /**
     * The share of a full charge a hold reached.
     *
     * @param heldTicks the ticks the use key was held
     * @return 0 to 1; 1 for a delivery that does not charge
     */
    public float chargeShare(int heldTicks) {
        return charges() ? Math.clamp((float) heldTicks / charge.maxTicks(), 0f, 1f) : 1f;
    }

    /**
     * Whether the hold sizes the cast: a spontaneous cast whose charge names
     * a largest size.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @return true for a cast sized by its charge
     */
    public boolean sizesByCharge() {
        return kind == DeliveryKind.SPONTANEOUS && charge.sizes();
    }

    /**
     * The whole stage a hold charges the cast to: growing with the ticks held
     * up to the cap its charge names, and no larger past it.
     * decision blast-charges-from-two-to-seven-blocks
     *
     * @param heldTicks the ticks the use key was held
     * @return the stage's size in blocks
     */
    public double stageAt(int heldTicks) {
        return charge.stageAt(heldTicks);
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
     * Whether the release slings a fan of flecks across a cone rather than
     * one blob along a line: an arc whose charge slings flecks, which aims the
     * spread fan in place of the line.
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @return true for an arc whose charge slings
     */
    public boolean slingsAFan() {
        return kind == DeliveryKind.ARC && charge.slings();
    }

    /**
     * Whether the glove aims a line at a target, the arc or the beam; an arc
     * slinging a fan aims the fan instead, a stream aims a cone and a self
     * ability at nothing.
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @return true for a beam, or an arc throwing one blob
     */
    public boolean aimsALine() {
        return (kind == DeliveryKind.ARC || kind == DeliveryKind.BEAM) && !slingsAFan();
    }

    /**
     * Whether the ability lands its program at the aimed target as one
     * strike: an arc thrown there, or a spontaneous cast made there, which
     * previews the area it will strike alike.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @return true for an arc or a spontaneous cast
     */
    public boolean strikesTheAim() {
        return kind == DeliveryKind.ARC || kind == DeliveryKind.SPONTANEOUS;
    }

    /**
     * The ticks a flight takes to cover a distance: a beam at its speed, an
     * arc by the thrown type's flight time, or by its top speed where that
     * flies it slower, so the eye follows a dart across its arc.
     * dart-flies-at-a-speed-you-can-follow
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
        int typeTicks = (int) ThrowArc.travelTicks(distance, levity, baseFlightTime);
        return topSpeed > NO_TOP_SPEED ? Math.max(typeTicks, (int) Math.ceil(distance / topSpeed)) : typeTicks;
    }

    /**
     * The ticks a scan's wave takes to reach its landing at the delivery's
     * speed, the delay before its program runs there.
     * click-is-a-hit-scan-drawn-as-a-delayed-wave
     *
     * @param distance world-space distance from the eye to the landing, in blocks
     * @return the delay in ticks, always at least 1
     */
    public int waveTicks(double distance) {
        return (int) Math.max(1, Math.ceil(distance / blocksPerTick));
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

    /**
     * How far a stream reaches the living it strikes.
     *
     * @return the reach in blocks
     */
    public double livingReach() {
        return seekRange > NO_SEEK_RANGE ? seekRange : range;
    }

    /**
     * Whether a stream throws drips that fly their arcs until they strike,
     * reaching however far the arcs carry them, rather than reaching by its range.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @return true for a delivery naming a throw speed
     */
    public boolean throwsDrips() {
        return throwSpeed > NO_THROW_SPEED;
    }

    /**
     * This delivery with a stream's cone reshaped, every other param kept.
     *
     * @param newRange       the stream's reach in blocks
     * @param newConeDegrees the stream's cone, apex to rim, in degrees
     * @return the reshaped delivery
     */
    public Delivery withCone(double newRange, double newConeDegrees) {
        return new Delivery(kind, blocksPerTick, newRange, newConeDegrees, ticksPerCharge, grannyAllowed, particle,
                transformAt, sound, charge, form, topSpeed, seekRange, drainsOnPlacement, throwSpeed);
    }
}
