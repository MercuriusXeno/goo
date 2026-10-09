package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.SunbeamPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Glow's Sunbeam: a ray of light held from the channeling player's eye
 * toward the cursor's aim point, out to its range. On every hit tick of the
 * hold it runs the child steps on the first living mob it strikes; striking
 * a prism instead, the prism refracts it into a beam toward every mob in
 * clear line within the refraction radius, each running the children with
 * the split share of the hit. Every client tracking the caster is sent the
 * ray each tick to draw it.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 *
 * @param range   how far the ray reaches, in blocks
 * @param every   the ticks between hits; the hold's first tick hits
 * @param where   the filters a struck mob must pass; empty keeps any living entity
 * @param refract how a prism the ray strikes splits it
 * @param steps   the child steps run on each mob a hit lands on
 */
public record RayStep(double range, int every, List<EntityFilter> where, Refraction refract, List<Step> steps)
        implements Step {

    private static final String NAME = "ray";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_EVERY = "every";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_REFRACT = "refract";
    private static final String FIELD_STEPS = "steps";
    /** How far out of the prism a refracted beam's sight line starts, so the prism's own shape never blocks it. */
    private static final double OUT_OF_THE_PRISM = 0.6;
    private static final double HALF = 0.5;
    private static final double DIAMETER_PER_RADIUS = 2;

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<RayStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RANGE).forGetter(RayStep::range),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_EVERY).forGetter(RayStep::every),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(RayStep::where),
            Refraction.CODEC.fieldOf(FIELD_REFRACT).forGetter(RayStep::refract),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(RayStep::steps)
    ).apply(inst, RayStep::new));

    /**
     * The registered type.
     */
    public static final StepType<RayStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RayStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost channel = context.hostAs(ChannelHost.class);
        Optional<ChannelAim> aim = channel.channelAim();
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        if (aim.isEmpty() || !(caster.level() instanceof ServerLevel level)) {
            return true;
        }
        Vec3 eye = channel.eye();
        Vec3 reach = eye.add(aim.get().aimPoint().subtract(eye).normalize().scale(range));
        HitResult hit = cast(level, caster, eye, reach);
        List<Vec3> refracted = land(level, caster, hit, hitsOn(aim.get().heldTicks()));
        EntityVisuals.sendToWatchers(caster, new SunbeamPayload(caster.getId(), hit.getLocation(), refracted));
        return true;
    }

    /**
     * Lands what the ray struck: the children on a struck mob on a hit tick,
     * or a prism's refraction.
     *
     * @param level  the server level
     * @param caster the channeling player
     * @param hit    what the ray struck
     * @param hits   whether this tick lands a hit
     * @return the points each refracted beam ends at, empty where no prism refracted it
     */
    private List<Vec3> land(ServerLevel level, LivingEntity caster, HitResult hit, boolean hits) {
        if (hit instanceof EntityHitResult struck && struck.getEntity() instanceof LivingEntity living) {
            if (hits) {
                runOn(new EntityHost(level, living, caster));
            }
            return List.of();
        }
        if (hit instanceof BlockHitResult block
                && level.getBlockEntity(block.getBlockPos()) instanceof PrismBlockEntity) {
            return refractFrom(level, caster, Vec3.atCenterOf(block.getBlockPos()), hits);
        }
        return List.of();
    }

    /**
     * Whether a tick of the hold lands a hit: the first tick, then one every {@link #every} ticks.
     *
     * @param heldTicks the hold's age, 1 on its first tick
     * @return true on a hit tick
     */
    boolean hitsOn(int heldTicks) {
        return (heldTicks - 1) % every == 0;
    }

    private HitResult cast(ServerLevel level, LivingEntity caster, Vec3 eye, Vec3 reach) {
        // A prism stands no collision, so the ray stops on outlines to strike it.
        BlockHitResult block = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, caster));
        Vec3 stop = block.getLocation();
        AABB swept = caster.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(caster, eye, stop, swept,
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && passes(living, caster),
                eye.distanceToSqr(stop));
        return entity != null ? entity : block;
    }

    /**
     * Refracts the ray at a prism toward every mob in clear line within the
     * refraction radius, landing the split hit on each on a hit tick.
     *
     * @param level  the server level
     * @param caster the channeling player
     * @param prism  the struck prism's center
     * @param hits   whether this tick lands a hit
     * @return the points each refracted beam ends at
     */
    private List<Vec3> refractFrom(ServerLevel level, LivingEntity caster, Vec3 prism, boolean hits) {
        List<LivingEntity> targets = refractTargets(level, caster, prism);
        double share = refract.share(targets.size());
        List<Vec3> ends = new ArrayList<>(targets.size());
        for (LivingEntity target : targets) {
            if (hits) {
                runOn(new EntityHost(level, target, caster, share));
            }
            ends.add(bodyOf(target));
        }
        return ends;
    }

    /**
     * The mobs a prism refracts toward: alive, passing the filters, within
     * the radius and in clear line of the prism.
     *
     * @param level  the server level
     * @param caster the channeling player, never a target
     * @param prism  the prism's center
     * @return the targets
     */
    private List<LivingEntity> refractTargets(ServerLevel level, LivingEntity caster, Vec3 prism) {
        double across = refract.radius() * DIAMETER_PER_RADIUS;
        return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(prism, across, across, across),
                candidate -> candidate != caster && candidate.isAlive() && passes(candidate, caster)
                        && seesThePrism(level, prism, candidate));
    }

    private boolean seesThePrism(ServerLevel level, Vec3 prism, LivingEntity candidate) {
        Vec3 body = bodyOf(candidate);
        return body.distanceTo(prism) <= refract.radius() && inClearLine(level, prism, body, candidate);
    }

    private static Vec3 bodyOf(LivingEntity living) {
        return living.position().add(0, living.getBbHeight() * HALF, 0);
    }

    private static boolean inClearLine(ServerLevel level, Vec3 prism, Vec3 body, Entity target) {
        Vec3 start = prism.add(body.subtract(prism).normalize().scale(OUT_OF_THE_PRISM));
        BlockHitResult blocked = level.clip(new ClipContext(start, body, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, target));
        return blocked.getType() == HitResult.Type.MISS;
    }

    private boolean passes(LivingEntity living, @Nullable Entity caster) {
        return EntityScan.passes(living, Set.copyOf(where), caster);
    }

    private void runOn(EntityHost struck) {
        new ProgramBehavior(steps).tick(struck);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.TARGET);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
    }

    /**
     * How a prism splits the ray: the mobs within its radius in clear line
     * share a hit raised by the factor, each of n taking the raised hit over
     * n to the exponent, so the split costs each target only a little.
     *
     * @param radius   how far the prism refracts, in blocks
     * @param factor   how much the refracted hit is raised over a direct one
     * @param exponent how steeply the split thins with the number of targets
     */
    public record Refraction(double radius, double factor, double exponent) {

        private static final String FIELD_RADIUS = "radius";
        private static final String FIELD_FACTOR = "factor";
        private static final String FIELD_EXPONENT = "exponent";

        /** Codec for the refraction params. */
        public static final Codec<Refraction> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(Refraction::radius),
                Codec.DOUBLE.fieldOf(FIELD_FACTOR).forGetter(Refraction::factor),
                Codec.DOUBLE.fieldOf(FIELD_EXPONENT).forGetter(Refraction::exponent)
        ).apply(inst, Refraction::new));

        /**
         * The share of a direct hit each refracted target takes.
         *
         * @param targets how many mobs the prism refracts to
         * @return the share, zero with no target
         */
        public double share(int targets) {
            return targets <= 0 ? 0 : factor / Math.pow(targets, exponent);
        }
    }
}
