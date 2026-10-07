package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock crush's step, run as the blob lands: the blocks in the direction it
 * struck, into the face it landed on, break with their drops to the JSON's
 * depth while they belong to the mundane set the JSON names, the first other
 * solid block stopping the crush; and every living thing within the JSON's
 * radius of the landing is shoved the same way.
 * decision crush-blob-breaks-along-its-strike
 *
 * @param breaks the block tag naming the blocks crush may break
 * @param depth  how many blocks deep the crush reaches
 * @param radius the reach of the shove around the landing, in blocks
 * @param push   the shove's speed along the strike, in blocks per tick
 */
public record CrushStep(TagKey<Block> breaks, int depth, double radius, double push) implements Step {

    private static final String NAME = "crush";
    private static final String FIELD_BREAKS = "breaks";
    private static final String FIELD_DEPTH = "depth";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_PUSH = "push";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CrushStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BREAKS).forGetter(CrushStep::breaks),
            Codec.INT.fieldOf(FIELD_DEPTH).forGetter(CrushStep::depth),
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(CrushStep::radius),
            Codec.DOUBLE.fieldOf(FIELD_PUSH).forGetter(CrushStep::push)
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
        PlacedFaceHost landing = context.hostAs(PlacedFaceHost.class);
        Direction strike = landing.placedFace().getOpposite();
        crushAlong(context.hostAs(BlockBreakHost.class), landing.position().relative(strike), strike);
        Vec3 shove = Vec3.atLowerCornerOf(strike.getUnitVec3i()).scale(push);
        context.hostAs(EntityScanHost.class).forEachEntityWithin(SelectionShape.SPHERE, radius,
                Set.of(EntityFilter.LIVING), target -> target.push(shove));
        return true;
    }

    /**
     * Breaks the blocks from the struck one inward to the depth, stopping at
     * the first solid block outside the tag.
     *
     * @param host   the block-breaking host
     * @param struck the block the blob struck
     * @param strike the direction the blob struck in
     */
    private void crushAlong(BlockBreakHost host, BlockPos struck, Direction strike) {
        for (int deep = 0; deep < depth; deep++) {
            BlockPos pos = struck.relative(strike, deep);
            if (host.blockIn(pos, breaks)) {
                host.breakBlock(pos);
            } else if (!host.airAt(pos)) {
                return;
            }
        }
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.PLACED_FACE, HostCapability.BREAK_BLOCKS, HostCapability.ENTITY_SCAN);
    }
}
