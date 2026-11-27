package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.LaserPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Glow's Laser: a ray of light held from the channeling player's eye
 * toward the cursor's aim point, out to its range. On every hit tick of the
 * hold it runs the child steps on the first living mob it strikes, the one
 * ray striking nothing off its line. Every client tracking the caster is
 * sent the ray each tick to draw it.
 * decision prism-leaves-the-mod
 *
 * @param range  how far the ray reaches, in blocks
 * @param every  the ticks between hits; the hold's first tick hits
 * @param where  the filters a struck mob must pass; empty keeps any living entity
 * @param steps  the child steps run on each mob a hit lands on
 * @param impact the steps run where the ray lands each tick of the hold, on the mob or block face it strikes
 */
public record RayStep(double range, int every, List<EntityFilter> where, List<Step> steps,
                      List<Step> impact) implements ReachesTheAim {

    private static final String NAME = "ray";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_EVERY = "every";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STEPS = "steps";
    private static final String FIELD_IMPACT = "impact";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<RayStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RANGE).forGetter(RayStep::range),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_EVERY).forGetter(RayStep::every),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(RayStep::where),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(RayStep::steps),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_IMPACT, List.of())
                    .forGetter(RayStep::impact)
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
        int held = aim.get().held();
        boolean hits = hitsOn(held);
        if (hits && hit instanceof EntityHitResult struck && struck.getEntity() instanceof LivingEntity living) {
            runOn(new EntityHost(level, living, caster));
        }
        impactAt(level, eye, hit, held, hits);
        EntityVisuals.sendToWatchers(caster, new LaserPayload(caster.getId(), hit.getLocation(), range));
        return true;
    }

    /**
     * Runs the impact steps where the ray lands, on the mob or block face it
     * strikes, each tick of the hold; a ray striking nothing within its range
     * lands nowhere.
     * decision sunbeam-lands-with-impact-and-aim
     *
     * @param level the server level
     * @param eye   the channeling player's eye
     * @param hit   what the ray struck
     * @param held  the hold's age in ticks
     * @param hits  whether this tick hits
     */
    private void impactAt(ServerLevel level, Vec3 eye, HitResult hit, int held, boolean hits) {
        if (impact.isEmpty() || hit.getType() == HitResult.Type.MISS) {
            return;
        }
        new ProgramBehavior(impact).tick(new ImpactHost(level, hit.getLocation(), faceStruck(eye, hit),
                held, hits));
    }

    /**
     * The face the ray strikes: a block's own face, or for a mob the side
     * turned toward the caster.
     *
     * @param eye the channeling player's eye
     * @param hit what the ray struck
     * @return the struck face
     */
    static Direction faceStruck(Vec3 eye, HitResult hit) {
        if (hit instanceof BlockHitResult block) {
            return block.getDirection();
        }
        Vec3 back = eye.subtract(hit.getLocation());
        return Direction.getApproximateNearest(back.x, back.y, back.z);
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
        return castFrom(level, caster, eye, reach, living -> passes(living, caster));
    }

    /**
     * What a ray from the eye toward its reach strikes first: a living mob
     * the filter keeps, else the block outline it meets. The client casts
     * the same ray each frame to draw its own (decision diagnose-and-correct-the-sunbeam-jitter),
     * and probes where a beam strikes from points far from the caster, so the
     * mobs searched lie around the ray's own line
     * (decision sunbeam-motes-scatter-away-from-struck-surfaces).
     *
     * @param level   the level the ray runs in
     * @param caster  the channeling entity, never struck
     * @param eye     where the ray starts
     * @param reach   the furthest point it reaches
     * @param strikes which living mobs the ray stops on
     * @return the mob or block struck, or a miss at the reach
     */
    public static HitResult castFrom(Level level, Entity caster, Vec3 eye, Vec3 reach,
                                     Predicate<LivingEntity> strikes) {
        BlockHitResult block = clipStriking(level, eye, reach);
        Vec3 stop = block.getLocation();
        AABB swept = new AABB(eye, stop).inflate(1);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(caster, eye, stop, swept,
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && strikes.test(living),
                eye.distanceToSqr(stop));
        return entity != null ? entity : block;
    }

    /**
     * Where a ray from a point toward its reach first meets a block's
     * collision, so the ray passes through what has none, such as a sign or
     * tall grass, or a miss at the reach.
     *
     * @param level the blocks the ray runs through
     * @param from  where the ray starts
     * @param to    the furthest point it reaches
     * @return the block struck, or a miss
     */
    static BlockHitResult clipStriking(BlockGetter level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level,
                (blocks, pos) -> blocks.getBlockState(pos).getCollisionShape(blocks, pos).clip(from, to, pos.immutable()),
                blocks -> {
                    Vec3 back = from.subtract(to);
                    return BlockHitResult.miss(to, Direction.getApproximateNearest(back.x, back.y, back.z),
                            BlockPos.containing(to));
                });
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
        return Stream.concat(steps.stream(), impact.stream());
    }

    @Override
    public boolean takesEntity(Entity entity) {
        return Step.takenThrough(where, steps, entity);
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return Stream.concat(steps.stream().map(child -> new HostedStep(child, HostKind.ENTITY)),
                impact.stream().map(child -> new HostedStep(child, HostKind.IMPACT)));
    }
}
