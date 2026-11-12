package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Telekinesis;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.GateRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
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
 * after holds it in front of the player along the look, against the first
 * surface the look meets within the hold distance, else at the hold distance.
 * A left click while held
 * throws it at the throw speed (GrabEvents.throwHeld), and the press lifts
 * nothing more until right click lets go. An outlined item entity is
 * pulled to the player at the pull speed each tick instead, unheld, and
 * picked up on arrival.
 * decision grab-holds-and-throws-a-physics-body
 * decision grab-pulls-an-item-to-you-and-picks-it-up
 * decision outlined-target-is-the-one-pick
 * decision grab-holds-the-mob-against-the-surface-you-look-at
 * decision grab-holds-slightly-closer-in-open-space
 * decision grab-cone-picks-more-aggressively-than-other-goo-types
 *
 * @param range        how far the look reaches for an entity to lift, in blocks, which Telekinesis doubles while it holds
 * @param holdDistance how far from the eye the held entity hangs where the look meets no block
 * @param throwSpeed   the speed a throw launches the held entity at, in blocks per tick
 * @param pullSpeed    the speed a pulled item entity flies to the player at, in blocks per tick
 * @param pickCone     the half-angle, in degrees, of the cone the glove picks Grab's target in, wider than the
 *                     shared one so a look passing close to a mob still picks it
 */
