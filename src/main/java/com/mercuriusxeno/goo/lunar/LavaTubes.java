package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.Optional;

/**
 * Lunar's lava tubes, the long round tunnels left where lava rivers drained, as a density that opens their air: each
 * tube winds down a helix from above the highest ground, opening wherever it cuts the surface, through every zone to
 * the sea cavern's roof, so a player walks from the surface to the molten iron sea. The ground is cut into square
 * cells, each holding one tube or none, rolled from the tube noise.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 *
 * @param noise the seeded noise each cell's rolls read
 */
public record LavaTubes(DensityFunction.NoiseHolder noise) implements DensityFunction {

    /** The data codec of the {@code goo:lava_tubes} density function type, naming the noise its rolls read. */
    public static final MapCodec<LavaTubes> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(LavaTubes::noise)
    ).apply(instance, LavaTubes::new));
    private static final KeyDispatchDataCodec<LavaTubes> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    /** The noise the tubes' cells roll from. */
    public static final ResourceKey<NormalNoise.NoiseParameters> NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_lava_tubes"));

    /** A tube's highest point, over the highest ground, so every tube opens on the surface. */
    public static final int TOP_Y = 256;
    /** A tube's lowest point, under the sea cavern's lowest roof, so every tube opens into the cavern. */
    public static final int BOTTOM_Y = LunarDepths.SEA_ZONE_TOP_Y - 22;
    /** A cell's side in blocks. */
    static final int CELL = 192;
    /** The share of cells holding a tube. */
    static final double TUBE_CHANCE = 0.5;
    /** The helix's radius about its axis, and how far it falls each turn: a slope a player walks down. */
    static final double HELIX_RADIUS = 20.0;
    static final double HELIX_PITCH = 48.0;
    /** The tunnel's radius about the helix. */
    public static final double TUBE_RADIUS = 3.5;
    /** The tube's centre keeps this far inside its cell, so a column reads its own cell's tube alone. */
    private static final double CELL_MARGIN = HELIX_RADIUS + TUBE_RADIUS + 1.0;
    private static final double FULL_TURN = 2.0 * Math.PI;
    /** The helix's slope, as the cosine across which a height difference narrows to the tunnel's own distance. */
    private static final double SLOPE_COSINE = FULL_TURN * HELIX_RADIUS
            / Math.hypot(FULL_TURN * HELIX_RADIUS, HELIX_PITCH);
    private static final double SOLID = 1.0;
    /** The density at the tunnel's heart, the most open it reads. */
    private static final double OPEN = -1.0;
    /** Tubes roll on indices clear of the craters' rolls. */
    private static final int PRESENCE = 20;
    private static final int OFFSET_X = 21;
    private static final int OFFSET_Z = 22;
    private static final int PHASE = 23;

    /**
     * One lava tube: its helix's axis and the angle its helix starts from at the top.
     *
     * @param centerX the axis's x
     * @param centerZ the axis's z
     * @param phase   the helix's angle at {@link #TOP_Y}, in radians
     */
    public record Tube(double centerX, double centerZ, double phase) {

        /**
         * The point on the tube's helix at a height.
         *
         * @param y the height
         * @return the helix's point there
         */
        public BlockPos axisAt(int y) {
            double angle = phase + FULL_TURN * (TOP_Y - y) / HELIX_PITCH;
            return BlockPos.containing(centerX + HELIX_RADIUS * Math.cos(angle), y,
                    centerZ + HELIX_RADIUS * Math.sin(angle));
        }

        /**
         * Whether the tube's tunnel crosses a column at some height: whether the column stands within the tunnel's
         * reach of the helix's circle.
         *
         * @param x the column's x
         * @param z the column's z
         * @return true where the tunnel passes through the column
         */
        public boolean crossesColumn(int x, int z) {
            return Math.abs(Math.hypot(x - centerX, z - centerZ) - HELIX_RADIUS) <= TUBE_RADIUS + 1.0;
        }

        /**
         * A block's distance from the tube's helix, in blocks.
         *
         * @param x the block's x
         * @param y the block's y
         * @param z the block's z
         * @return the distance
         */
        public double distance(int x, int y, int z) {
            return helixDistance(x - centerX, y, z - centerZ, phase);
        }
    }

    /**
     * A block's distance from a helix falling from {@link #TOP_Y} about an axis at the origin: the block's distance
     * out from the helix's radius, and its height from the nearest turn narrowed across the helix's slope.
     *
     * @param dx    the block's x from the axis
     * @param y     the block's y
     * @param dz    the block's z from the axis
     * @param phase the helix's angle at the top
     * @return the distance
     */
    static double helixDistance(double dx, int y, double dz, double phase) {
        double out = Math.hypot(dx, dz) - HELIX_RADIUS;
        double turns = (Math.atan2(dz, dx) - phase) / FULL_TURN;
        double helixFall = (turns - Math.floor(turns)) * HELIX_PITCH;
        double fromTurn = ((TOP_Y - y - helixFall) % HELIX_PITCH + HELIX_PITCH) % HELIX_PITCH;
        double up = Math.min(fromTurn, HELIX_PITCH - fromTurn) * SLOPE_COSINE;
        return Math.hypot(out, up);
    }

    /**
     * The tube a cell holds, if it holds one.
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @param rolls each cell's rolls
     * @return the tube, or empty
     */
    static Optional<Tube> tubeIn(int cellX, int cellZ, CraterField.CellRolls rolls) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= TUBE_CHANCE) {
            return Optional.empty();
        }
        double span = CELL - CELL_MARGIN - CELL_MARGIN;
        return Optional.of(new Tube(cellX * CELL + CELL_MARGIN + rolls.roll(cellX, cellZ, OFFSET_X) * span,
                cellZ * CELL + CELL_MARGIN + rolls.roll(cellX, cellZ, OFFSET_Z) * span,
                rolls.roll(cellX, cellZ, PHASE) * FULL_TURN));
    }

    /**
     * The tube field a world's seeded tube noise draws, as its random state wires {@link #NOISE}.
     *
     * @param noise the seeded tube noise
     * @return the field that noise draws
     */
    public static LavaTubes seeded(NormalNoise noise) {
        return new LavaTubes(new DensityFunction.NoiseHolder(Holder.direct(noise.parameters()), noise));
    }

    /**
     * The tube a cell of this field holds, if it holds one.
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @return the tube, or empty
     */
    public Optional<Tube> tubeInCell(int cellX, int cellZ) {
        return tubeIn(cellX, cellZ, this::roll);
    }

    /**
     * Whether some tube's tunnel crosses a column at some height.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true where a tunnel passes through the column
     */
    public boolean crossesColumnAt(int x, int z) {
        return tubeInCell(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)).map(tube -> tube.crossesColumn(x, z))
                .orElse(false);
    }

    private double roll(int cellX, int cellZ, int roll) {
        return CraterField.rollFrom(noise, cellX, cellZ, roll);
    }

    /** Opens a tube's air, negative within its radius of the helix; solid everywhere else. */
    @Override
    public double compute(DensityFunction.FunctionContext context) {
        int y = context.blockY();
        if (y > TOP_Y || y < BOTTOM_Y) {
            return SOLID;
        }
        Optional<Tube> tube = tubeInCell(Math.floorDiv(context.blockX(), CELL), Math.floorDiv(context.blockZ(), CELL));
        if (tube.isEmpty()) {
            return SOLID;
        }
        double distance = tube.get().distance(context.blockX(), y, context.blockZ());
        return Math.min(SOLID, (distance - TUBE_RADIUS) / TUBE_RADIUS);
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new LavaTubes(visitor.visitNoise(noise)));
    }

    @Override
    public double minValue() {
        return OPEN;
    }

    @Override
    public double maxValue() {
        return SOLID;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
