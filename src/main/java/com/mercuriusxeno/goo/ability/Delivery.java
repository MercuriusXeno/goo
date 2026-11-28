package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
 * @param range          how far the ability aims and lands, a stream's reach and a scan's read; 0 where
 *                       it names none (decision each-ability-names-its-range-and-the-shared-throw-range-retires)
 * @param coneDegrees    a stream's cone, apex to rim, in degrees
 * @param ticksPerCharge a stream's ticks of hold one cost pays for
 * @param grannyAllowed  whether an arc may lob onto a top face
 * @param particle       the particle a stream sprays along its cone, empty for none
 * @param transformAt    the share of the flight by which the blob has taken its traveling form
 * @param sound          the sound a stream makes while held, empty for none; the server plays it, and the
 *                       holder's client loops it where it loops
 * @param charge         the charge block: how long a hold charges and what a slinging release throws
 * @param form           the form the goo takes in flight, by the share transformAt names
 * @param arcSpeed       the speed an arc flies at in blocks per tick, in place of its type's flight time;
 *                       {@link #NO_ARC_SPEED} for none
 * @param seekRange      how far a stream reaches the living it strikes, past the range it works blocks within;
 *                       {@link #NO_SEEK_RANGE} for the range itself
 * @param drainsOnPlacement whether a stream pays its cost by what it lays rather than by the ticks it is held
 * @param throwSpeed     the speed a stream throws its drips at, blocks per tick, each flying its arc until it
 *                       strikes; {@link #NO_THROW_SPEED} for a stream reaching by its range
 * @param dripsPerTick   the drips a stream throwing drips throws a tick, each at its own place in the cone;
 *                       {@link #WHOLE_FAN} throws as many as the cone's fan holds
 * @param spew           what a charm-like cast's release sends at the mob its press took, the blob for any other
 * @param reachesBlockReach whether a stream reaches as far as its caster's block reach in place of its range
 * @param peakShare      the share of the base peak an arc flies, {@link #FULL_PEAK} for the whole
 * @param formSounds     the sounds a form formed in the hand makes as it forms and as it reverts
 */
public record Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees,
                       int ticksPerCharge, boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                       Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed,
                       double seekRange, boolean drainsOnPlacement, double throwSpeed, int dripsPerTick,
                       Spew spew, boolean reachesBlockReach, double peakShare, FormSounds formSounds) {

    /**
     * A delivery whose form makes no sound as it forms or reverts in the hand.
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
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed        the speed a stream throws its drips at
     * @param dripsPerTick      the drips a stream throws a tick
     * @param spew              what a charm-like cast's release sends
     * @param reachesBlockReach whether a stream reaches as far as its caster's block reach
     * @param peakShare         the share of the base peak an arc flies
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange,
                    boolean drainsOnPlacement, double throwSpeed, int dripsPerTick, Spew spew,
                    boolean reachesBlockReach, double peakShare) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, dripsPerTick, spew,
                reachesBlockReach, peakShare, FormSounds.NONE);
    }

    /**
     * A delivery whose arc flies the whole base peak.
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
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed        the speed a stream throws its drips at
     * @param dripsPerTick      the drips a stream throws a tick
     * @param spew              what a charm-like cast's release sends
     * @param reachesBlockReach whether a stream reaches as far as its caster's block reach
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange,
                    boolean drainsOnPlacement, double throwSpeed, int dripsPerTick, Spew spew,
                    boolean reachesBlockReach) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, dripsPerTick, spew,
                reachesBlockReach, FULL_PEAK);
    }

    /**
     * A delivery reaching by its own range.
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
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed        the speed a stream throws its drips at
     * @param dripsPerTick      the drips a stream throws a tick
     * @param spew              what a charm-like cast's release sends
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange,
                    boolean drainsOnPlacement, double throwSpeed, int dripsPerTick, Spew spew) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, dripsPerTick, spew, false);
    }

    /**
     * A delivery throwing as many drips a tick as the cone's fan holds, where it throws any, whose release
     * sends the blob itself.
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
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed        the speed a stream throws its drips at
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange,
                    boolean drainsOnPlacement, double throwSpeed) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, WHOLE_FAN, Spew.BLOB);
    }

    /**
     * A delivery throwing drips at a pace, whose release sends the blob itself.
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
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed        the speed a stream throws its drips at
     * @param dripsPerTick      the drips a stream throws a tick
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange,
                    boolean drainsOnPlacement, double throwSpeed, int dripsPerTick) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, dripsPerTick, Spew.BLOB);
    }

    /**
     * A delivery whose release sends what a charm-like cast spews, throwing as many drips a tick as the cone's
     * fan holds, where it throws any.
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
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed        the speed a stream throws its drips at
     * @param spew              what a charm-like cast's release sends
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange,
                    boolean drainsOnPlacement, double throwSpeed, Spew spew) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, WHOLE_FAN, spew);
    }

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
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange,
                    boolean drainsOnPlacement) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, drainsOnPlacement, NO_THROW_SPEED);
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
     * @param arcSpeed       the speed an arc flies at in blocks per tick
     * @param seekRange      how far a stream reaches the living it strikes
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed, double seekRange) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, seekRange, false);
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
     * @param arcSpeed       the speed an arc flies at in blocks per tick
     */
    public Delivery(DeliveryKind kind, double blocksPerTick, double range, double coneDegrees, int ticksPerCharge,
                    boolean grannyAllowed, Optional<Identifier> particle, double transformAt,
                    Optional<StreamSound> sound, Charge charge, TravelForm form, double arcSpeed) {
        this(kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
                charge, form, arcSpeed, NO_SEEK_RANGE);
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
                charge, form, NO_ARC_SPEED);
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
    /** The arc speed of a delivery that names none: its flight keeps its type's time. */
    public static final double NO_ARC_SPEED = 0;
    /** The peak share of a delivery that names none: its arc flies the whole base peak. */
    public static final double FULL_PEAK = 1;
    /** The seek range of a stream that names none: it strikes the living within its range. */
    public static final double NO_SEEK_RANGE = 0;
    /** The throw speed of a stream that names none: it reaches by its range and throws no drips. */
    public static final double NO_THROW_SPEED = 0;
    /** The drips a tick of a stream naming no pace throws: one along every share of the cone's fan. */
    public static final int WHOLE_FAN = CoatRay.FAN_SHARES;

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
            // dart-flies-low-and-straight-at-double-speed
            Codec.DOUBLE.optionalFieldOf("arc_speed", NO_ARC_SPEED).forGetter(Delivery::arcSpeed),
            // decay-gnats-degrade-each-block-once
            Codec.DOUBLE.optionalFieldOf("seek_range", NO_SEEK_RANGE).forGetter(Delivery::seekRange),

            // goop-drains-per-cubic-meter-of-slime-placed
            Codec.BOOL.optionalFieldOf("drains_on_placement", false).forGetter(Delivery::drainsOnPlacement),
            // goop-and-slick-coat-lands-where-each-drip-arc-strikes
            Codec.DOUBLE.optionalFieldOf("throw_speed", NO_THROW_SPEED).forGetter(Delivery::throwSpeed),
            CodecTail.CODEC.forGetter(delivery -> new CodecTail(delivery.dripsPerTick(), delivery.spew(),
                    delivery.reachesBlockReach(), delivery.peakShare(), delivery.formSounds().form(),
                    delivery.formSounds().revert(), delivery.formSounds().thrown(),
                    delivery.formSounds().throwPitchSpread()))
    ).apply(inst, (kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt,
                   sound, charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, tail) -> new Delivery(
            kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
            charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, tail.dripsPerTick(), tail.spew(),
            tail.reachesBlockReach(), tail.peakShare(), new FormSounds(tail.formSound(), tail.revertSound(),
            tail.throwSound(), tail.throwPitchSpread()))));

    /**
     * The delivery block's last fields as one, since a record codec groups at most sixteen; their keys stay
     * at the delivery block's top level.
     *
     * @param dripsPerTick      the drips a stream throws a tick
     * @param spew              what a charm-like cast's release sends
     * @param reachesBlockReach whether a stream reaches as far as its caster's block reach
     * @param peakShare         the share of the base peak an arc flies
     * @param formSound         the sound a form formed in the hand makes as it begins forming
     * @param revertSound       the sound a form formed in the hand makes as it begins reverting
     * @param throwSound        the sound each one of a held throw's stack makes as it is thrown
     * @param throwPitchSpread  how far either side of its pitch each throw's sound may play
     */
    private record CodecTail(int dripsPerTick, Spew spew, boolean reachesBlockReach, double peakShare,
                             Optional<SoundCue> formSound, Optional<SoundCue> revertSound,
                             Optional<SoundCue> throwSound, float throwPitchSpread) {

        private static final MapCodec<CodecTail> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                // goop-drains-per-cubic-meter-of-slime-placed: the spray's pace is how many drips land a tick
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("drips_per_tick", WHOLE_FAN)
                        .forGetter(CodecTail::dripsPerTick),
                // charm-like-abilities-lock-the-highlighted-mob-on-right-click
                Spew.CODEC.optionalFieldOf("spews", Spew.BLOB).forGetter(CodecTail::spew),
                // bore-reaches-as-far-as-your-block-reach
                Codec.BOOL.optionalFieldOf("reaches_block_reach", false).forGetter(CodecTail::reachesBlockReach),
                // dart-flies-low-and-straight-at-double-speed
                Codec.DOUBLE.optionalFieldOf("peak_share", FULL_PEAK).forGetter(CodecTail::peakShare),
                // dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
                SoundCue.CODEC.optionalFieldOf("form_sound").forGetter(CodecTail::formSound),
                SoundCue.CODEC.optionalFieldOf("revert_sound").forGetter(CodecTail::revertSound),
                // knives-and-javelins-sound-as-each-forms-and-is-thrown
                SoundCue.CODEC.optionalFieldOf("throw_sound").forGetter(CodecTail::throwSound),
                Codec.FLOAT.optionalFieldOf("throw_pitch_spread", 0f).forGetter(CodecTail::throwPitchSpread)
        ).apply(inst, CodecTail::new));
    }

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
            delivery -> new WireTail(delivery.arcSpeed(), delivery.seekRange(), delivery.drainsOnPlacement(),
                    delivery.throwSpeed(), delivery.dripsPerTick(), delivery.spew(), delivery.reachesBlockReach(),
                    delivery.peakShare(), delivery.formSounds()),
            (kind, blocksPerTick, range, coneDegrees, ticksPerCharge, grannyAllowed, particle, transformAt, sound,
             charge, form, tail) -> new Delivery(kind, blocksPerTick, range, coneDegrees, ticksPerCharge,
                    grannyAllowed, particle, transformAt, sound, charge, form, tail.arcSpeed(), tail.seekRange(),
                    tail.drainsOnPlacement(), tail.throwSpeed(), tail.dripsPerTick(), tail.spew(),
                    tail.reachesBlockReach(), tail.peakShare(), tail.formSounds()));

    /**
     * The delivery's last fields on the wire as one, since a stream codec
     * composes at most twelve.
     *
     * @param arcSpeed          the speed an arc flies at in blocks per tick
     * @param seekRange         how far a stream reaches the living it strikes
     * @param drainsOnPlacement whether a stream pays its cost by what it lays
     * @param throwSpeed        the speed a stream throws its drips at
     * @param dripsPerTick      the drips a stream throws a tick
     * @param spew              what a charm-like cast's release sends
     * @param reachesBlockReach whether a stream reaches as far as its caster's block reach
     * @param peakShare         the share of the base peak an arc flies
     * @param formSounds        the sounds a form formed in the hand makes
     */
    private record WireTail(double arcSpeed, double seekRange, boolean drainsOnPlacement, double throwSpeed,
                            int dripsPerTick, Spew spew, boolean reachesBlockReach, double peakShare,
                            FormSounds formSounds) {

        private static final StreamCodec<ByteBuf, WireTail> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, WireTail::arcSpeed,
                ByteBufCodecs.DOUBLE, WireTail::seekRange,
                ByteBufCodecs.BOOL, WireTail::drainsOnPlacement,
                ByteBufCodecs.DOUBLE, WireTail::throwSpeed,
                ByteBufCodecs.VAR_INT, WireTail::dripsPerTick,
                Spew.STREAM_CODEC, WireTail::spew,
                ByteBufCodecs.BOOL, WireTail::reachesBlockReach,
                ByteBufCodecs.DOUBLE, WireTail::peakShare,
                FormSounds.STREAM_CODEC, WireTail::formSounds,
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
     * Whether the delivery is the charm-like cast: the press takes the
     * highlighted mob, the release darts the blob straight to it and envelops it.
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     *
     * @return true for an envelop delivery
     */
    public boolean envelops() {
        return kind == DeliveryKind.ENVELOP;
    }

    /**
     * Whether the throw's path is a straight line with no peak: a beam's, a
     * rolling goo's, which flies with no gravity, so its aim previews
     * straight (decision orb-carries-a-swirling-nova), or the blob darting
     * to the mob a charm-like cast took.
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     *
     * @return true for a beam, a rolling delivery or an envelop
     */
    public boolean aimsStraight() {
        return fliesStraight() || rolls() || envelops();
    }

    /**
     * Whether the hold forms a fan of knives rather than one blob thrown
     * along a line: an arc whose charge forms knives in stages, which shows
     * no aim line of any kind.
     * knives-aims-a-spread-fan-not-a-blob-line
     * knives-emerge-from-the-blob-and-hover-in-a-fan
     *
     * @return true for an arc whose charge forms knives
     */
    public boolean slingsAFan() {
        return formsKnives();
    }

    /**
     * Whether the hold forms Knives' knives: an arc whose charge stacks, in the blob form.
     * decision knives-charge-one-per-three-ticks-up-to-ten
     *
     * @return true for a held throw stacking knives
     */
    public boolean formsKnives() {
        return kind == DeliveryKind.ARC && charge.stacks() && !form.morphs();
    }

    /**
     * Whether the hold stacks javelins, Dart's or Spear's: a charge that stacks, in a form that morphs.
     * decision dart-stacks-three-javelins-one-per-ten-ticks
     * decision spear-is-a-barbed-javelin-in-the-dart-family
     *
     * @return true for a held throw stacking javelins
     */
    public boolean stacksJavelins() {
        return charge.stacks() && form.morphs();
    }

    /**
     * Whether the glove aims a line at a target, the arc or the beam; an arc
     * slinging a fan aims no line, a stream aims a cone and a self ability at
     * nothing.
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
     * The ticks a flight takes to cover a distance: a beam, or an envelop's
     * darting blob, at its speed, an arc at the arc speed it names, and an
     * arc naming none by the thrown type's flight time.
     * dart-flies-low-and-straight-at-double-speed
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     *
     * @param distance       world-space distance in blocks
     * @param levity         the thrown type's multiplier on the root of distance
     * @param baseFlightTime the thrown type's ticks of flight before distance adds any
     * @return travel ticks, always at least 1
     */
    public int travelTicks(double distance, float levity, int baseFlightTime) {
        if (fliesStraight() || envelops()) {
            return (int) Math.max(1, Math.ceil(distance / blocksPerTick));
        }
        if (arcSpeed > NO_ARC_SPEED) {
            return (int) Math.max(1, Math.ceil(distance / arcSpeed));
        }
        return (int) ThrowArc.travelTicks(distance, levity, baseFlightTime);
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
     * Whether the palm blob takes its travel form in the hand as the press
     * lifts it, so its thrown goo entity leaves already in that form: a form
     * that morphs, named with no transform point in flight. A form named with
     * a transform point above zero morphs in flight instead.
     * shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob
     *
     * @return true for a morphing form at a transform point of zero
     */
    public boolean morphsInHand() {
        return form.morphs() && transformAt <= 0;
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
     * allows, the share of the base peak the delivery names otherwise.
     * dart-flies-low-and-straight-at-double-speed
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
        return grannyArc && grannyAllowed ? ThrowArc.lobPeak(start, end)
                : ThrowArc.basePeak(start.distanceTo(end)) * peakShare;
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
     * @return true for a stream naming a throw speed
     */
    public boolean throwsDrips() {
        return kind == DeliveryKind.STREAM && throwSpeed > NO_THROW_SPEED;
    }

    /**
     * The speed a stream's drips leave at, thrown by a caster moving at a
     * horizontal speed: the throw speed plus that speed, along the aim, so a
     * running caster's spray lands ahead of them as far as it would at rest;
     * a stream throwing no drips keeps its throw speed of none.
     * goop-and-slick-drips-leave-at-one-exit-speed
     * goop-and-slick-drips-add-none-of-the-players-movement: the speed grows, the heading stays the aim's
     *
     * @param casterHorizontalSpeed the caster's horizontal speed, blocks per tick
     * @return the drips' exit speed, blocks per tick
     */
    public double exitSpeedAt(double casterHorizontalSpeed) {
        return throwsDrips() ? throwSpeed + casterHorizontalSpeed : throwSpeed;
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
                transformAt, sound, charge, form, arcSpeed, seekRange, drainsOnPlacement, throwSpeed, dripsPerTick,
                spew, reachesBlockReach, peakShare, formSounds);
    }

    /**
     * This delivery as it reaches for a caster: out to the caster's block
     * reach for a stream reaching that far, which Remote raises, and by
     * its own range otherwise.
     * bore-reaches-as-far-as-your-block-reach
     *
     * @param blockReach the caster's block reach, in blocks
     * @return the delivery reaching that far, or this one
     */
    public Delivery reachingTo(double blockReach) {
        return reachesBlockReach ? withCone(blockReach, coneDegrees) : this;
    }

    /**
     * Whether a charm-like cast's release spews a shulker bullet from the
     * held blob in place of the blob darting out.
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     *
     * @return true for an envelop naming the shulker bullet
     */
    public boolean spewsAShulkerBullet() {
        return envelops() && spew == Spew.SHULKER_BULLET;
    }
}
