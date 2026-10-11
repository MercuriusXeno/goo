package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Cools the lava within a sphere around the host's center into magma blocks,
 * still harmful underfoot but far less than the lava it replaced.
 * weird-bounces-and-softens-harm
 *
 * @param radius the sphere's radius in blocks, evaluated on the host
 */
public record CoolLavaStep(Expr radius) implements Step {

    private static final String NAME = "cool_lava";
    private static final String FIELD_RADIUS = "radius";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CoolLavaStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(CoolLavaStep::radius)
    ).apply(inst, CoolLavaStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CoolLavaStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CoolLavaStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        FrostHost host = context.hostAs(FrostHost.class);
        coolWithin(host.level(), host.frostCenter(), radius.evaluate(context));
        return true;
    }

    /**
     * Turns every lava block, source or flowing, whose center stands within a
     * sphere into a magma block.
     *
     * @param level  the level
     * @param center the sphere's center
     * @param radius the sphere's radius in blocks
     */
    public static void coolWithin(ServerLevel level, Vec3 center, double radius) {
        BlockPos from = BlockPos.containing(center.subtract(radius, radius, radius));
        BlockPos to = BlockPos.containing(center.add(radius, radius, radius));
        double reachSquared = radius * radius;
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (Vec3.atCenterOf(pos).distanceToSqr(center) <= reachSquared && level.getBlockState(pos).is(Blocks.LAVA)) {
                level.setBlock(pos, magma, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.FROST);
    }
}
