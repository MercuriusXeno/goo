package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.ReapSwellPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Verdant's step, the leaf combo a prism runs for as long as it stands: on
 * every pulse a small sphere of Growth's breeze puffs out of the prism, a
 * few random cells within the radius are tried and the first that can green
 * does, stepping one rung along the greening map or, for still water open to
 * the sky, gaining a lily pad, with a bone-meal sparkle; and one pulse in
 * the crop odds, one plant Growth grows within the radius takes a random
 * tick, ground bone meal spreads over aside. The step never finishes. Leaf Verdant is
 * {@code verdant map=goo:greening radius=5 interval=40 tries=8 crop_odds=4 swell_radius=1.5 swell_ticks=6}.
 * verdant-prism-greens-blocks-slowly
 *
 * @param map         the id of the block map the greening steps along
 * @param radius      the blocks the greening reaches from the prism
 * @param interval    the ticks between pulses
 * @param tries       the random cells a pulse tries before it gives up greening
 * @param cropOdds    one pulse in this many nudges a crop
 * @param swellRadius the radius the breeze puffs to, in blocks
 * @param swellTicks  the ticks the breeze takes to puff out
 */
public record VerdantStep(Identifier map, int radius, int interval, int tries, int cropOdds, float swellRadius,
                          int swellTicks) implements Step {

    private static final String NAME = "verdant";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_INTERVAL = "interval";
    private static final String FIELD_TRIES = "tries";
    private static final String FIELD_CROP_ODDS = "crop_odds";
    private static final String FIELD_SWELL_RADIUS = "swell_radius";
    private static final String FIELD_SWELL_TICKS = "swell_ticks";
    private static final int SPARKLE_COUNT = 6;
    private static final double SPARKLE_SPREAD = 0.4;
    private static final double SPARKLE_LIFT = 0.6;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VerdantStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(VerdantStep::map),
            Codec.INT.fieldOf(FIELD_RADIUS).forGetter(VerdantStep::radius),
            Codec.INT.fieldOf(FIELD_INTERVAL).forGetter(VerdantStep::interval),
            Codec.INT.fieldOf(FIELD_TRIES).forGetter(VerdantStep::tries),
            Codec.INT.fieldOf(FIELD_CROP_ODDS).forGetter(VerdantStep::cropOdds),
            Codec.FLOAT.fieldOf(FIELD_SWELL_RADIUS).forGetter(VerdantStep::swellRadius),
            Codec.INT.fieldOf(FIELD_SWELL_TICKS).forGetter(VerdantStep::swellTicks)
    ).apply(inst, VerdantStep::new));

    /**
     * The registered type.
     */
    public static final StepType<VerdantStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<VerdantStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        GreeningHost host = context.hostAs(GreeningHost.class);
        ServerLevel level = host.level();
        if (level.getGameTime() % interval != 0) {
            return false;
        }
        BlockPos prism = host.position();
        BlockVisuals.sendToWatchers(level, prism, new ReapSwellPayload(host.center(), swellRadius, swellTicks));
        RandomSource random = level.getRandom();
        Optional<BlockMap> greening = BlockMaps.get(map);
        for (int tried = 0; tried < tries; tried++) {
            if (greenCell(level, randomCellWithin(prism, random), greening)) {
                break;
            }
        }
        if (random.nextInt(cropOdds) == 0) {
            nudgeACrop(level, prism, random);
        }
        return false;
    }

    /**
     * A random cell within the radius of the prism, the prism's own cell aside.
     *
     * @param prism  the prism's cell
     * @param random the random source
     * @return the cell
     */
    private BlockPos randomCellWithin(BlockPos prism, RandomSource random) {
        BlockPos cell;
        do {
            cell = prism.offset(random.nextIntBetweenInclusive(-radius, radius),
                    random.nextIntBetweenInclusive(-radius, radius), random.nextIntBetweenInclusive(-radius, radius));
        } while (cell.equals(prism) || cell.distSqr(prism) > (double) radius * radius);
        return cell;
    }

    /**
     * Greens a cell where it can: a block the map names steps one rung,
     * keeping its shape's properties, and still water open to the sky gains
     * a lily pad.
     *
     * @param level    the server level
     * @param cell     the cell
     * @param greening the greening map, empty when no data names it
     * @return true when the cell greened
     */
    private static boolean greenCell(ServerLevel level, BlockPos cell, Optional<BlockMap> greening) {
        BlockState state = level.getBlockState(cell);
        Optional<Block> next = greening.flatMap(steps -> steps.next(state.getBlock()));
        if (next.isPresent()) {
            level.setBlock(cell, next.get().withPropertiesOf(state), Block.UPDATE_ALL);
            sparkle(level, cell);
            return true;
        }
        BlockPos above = cell.above();
        if (gainsALily(level.getFluidState(cell).is(Fluids.WATER), level.isEmptyBlock(above),
                level.canSeeSky(above))) {
            level.setBlock(above, Blocks.LILY_PAD.defaultBlockState(), Block.UPDATE_ALL);
            sparkle(level, above);
            return true;
        }
        return false;
    }

    /**
     * Whether a cell gains a lily pad: still water with open air above it, under the sky.
     *
     * @param stillWater whether the cell holds a water source
     * @param airAbove   whether the cell above stands empty
     * @param openSky    whether the cell above sees the sky
     * @return true where a lily grows
     */
    static boolean gainsALily(boolean stillWater, boolean airAbove, boolean openSky) {
        return stillWater && airAbove && openSky;
    }

    /**
     * Gives one random plant Growth grows within the radius a random tick.
     *
     * @param level  the server level
     * @param prism  the prism's cell
     * @param random the random source
     */
    private void nudgeACrop(ServerLevel level, BlockPos prism, RandomSource random) {
        List<BlockPos> plants = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(prism.offset(-radius, -radius, -radius),
                prism.offset(radius, radius, radius))) {
            if (cell.distSqr(prism) <= (double) radius * radius && crop(level.getBlockState(cell))) {
                plants.add(cell.immutable());
            }
        }
        if (!plants.isEmpty()) {
            TickPlantsStep.tickPlant(level, plants.get(random.nextInt(plants.size())), random);
        }
    }

    /**
     * Whether a block is a crop Verdant nudges: a plant Growth grows, but not
     * ground that bone meal spreads over, such as grass or nylium, which
     * would crowd the crops out of the pick.
     *
     * @param state the block
     * @return true for a crop
     */
    private static boolean crop(BlockState state) {
        return TickPlantsStep.grows(state) && !(state.getBlock() instanceof BonemealableBlock bonemealable
                && bonemealable.getType() == BonemealableBlock.Type.NEIGHBOR_SPREADER);
    }

    private static void sparkle(ServerLevel level, BlockPos cell) {
        Vec3 at = Vec3.atBottomCenterOf(cell).add(0, SPARKLE_LIFT, 0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, SPARKLE_COUNT, SPARKLE_SPREAD,
                SPARKLE_SPREAD, SPARKLE_SPREAD, 0);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.GREENING);
    }
}
