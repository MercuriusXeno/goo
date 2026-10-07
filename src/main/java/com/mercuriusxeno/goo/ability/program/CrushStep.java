package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock crush's step, run where the blob strikes. Landing on the ground, every
 * block of the set the JSON names whose center stands within the crater's
 * radius of the landing point breaks with its drops, throwing its debris up
 * and out, statues crushed among them, and no mob is hurt. Striking a mob
 * directly, the blob shatters into stone rubble and dust, drawing no goo
 * splat (its ability is tagged no_splat), the mob takes the JSON's force
 * damage and no crater is blasted, so mob attack never mixes with block crush. The step serves whichever host
 * the blob strikes, so it names no capability and reads the host it runs on
 * (decision crush-blob-breaks-along-its-strike).
 *
 * @param breaks the block tag naming the blocks crush may break
 * @param radius the crater's radius in blocks
 * @param damage the force damage a struck mob takes, evaluated when the step runs
 */
public record CrushStep(TagKey<Block> breaks, double radius, Expr damage) implements Step {

    private static final String NAME = "crush";
    private static final String FIELD_BREAKS = "breaks";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DAMAGE = "damage";
    /** The stone chunks and gravel dust a blob shatters into against a mob, and how they fly. */
    private static final int RUBBLE_CHUNKS = 24;
    private static final int DUST_PUFFS = 16;
    private static final double RUBBLE_SPREAD = 0.3;
    private static final double RUBBLE_SPEED = 0.25;

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
        StepHost host = context.host();
        if (host instanceof TargetHost struck) {
            strike(struck.target(), (float) damage.evaluate(context));
        } else if (host instanceof AnchoredWorldHost landing && host instanceof BlockBreakHost blocks) {
            blast(blocks, landing.anchor());
        }
        return true;
    }

    /**
     * Blasts the crater about the landing point, each crushed block throwing its debris.
     *
     * @param blocks the landing's block-breaking host
     * @param point  the landing point
     */
    private void blast(BlockBreakHost blocks, Vec3 point) {
        for (BlockPos pos : craterCells(point, radius)) {
            if (blocks.blockIn(pos, breaks)) {
                blocks.throwDebris(pos);
                blocks.breakBlock(pos);
            }
        }
    }

    /**
     * Strikes a mob: the blob shatters into stone rubble and dust against it
     * and the mob takes the force damage.
     *
     * @param mob    the struck mob
     * @param amount the force damage
     */
    private static void strike(LivingEntity mob, float amount) {
        if (mob.level() instanceof ServerLevel level) {
            Vec3 hit = mob.getBoundingBox().getCenter();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
                    hit.x, hit.y, hit.z, RUBBLE_CHUNKS, RUBBLE_SPREAD, RUBBLE_SPREAD, RUBBLE_SPREAD, RUBBLE_SPEED);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
                    hit.x, hit.y, hit.z, DUST_PUFFS, RUBBLE_SPREAD, RUBBLE_SPREAD, RUBBLE_SPREAD, RUBBLE_SPEED);
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

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(damage);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }
}