public record GrabStep(double range, double holdDistance, double throwSpeed, double pullSpeed, double pickCone)
        implements ReachesTheAim {

    private static final String NAME = "grab";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_HOLD_DISTANCE = "hold_distance";
    private static final String FIELD_THROW_SPEED = "throw_speed";
    private static final String FIELD_PULL_SPEED = "pull_speed";
    private static final String FIELD_PICK_CONE = "pick_cone";
    private static final double HALF = 0.5;

    /** Codec for the step's params. */
    public static final MapCodec<GrabStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RANGE).forGetter(GrabStep::range),
            Codec.DOUBLE.fieldOf(FIELD_HOLD_DISTANCE).forGetter(GrabStep::holdDistance),
            Codec.DOUBLE.fieldOf(FIELD_THROW_SPEED).forGetter(GrabStep::throwSpeed),
            Codec.DOUBLE.fieldOf(FIELD_PULL_SPEED).forGetter(GrabStep::pullSpeed),
            Codec.DOUBLE.fieldOf(FIELD_PICK_CONE).forGetter(GrabStep::pickCone)
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
     * else the shorter of the straight line and the shortest way through a
     * linked pair, so a pair standing far off never lengthens the reach to a
     * body the straight line already reaches.
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
                : shorterWay(straight, GateRay.shortestRoute(GatePassage.pairsOf(player.level().getServer()), eye,
                        middle).map(GateRay.Route::length));
        return length * length;
    }

    /**
     * The shorter of a straight line and a way through a linked pair.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param straight the straight line's length
     * @param throughPair the way through a pair's length, empty where no pair offers one
     * @return the shorter length
     */
    static double shorterWay(double straight, Optional<Double> throughPair) {
        return throughPair.filter(length -> length < straight).orElse(straight);
    }

    /**
     * Where a held entity's feet go this tick: its middle resting against
     * the surface the look meets (restingMiddle), the look carried through
     * each linked gate it passes, so a body held by a look aimed into a
     * portal hangs where the look ends past the partner, as seen through the
     * portal, rather than dragged at the wall behind it and round the pair
     * every tick.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param player the holding player
     * @param held   the held entity
     * @return the feet position
     */
    private Vec3 holdPoint(ServerPlayer player, Entity held) {
        MinecraftServer server = player.level().getServer();
        List<GatePassage.Pair> pairs = GatePassage.pairsOf(server);
        LookClip clip = (dimension, from, to) -> {
            Level level = server.getLevel(dimension);
            return level == null ? BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to))
                    : level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            CollisionContext.empty()));
        };
        Vec3 middle = restingMiddle(pairs, player.level().dimension(), player.getEyePosition(),
                player.getLookAngle(), clip, held.getBbWidth(), held.getBbHeight());
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

    /**
     * Clips one leg of a held body's look against a level's blocks.
     */
    @FunctionalInterface
    interface LookClip {
        /**
         * Clips the leg.
         *
         * @param dimension the level the leg runs in
         * @param from      the leg's start
         * @param to        the leg's end
         * @return the clip result
         */
        BlockHitResult clip(ResourceKey<Level> dimension, Vec3 from, Vec3 to);
    }

    /**
     * Where a held body's middle hangs for a look: the look runs the
     * hold distance from the eye, carried through each linked gate it
     * passes, a few at most, so a look down the tunnel a facing pair makes
     * ends rather than going round it; where a leg meets a block first, the
     * middle backs off along that leg until the body's box rests against the
     * block's face, so looking down brings the body in only because the look
     * meets the ground, and a look at the wall under a ledge pulls a body
     * off the ledge to that wall.
     * decision grab-holds-the-mob-against-the-surface-you-look-at
     * aim-and-shots-carry-through-linked-gates
     *
     * @param pairs     the linked pairs
     * @param dimension the level the look starts in
     * @param eye       the holder's eye
     * @param look      the holder's unit look
     * @param clip      the block clip each leg runs through
     * @param width     the held body's width
     * @param height    the held body's height
     * @return the middle
     */
    Vec3 restingMiddle(List<GatePassage.Pair> pairs, ResourceKey<Level> dimension, Vec3 eye, Vec3 look,
            LookClip clip, double width, double height) {
        ResourceKey<Level> legDimension = dimension;
        Vec3 start = eye;
        // The look Minecraft hands over is built from its sine table and falls a little short of unit length.
        Vec3 end = eye.add(look.normalize().scale(holdDistance));
        for (int hop = 0; hop <= MOST_HOPS; hop++) {
            Optional<GateRay.Crossing> crossing = hop < MOST_HOPS
                    ? GateRay.first(pairs, legDimension, start, end) : Optional.empty();
            // The near leg's clip ends just before the gate's face, clear of the wall the gate stands on.
            Vec3 legEnd = crossing.map(through -> through.facePoint()
                    .add(through.near().face().getUnitVec3().scale(BEFORE_THE_FACE))).orElse(end);
            BlockHitResult hit = clip.clip(legDimension, start, legEnd);
            if (hit.getType() == HitResult.Type.BLOCK) {
                return restingAgainst(hit, start, end.subtract(start).normalize(), width, height);
            }
            if (crossing.isEmpty()) {
                break;
            }
            legDimension = crossing.get().far().dimension();
            start = crossing.get().farFrom();
            end = crossing.get().farTo();
        }
        return end;
    }

    /**
     * Where a body's middle hangs on a leg so its box rests against the face
     * the leg met: backed off along the leg until the box's half extent
     * across that face clears it, and never back past the leg's start.
     *
     * @param hit    the face the leg met
     * @param start  the leg's start
     * @param along  the leg's unit direction
     * @param width  the body's width
     * @param height the body's height
     * @return the middle
     */
    private static Vec3 restingAgainst(BlockHitResult hit, Vec3 start, Vec3 along, double width, double height) {
        Direction face = hit.getDirection();
        double halfAcross = (face.getAxis() == Direction.Axis.Y ? height : width) * HALF;
        double into = -along.dot(face.getUnitVec3());
        double reached = start.distanceTo(hit.getLocation());
        double back = into <= 0 ? reached : Math.min(halfAcross / into, reached);
        return hit.getLocation().subtract(along.scale(back));
    }

    /** How far past half its width a held body's middle hangs before a linked gate's face. */
    private static final double FACE_CLEARANCE = 0.1;
    /** How far before a gate's face the near leg's clip ends, clear of the gate's own blocks. */
    private static final double BEFORE_THE_FACE = 0.01;

    /** How many gates a held body's look is carried through at most. */
    private static final int MOST_HOPS = 4;

    /** How many times its own range Grab reaches while Telekinesis holds. */
    private static final double TELEKINESIS_REACH_FACTOR = 2;

    /**
     * How far Grab's look reaches for an entity to lift: its own range, and
     * twice that while Telekinesis holds, whatever reach Telekinesis lays.
     * decision grab-reaches-telekinesis-range
     *
     * @param telekinesisReach the modifier Telekinesis lays on the player's entity reach, or null where none stands
     * @return the reach, in blocks
     */
    double reachWith(@Nullable AttributeModifier telekinesisReach) {
        return telekinesisReach == null ? range : range * TELEKINESIS_REACH_FACTOR;
    }

    /**
     * Whether Grab takes an entity the glove sent: one it takes, standing
     * within its reach of the eye, measured eye to middle as the glove's aim
     * measures it, so the outline and the lift agree.
     * decision outlined-target-is-the-one-pick
     *
     * @param sent             the entity the tick named
     * @param distanceSquared  the squared distance from the eye to the entity's middle
     * @param telekinesisReach the modifier Telekinesis lays on the player's entity reach, which doubles the range
     *                         where it stands, or null where none stands
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
     * @param telekinesisReach the modifier Telekinesis lays on the player's entity reach, which doubles the range
     *                         where it stands, or null where none stands
     * @return the reach, in blocks, or empty where the ability holds no grab
     */
    public static OptionalDouble reachOf(List<Step> behaviors, @Nullable AttributeModifier telekinesisReach) {
        return behaviors.stream().filter(GrabStep.class::isInstance).map(GrabStep.class::cast)
                .mapToDouble(grab -> grab.reachWith(telekinesisReach)).findFirst();
    }

    /**
     * The cone an ability's Grab picks its target in, so the glove outlines
     * a mob the look passes close to.
     * decision grab-cone-picks-more-aggressively-than-other-goo-types
     *
     * @param behaviors the ability's behaviors
     * @return the cone's half-angle in degrees, or empty where the ability holds no grab
     */
    public static OptionalDouble pickConeOf(List<Step> behaviors) {
        return behaviors.stream().filter(GrabStep.class::isInstance).map(GrabStep.class::cast)
                .mapToDouble(GrabStep::pickCone).findFirst();
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
