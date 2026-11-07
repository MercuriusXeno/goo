package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Telekinesis;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
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
public record GrabStep(double range, double near, double far, double throwSpeed, double pullSpeed) implements Step {

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
        }
        if (held instanceof ItemEntity item) {
            GrabEvents.pull(player, item, pullSpeed);
        } else if (held != null) {
            GrabEvents.hold(player, held, holdPoint(player, held), throwSpeed);
        }
        return true;
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
     * hold distance.
     *
     * @param player the holding player
     * @param held   the held entity
     * @return the feet position
     */
    private Vec3 holdPoint(ServerPlayer player, Entity held) {
        double distance = holdDistance(player.getXRot(), near, far);
        Vec3 middle = player.getEyePosition().add(player.getLookAngle().scale(distance));
        return middle.subtract(0, held.getBbHeight() * HALF, 0);
    }

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
        double distanceSquared = player.getEyePosition().distanceToSqr(sent.getBoundingBox().getCenter());
        return takesSent(sent, distanceSquared, Telekinesis.entityReachLaid(player)) ? sent : null;
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
     * press stands and the selected ability holds a grab.
     *
     * @param pressArmed whether right click holds a live glove press
     * @param behaviors  the selected ability's behaviors
     * @return true where the click throws
     */
    public static boolean throwsOnAttack(boolean pressArmed, List<Step> behaviors) {
        return pressArmed && behaviors.stream().anyMatch(GrabStep.class::isInstance);
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
