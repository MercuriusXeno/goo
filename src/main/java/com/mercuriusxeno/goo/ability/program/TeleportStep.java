package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.oculus.OculusNodes;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Moves the host's target and finishes. Ender teleport is
 * {@code teleport mode=random_offset range=32}: a random horizontal jump
 * of up to sixteen blocks either way.
 *
 * @param mode      how the destination is picked
 * @param range     the mode's range in blocks, evaluated when the step runs
 * @param nodeRange how far off a look blink may snap to an oculus, zero for none
 * @param nodeCone  how far off the look, in degrees, an oculus it snaps to may stand
 */
public record TeleportStep(TeleportMode mode, Expr range, Expr nodeRange, Expr nodeCone) implements Step {

    private static final String NAME = "teleport";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_NODE_RANGE = "node_range";
    private static final String FIELD_NODE_CONE = "node_cone";
    private static final Expr NO_NODES = Expr.literal(0);
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<TeleportStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TeleportMode.CODEC.fieldOf(FIELD_MODE).forGetter(TeleportStep::mode),
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(TeleportStep::range),
            Expr.CODEC.optionalFieldOf(FIELD_NODE_RANGE, NO_NODES).forGetter(TeleportStep::nodeRange),
            Expr.CODEC.optionalFieldOf(FIELD_NODE_CONE, NO_NODES).forGetter(TeleportStep::nodeCone)
    ).apply(inst, TeleportStep::new));

    /**
     * A teleport that snaps to no oculus.
     *
     * @param mode  how the destination is picked
     * @param range the mode's range in blocks, evaluated when the step runs
     */
    public TeleportStep(TeleportMode mode, Expr range) {
        this(mode, range, NO_NODES, NO_NODES);
    }

    /**
     * The registered type.
     */
    public static final StepType<TeleportStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TeleportStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        double reach = range.evaluate(context);
        Vec3 standing = new Vec3(target.getX(), target.getY(), target.getZ());
        Vec3 destination = switch (mode) {
            case RANDOM_OFFSET -> standing.add(randomOffset(target, reach));
            case TOWARD_THROWER ->
                    standing.add(towardThrower(target, context.hostAs(TargetHost.class).thrower(), reach));
            case AWAY_FROM_THROWER ->
                    standing.add(towardThrower(target, context.hostAs(TargetHost.class).thrower(), -reach));
            case THROWER_LOOK -> blinkAlongLook(context.hostAs(TargetHost.class), reach)
                    .map(BlinkLanding::feet).orElse(standing);
        };
        // A step after the jump, a ghost trail, reads where the target left from: a player's old
        // position is overwritten by the teleport itself, so the source is kept on the entity.
        // Decision ghost-trail-spans-the-blink.
        target.setData(GooAttachments.JUMP_SOURCE, target.position());
        target.teleportTo(destination.x(), destination.y(), destination.z());
        return true;
    }

    /**
     * Rolls a level jump of up to half the range either way on each axis.
     *
     * @param target the entity jumping
     * @param range  the full width of the roll
     * @return the jump
     */
    private static Vec3 randomOffset(LivingEntity target, double range) {
        RandomSource random = target.level().getRandom();
        return new Vec3((random.nextDouble() - HALF) * range, 0, (random.nextDouble() - HALF) * range);
    }

    /**
     * Measures a level jump of the range along the line from the target
     * to the thrower; a negative range jumps away.
     *
     * @param target  the entity jumping
     * @param thrower the entity the line runs to, or null when unknown
     * @param range   the jump length, negative to jump away
     * @return the jump, zero with no thrower or a thrower at the target
     */
    private static Vec3 towardThrower(LivingEntity target, @Nullable Entity thrower, double range) {
        if (thrower == null) {
            return Vec3.ZERO;
        }
        Vec3 line = new Vec3(thrower.getX() - target.getX(), 0, thrower.getZ() - target.getZ());
        return line.lengthSqr() == 0 ? Vec3.ZERO : line.normalize().scale(range);
    }

    /**
     * Where a blink along the thrower's look puts the target, sliding on the
     * plane the press pinned where the host carries one.
     * decision blink-lands-safely-costed-by-distance
     *
     * @param host  the host whose target blinks
     * @param range the blink's range
     * @return the landing, empty with no thrower or no spot the target fits
     */
    private Optional<BlinkLanding> blinkAlongLook(TargetHost host, double range) {
        Entity thrower = host.thrower();
        return thrower == null ? Optional.empty()
                : landing(host.target(), host.target().position(), thrower.getLookAngle(), range, host.blinkPin());
    }

    /**
     * Where this step's blink puts an entity: beside the oculus the look
     * snaps to where a free-aim blink finds one, else where the blink lands.
     * Decision oculus-prism-becomes-a-hovering-eye.
     *
     * @param blinker the entity blinking
     * @param feet    where the entity's feet stand
     * @param look    the unit look vector
     * @param reach   the blink's range in blocks
     * @param pin     the face plane the press pinned, empty for free aim
     * @return the landing, empty when no spot the entity fits lies on the way
     */
    public Optional<BlinkLanding> landing(Entity blinker, Vec3 feet, Vec3 look, double reach,
            Optional<ChannelAim.FacePlane> pin) {
        double snapRange = nodeRange.evaluate(Variables.NONE);
        Optional<BlinkLanding> snapped = pin.isPresent() || snapRange <= 0 ? Optional.empty()
                : OculusNodes.onLook(blinker.level(), feet.add(0, blinker.getEyeHeight(), 0), look, snapRange,
                        nodeCone.evaluate(Variables.NONE))
                        .flatMap(node -> BlinkResolver.toNode(new LevelBlinkSpace(blinker.level(), blinker), feet,
                                node, BlinkBody.of(blinker)));
        return snapped.isPresent() ? snapped : landingAlongLook(blinker, feet, look, reach, pin);
    }

    /**
     * Where a blink puts an entity. The server's teleport, its price and the
     * client's blink cursor all resolve the landing here, so the cursor
     * stands where the jump lands and the cost shown is the cost drained.
     * Decision ripple-outline-is-the-blink-cursor.
     * Decision blink-lands-safely-costed-by-distance.
     *
     * @param blinker the entity blinking, whose level and size the landing reads
     * @param feet    where the entity's feet stand, interpolated for a frame on the client
     * @param look    the unit look vector
     * @param range   the blink's range in blocks
     * @param pin     the face plane the press pinned, empty for free aim
     * @return the landing, empty when no spot the entity fits lies on the way
     */
    public static Optional<BlinkLanding> landingAlongLook(Entity blinker, Vec3 feet, Vec3 look, double range,
            Optional<ChannelAim.FacePlane> pin) {
        return BlinkResolver.resolve(new LevelBlinkSpace(blinker.level(), blinker), feet, look, range,
                BlinkBody.of(blinker), pin);
    }

    /**
     * Where an ability's blink would put an entity now, which its price
     * reads before the blink runs.
     * decision blink-lands-safely-costed-by-distance
     *
     * @param behaviors the ability's top-level steps
     * @param blinker   the entity that would blink
     * @param feet      where the entity's feet stand
     * @param look      the unit look vector
     * @param pin       the face plane the press pinned, empty for free aim
     * @return the landing, empty where the ability blinks along no look or lands nowhere
     */
    public static Optional<BlinkLanding> tripOf(List<Step> behaviors, Entity blinker, Vec3 feet, Vec3 look,
            Optional<ChannelAim.FacePlane> pin) {
        return lookStep(behaviors).flatMap(step ->
                step.landing(blinker, feet, look, step.range().evaluate(Variables.NONE), pin));
    }

    /**
     * A blink with nowhere to land neither runs nor drains; every other
     * teleport always can.
     * decision blink-lands-safely-costed-by-distance
     */
    @Override
    public boolean admits(StepContext context) {
        return mode != TeleportMode.THROWER_LOOK
                || blinkAlongLook(context.hostAs(TargetHost.class), range.evaluate(context)).isPresent();
    }

    /**
     * The range of the first look-following teleport among the steps, for a
     * client that previews where it lands. A range naming a variable reads
     * only when the step runs, so it answers nothing here.
     * Decision ripple-outline-is-the-blink-cursor.
     *
     * @param behaviors an ability's top-level steps
     * @return the range, empty where no look teleport with a literal range stands
     */
    public static OptionalDouble lookRange(List<Step> behaviors) {
        return lookStep(behaviors).map(step -> OptionalDouble.of(step.range().evaluate(Variables.NONE)))
                .orElse(OptionalDouble.empty());
    }

    /**
     * The first look-following teleport with a literal range among the steps.
     *
     * @param behaviors an ability's top-level steps
     * @return the step, empty where none stands
     */
    private static Optional<TeleportStep> lookStep(List<Step> behaviors) {
        return behaviors.stream()
                .filter(TeleportStep.class::isInstance).map(TeleportStep.class::cast)
                .filter(teleport -> teleport.mode() == TeleportMode.THROWER_LOOK
                        && teleport.range().variables().isEmpty())
                .findFirst();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(range, nodeRange, nodeCone);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
