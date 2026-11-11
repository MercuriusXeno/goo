package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Telekinesis;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.GateRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Kinetic's Grab, run each tick of a held channel on the channeling player:
 * the first tick lifts the entity the glove outlines within range, and every tick
 * after holds it in front of the player along the look, from the far
 * distance with the look level or above, drawn in to the near distance as
 * the look pitches down to {@link #FULL_DRAW_PITCH}. A left click while held
 * throws it at the throw speed (GrabEvents.throwHeld), and the press lifts
 * nothing more until right click lets go. An outlined item entity is
 * pulled to the player at the pull speed each tick instead, unheld, and
 * picked up on arrival.
 * decision grab-holds-and-throws-a-physics-body
 * decision grab-pulls-an-item-to-you-and-picks-it-up
 * decision outlined-target-is-the-one-pick
 *
 * @param range      how far the look reaches for an entity to lift, in blocks, before Telekinesis adds its reach
 * @param near       how far from the eye the held entity hangs with the look pitched fully down
 * @param far        how far from the eye the held entity hangs with the look level or above
 * @param throwSpeed the speed a throw launches the held entity at, in blocks per tick
 * @param pullSpeed  the speed a pulled item entity flies to the player at, in blocks per tick
 */
public record GrabStep(double range, double near, double far, double throwSpeed, double pullSpeed)
        implements ReachesTheAim {

    /** The downward pitch, in degrees, at which the held entity is drawn fully in to the near distance. */
    public static final float FULL_DRAW_PITCH = 60f;
    private static final String NAME = "grab";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_NEAR = "near";
    private static final String FIELD_FAR = "far";
    private static final String FIELD_THROW_SPEED = "throw_speed";
    private static final String FIELD_PULL_SPEED = "pull_speed";
    private static final double HALF = 0.5;

    /** Codec for the step's params. */
    public static final MapCodec<GrabStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RANGE).forGetter(GrabStep::range),
            Codec.DOUBLE.fieldOf(FIELD_NEAR).forGetter(GrabStep::near),
            Codec.DOUBLE.fieldOf(FIELD_FAR).forGetter(GrabStep::far),
            Codec.DOUBLE.fieldOf(FIELD_THROW_SPEED).forGetter(GrabStep::throwSpeed),
            Codec.DOUBLE.fieldOf(FIELD_PULL_SPEED).forGetter(GrabStep::pullSpeed)
    ).apply(inst, GrabStep::new));

    /** The registered type. */
    public static final StepType<GrabStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<GrabStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (!(context.hostAs(TargetHost.class).target() instanceof ServerPlayer player)
                || GrabEvents.extendSpentPress(player)) {
            // decision diagnose-and-correct-grabs-left-click-throw: a throw spends the press until right click lets go
            return true;
        }
        Entity held = GrabEvents.heldBy(player);
        if (held == null) {
            held = sentTarget(player, context.hostAs(ChannelHost.class));
            if (held != null) {
                GrabEvents.bindThrough(player, held, aimedThrough(player, held));
            }
        }
        if (held instanceof ItemEntity item) {
            Vec3 eye = player.getEyePosition();
            GrabEvents.pull(player, item, pullSpeed, throughGates(player, item, item.position(), eye).orElse(eye));
        } else if (held != null) {
            Vec3 middle = held.getBoundingBox().getCenter();
            Vec3 lift = new Vec3(0, held.getBbHeight() * HALF, 0);
            Vec3 hold = holdPoint(player, held);
            Entity body = held;
            Optional<Vec3> gate = boundGate(player, body).map(GateRay::intoGate)
                    .or(() -> throughGates(player, body, middle, hold.add(lift)));
            if (gate.isPresent()) {
                Vec3 toGate = gate.get().subtract(middle);
                GrabEvents.drawToward(player, held, toGate.normalize().scale(Math.min(pullSpeed, toGate.length())),
                        throwSpeed);
            } else {
                GrabEvents.hold(player, held, hold, throwSpeed);
            }
        }
        return true;
    }

    /**
     * The gate on a body's side a grab reached it through: the first linked
     * gate the player's look passes before it reaches the body, so a body
     * aimed at through a portal is drawn back through that portal whether or
     * not a straight way to the holder stands open.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param player the grabbing player
     * @param body   the body taken
     * @return the partner of the gate the look passed, empty where the look reached the body straight
     */
    private Optional<Gate> aimedThrough(ServerPlayer player, Entity body) {
        Vec3 eye = player.getEyePosition();
        double reach = reachWith(Telekinesis.entityReachLaid(player));
        double straight = eye.distanceTo(body.getBoundingBox().getCenter());
        return GateRay.first(GatePassage.pairsOf(player.level().getServer()), player.level().dimension(), eye,
                        eye.add(player.getLookAngle().scale(reach)))
                .filter(crossing -> eye.distanceTo(crossing.facePoint()) < straight)
                .map(GateRay.Crossing::far);
    }

    /**
     * The gate a held body is still drawn through: the one its grab is bound
     * to, while that gate stands linked.
     *
     * @param player the holding player
     * @param body   the held body
     * @return the gate, empty once the pair carried it out, for a straight grab, or once the gate is gone
     */
    private static Optional<Gate> boundGate(ServerPlayer player, Entity body) {
        List<GatePassage.Pair> pairs = GatePassage.pairsOf(player.level().getServer());
        return GrabEvents.boundThrough(player, body).filter(gate -> GateRay.partnerIn(pairs, gate).isPresent());
    }

    /**
     * Where a body Grab draws heads where a linked pair offers the shorter
     * way to the end, the pair read as ordinary space: through the middle of
     * the gate's opening, so the body crosses its face and the pair carries
     * it out, rather than dragged the long way round, across the room and
     * through the holder, or at the walls between, as the operator found a
     * slime pulled back out of a portal come at them from behind.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param player the holding player
     * @param body   the drawn body
     * @param from   where the body stands
     * @param to     where Grab draws it
     * @return where it heads through a gate, empty where it heads straight for the end
     */
    private static Optional<Vec3> throughGates(ServerPlayer player, Entity body, Vec3 from, Vec3 to) {
        boolean straightOpen = inSight(player, from, to);
        double straight = from.distanceTo(to);
        // A way whose far leg is walled too reaches nothing, and the body would go round the pair for ever.
        return GateRay.shortestRoute(GatePassage.pairsOf(player.level().getServer()), from, to)
                .filter(route -> !straightOpen || route.length() < straight)
                .filter(route -> inSight(player, route.out().center()
                        .add(route.out().face().getUnitVec3().scale(HALF)), to))
                .map(GateRay.Route::headFor);
    }

    /**
     * Whether nothing solid stands on the straight line between two points,
     * read as the bare world: a body's own context would read a linked gate's
     * blocks open while the body stands at the gate, and see straight through
     * the wall to the far side of it, though no straight way runs there.
     *
     * @param player the holding player
     * @param from   the line's start
     * @param to     its end
     * @return true where the line runs clear
     */
    private static boolean inSight(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /**
     * How far Grab reaches to a body: straight where nothing stands between,
     * else along the shortest way through a linked pair where one runs.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param player the grabbing player
     * @param body   the body
     * @return the squared reach to its middle
     */
    private static double reachedSquared(ServerPlayer player, Entity body) {
        Vec3 eye = player.getEyePosition();
        Vec3 middle = body.getBoundingBox().getCenter();
        double straight = eye.distanceTo(middle);
        double length = inSight(player, eye, middle) ? straight
                : GateRay.shortestRoute(GatePassage.pairsOf(player.level().getServer()), eye, middle)
                        .map(GateRay.Route::length).orElse(straight);
        return length * length;
    }

    /**
     * How far from the eye the held entity hangs at a pitch: the far
     * distance with the look level or above, falling in a straight line to
     * the near distance as the look pitches down to FULL_DRAW_PITCH.
     *
     * @param pitch the look's pitch in degrees, positive looking down
     * @param near  the distance with the look pitched fully down
     * @param far   the distance with the look level or above
     * @return the distance, in blocks
     */
    public static double holdDistance(float pitch, double near, double far) {
        double drawn = Math.clamp(pitch / FULL_DRAW_PITCH, 0f, 1f);
        return far - (far - near) * drawn;
    }

    /**
     * Where a held entity's feet go this tick: its middle on the look at the
     * hold distance, the look carried through each linked gate it passes, so
     * a body held by a look aimed into a portal hangs where the look ends past
     * the partner, as seen through the portal, rather than dragged at the wall
     * behind it and round the pair every tick.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param player the holding player
     * @param held   the held entity
     * @return the feet position
     */
    private Vec3 holdPoint(ServerPlayer player, Entity held) {
        double distance = holdDistance(player.getXRot(), near, far);
        Vec3 eye = player.getEyePosition();
        List<GatePassage.Pair> pairs = GatePassage.pairsOf(player.level().getServer());
        Vec3 middle = lookEndThroughGates(pairs, player.level().dimension(), eye,
                eye.add(player.getLookAngle().scale(distance)));
        return clearOfTheFaces(pairs, middle, held.getBbWidth()).subtract(0, held.getBbHeight() * HALF, 0);
    }

    /**
     * Where a held body's middle hangs so its box stands clear of each
     * linked gate's face it would hang at: a middle at the face crosses it
     * as it is held there and goes round the pair every tick, as the
     * operator found a slime held at the portal's edge looping.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param pairs  the linked pairs
     * @param middle where the look would hang the body's middle
     * @param width  the body's width
     * @return the middle, set out from any face it stood too near
     */
    static Vec3 clearOfTheFaces(List<GatePassage.Pair> pairs, Vec3 middle, double width) {
        double clear = width * HALF + FACE_CLEARANCE;
        Vec3 held = middle;
        for (Gate gate : pairs.stream().flatMap(pair -> Stream.of(pair.one(), pair.other())).toList()) {
            double out = gate.out(held);
            if (out > -clear && out < clear && gate.within(held, width * HALF)) {
                held = held.add(gate.face().getUnitVec3().scale(clear - out));
            }
        }
        return held;
    }

    /** How far past half its width a held body's middle hangs before a linked gate's face. */
    private static final double FACE_CLEARANCE = 0.1;

    /**
     * Where a look ends carried through each linked gate it passes, a few at
     * most, so a look down the tunnel a facing pair makes ends rather than
     * going round it.
     *
     * @param pairs     the linked pairs
     * @param dimension the level the look runs in
     * @param from      the look's start
     * @param to        its straight end
     * @return its end through the gates
     */
    static Vec3 lookEndThroughGates(List<GatePassage.Pair> pairs, ResourceKey<Level> dimension, Vec3 from,
            Vec3 to) {
        Vec3 start = from;
        Vec3 end = to;
        for (int hop = 0; hop < MOST_HOPS; hop++) {
            Optional<GateRay.Crossing> crossing = GateRay.first(pairs, dimension, start, end);
            if (crossing.isEmpty()) {
                break;
            }
            start = crossing.get().farFrom();
            end = crossing.get().farTo();
        }
        return end;
    }

    /** How many gates a held body's look is carried through at most. */
    private static final int MOST_HOPS = 4;

    /**
     * How far Grab's look reaches for an entity to lift: its own range, and
     * Telekinesis's reach on top while Telekinesis holds.
     * decision grab-reaches-telekinesis-range
     *
     * @param telekinesisReach the modifier Telekinesis lays on the player's entity reach, or null where none stands
     * @return the reach, in blocks
     */
    double reachWith(@Nullable AttributeModifier telekinesisReach) {
        return telekinesisReach == null ? range : range + telekinesisReach.amount();
    }

    /**
     * Whether Grab takes an entity the glove sent: one it takes, standing
     * within its reach of the eye, measured eye to middle as the glove's aim
     * measures it, so the outline and the lift agree.
     * decision outlined-target-is-the-one-pick
     *
     * @param sent             the entity the tick named
     * @param distanceSquared  the squared distance from the eye to the entity's middle
     * @param telekinesisReach the modifier Telekinesis lays on the player's entity reach, or null where none stands
     * @return true where Grab takes it
     */
    boolean takesSent(Entity sent, double distanceSquared, @Nullable AttributeModifier telekinesisReach) {
        double reach = reachWith(telekinesisReach);
        return takesEntity(sent) && distanceSquared <= reach * reach;
    }

    /**
     * How far an ability's Grab reaches, so the glove aims no farther than it lifts.
     *
     * @param behaviors        the ability's behaviors
     * @param telekinesisReach the modifier Telekinesis lays on the player's entity reach, or null where none stands
     * @return the reach, in blocks, or empty where the ability holds no grab
     */
    public static OptionalDouble reachOf(List<Step> behaviors, @Nullable AttributeModifier telekinesisReach) {
        return behaviors.stream().filter(GrabStep.class::isInstance).map(GrabStep.class::cast)
                .mapToDouble(grab -> grab.reachWith(telekinesisReach)).findFirst();
    }

    /**
     * The entity the glove outlined and the tick sent, where Grab takes it
     * and it stands within reach; the server picks no entity of its own.
     * decision outlined-target-is-the-one-pick
     *
     * @param player  the channeling player
     * @param channel the channel's host, carrying the tick's aim
     * @return the sent entity, or null where none was sent or Grab refuses it
     */
    private @Nullable Entity sentTarget(ServerPlayer player, ChannelHost channel) {
        int sentId = channel.channelAim().map(ChannelAim::targetEntityId).orElse(ChannelAim.NO_ENTITY);
        Entity sent = sentId == ChannelAim.NO_ENTITY ? null : player.level().getEntity(sentId);
        if (sent == null || sent == player) {
            return null;
        }
        return takesSent(sent, reachedSquared(player, sent), Telekinesis.entityReachLaid(player)) ? sent : null;
    }

    @Override
    public boolean takesEntity(Entity entity) {
        return liftable(entity);
    }

    /**
     * Whether Grab can take an entity: a living mob that is no boss and
     * rides nothing, which it lifts, or an item entity, which it pulls.
     * decision steps-declare-the-entities-they-take
     *
     * @param candidate the entity
     * @return true where Grab takes it
     */
    static boolean liftable(Entity candidate) {
        if (candidate instanceof ItemEntity) {
            return candidate.isAlive();
        }
        return candidate instanceof Mob mob && mob.isAlive() && !EntityScan.isBoss(mob) && !mob.isPassenger();
    }

    /**
     * Whether a left click throws through Grab rather than punching: a glove
     * press stands and the selected ability holds a grab, or Terrakinesis's
     * hunk (decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity).
     *
     * @param pressArmed whether right click holds a live glove press
     * @param behaviors  the selected ability's behaviors
     * @return true where the click throws
     */
    public static boolean throwsOnAttack(boolean pressArmed, List<Step> behaviors) {
        return pressArmed && behaviors.stream()
                .anyMatch(step -> step instanceof GrabStep || step instanceof TerrakinesisStep);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.TARGET);
    }
}
