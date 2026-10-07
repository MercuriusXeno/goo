package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Rock crush's step, run as the blob lands: every block of the mundane set
 * the JSON names whose center stands within the crater's radius of the
 * landing point breaks with its drops, throwing its debris up and out, and
 * the one mob nearest the landing point within a block of it takes the
 * JSON's force damage, no other
 * (decision crush-blob-breaks-along-its-strike).
 *
 * @param breaks the block tag naming the blocks crush may break
 * @param radius the crater's radius in blocks
 * @param damage the force damage the struck mob takes, evaluated when the step runs
 */
public record CrushStep(TagKey<Block> breaks, double radius, Expr damage) implements Step {

    private static final String NAME = "crush";
    private static final String FIELD_BREAKS = "breaks";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DAMAGE = "damage";
    /** How near the landing point a mob stands to be the one the blob strikes, in blocks. */
    static final double STRIKE_REACH = 1.0;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CrushStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BREAKS).forGetter(CrushStep::breaks),
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(CrushStep::radius),
            Expr.CODEC.fieldOf(FIELD_DAMAGE).forGetter(CrushStep::damage)
    ).apply(inst, CrushStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CrushStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CrushStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        AnchoredWorldHost landing = context.hostAs(AnchoredWorldHost.class);
        BlockBreakHost blocks = context.hostAs(BlockBreakHost.class);
        Vec3 point = landing.anchor();
        for (BlockPos pos : craterCells(point, radius)) {
            if (blocks.blockIn(pos, breaks)) {
                blocks.throwDebris(pos);
                blocks.breakBlock(pos);
            }
        }
        List<TargetHost> near = new ArrayList<>();
        landing.forEachEntityWithin(SelectionShape.SPHERE, STRIKE_REACH, Set.of(EntityFilter.LIVING), near::add);
        float amount = (float) damage.evaluate(context);
        nearest(near, target -> target.target().position().distanceToSqr(point))
                .ifPresent(struck -> strike(struck.target(), amount));
        return true;
    }

    private static void strike(LivingEntity mob, float amount) {
        if (mob.level() instanceof ServerLevel level) {
            mob.hurtServer(level, mob.damageSources().generic(), amount);
        }
    }

    /**
     * The blocks whose centers stand within a radius of a point.
     *
     * @param point  the crater's center
     * @param radius the crater's radius
     * @return the crater's blocks
     */
    static List<BlockPos> craterCells(Vec3 point, double radius) {
        List<BlockPos> cells = new ArrayList<>();
        BlockPos from = BlockPos.containing(point.subtract(radius, radius, radius));
        BlockPos to = BlockPos.containing(point.add(radius, radius, radius));
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (Vec3.atCenterOf(pos).distanceToSqr(point) <= radius * radius) {
                cells.add(pos.immutable());
            }
        }
        return cells;
    }

    /**
     * The one candidate nearest a point, the one the blob strikes.
     *
     * @param candidates the candidates
     * @param distance   each candidate's distance from the landing point
     * @param <T>        the candidate type
     * @return the nearest, empty with no candidate
     */
    static <T> Optional<T> nearest(List<T> candidates, ToDoubleFunction<T> distance) {
        return candidates.stream().min(Comparator.comparingDouble(distance));
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(damage);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.BREAK_BLOCKS, HostCapability.ENTITY_SCAN);
    }
}
