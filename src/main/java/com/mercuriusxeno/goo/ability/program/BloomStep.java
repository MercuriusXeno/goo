package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.bloom.BloomPlants;
import com.mercuriusxeno.goo.ability.bloom.BloomSurface;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spawns Bloom's plants by chance around where the blob struck, and
 * finishes: it tries random empty cells within the radius, and each cell
 * whose surface takes a plant gets one of its flora that survives there,
 * thrown up with the bone-meal growth sparkle, until the plants are spawned
 * or the tries run out. Leaf Bloom is {@code bloom radius=4 plants=8 tries=64}.
 * bloom-places-buds-by-biome-and-surface
 *
 * @param radius how far from the struck cell a plant may spawn, in blocks
 * @param plants the most plants one blob spawns
 * @param tries  the random cells tried for them
 */
public record BloomStep(Expr radius, Expr plants, Expr tries) implements Step {

    private static final String NAME = "bloom";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_PLANTS = "plants";
    private static final String FIELD_TRIES = "tries";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BloomStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(BloomStep::radius),
            Expr.CODEC.fieldOf(FIELD_PLANTS).forGetter(BloomStep::plants),
            Expr.CODEC.fieldOf(FIELD_TRIES).forGetter(BloomStep::tries)
    ).apply(inst, BloomStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BloomStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BloomStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        BlockBreakHost host = context.hostAs(BlockBreakHost.class);
        ServerLevel level = host.level();
        RandomSource random = level.getRandom();
        int reach = (int) Math.ceil(radius.evaluate(context));
        double reachSquared = radius.evaluate(context) * radius.evaluate(context);
        int left = plants.evaluateInt(context);
        for (int attempt = tries.evaluateInt(context); attempt > 0 && left > 0; attempt--) {
            BlockPos cell = host.position().offset(random.nextIntBetweenInclusive(-reach, reach),
                    random.nextIntBetweenInclusive(-reach, reach), random.nextIntBetweenInclusive(-reach, reach));
            if (cell.distSqr(host.position()) <= reachSquared && spawnPlant(level, cell, random)) {
                left--;
            }
        }
        return true;
    }

    /**
     * Spawns a plant in an empty cell whose surface takes one.
     *
     * @param level  the server level
     * @param cell   the cell
     * @param random the random source
     * @return true when a plant was spawned
     */
    private static boolean spawnPlant(ServerLevel level, BlockPos cell, RandomSource random) {
        return level.isEmptyBlock(cell) && BloomSurface.classify(BloomSurface.of(level, cell))
                .map(spot -> BloomPlants.spawn(level, cell, spot, random)).orElse(false);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, plants, tries);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.BREAK_BLOCKS);
    }
}
