package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Sealed hierarchy for ability cost formulas. Each variant computes
 * the mB cost of the Nth blob in a throw sequence, where N is the
 * sequence-aware stack position (landed + in-flight at the target).
 */
public sealed interface AbilityCost {

    String FORMULA_QUADRATIC = "quadratic";
    String FORMULA_BLOCK_COUNT = "block_count";
    String FORMULA_POWER_LAW = "power_law";

    /** The formula names a cost JSON may carry; any other refuses at load. */
    Set<String> FORMULAS = Set.of(FORMULA_QUADRATIC, FORMULA_BLOCK_COUNT, FORMULA_POWER_LAW);

    /** The load refusal for a formula name outside {@link #FORMULAS}. */
    String UNKNOWN_FORMULA = "Unknown cost formula '%s'; expected one of %s";

    /** Codec that reads "formula" and all flat fields, constructing the right type. */
    Codec<AbilityCost> CODEC = RecordCodecBuilder.<AbilityCost>create(
            inst -> inst.group(
                    Codec.STRING.validate(AbilityCost::knownFormula).fieldOf("formula")
                            .forGetter(AbilityCost::formulaName),
                    Codec.INT.optionalFieldOf("baseCost", 0).forGetter(c -> switch (c) {
                        case Quadratic q -> q.baseCost();
                        case PowerLaw p -> p.baseCost();
                        default -> 0;
                    }),
                    Codec.FLOAT.optionalFieldOf("a", 0f).forGetter(c ->
                            c instanceof Quadratic q ? q.a() : 0f),
                    Codec.FLOAT.optionalFieldOf("b", 0f).forGetter(c ->
                            c instanceof Quadratic q ? q.b() : 0f),
                    Codec.FLOAT.optionalFieldOf("c", 0f).forGetter(c ->
                            c instanceof Quadratic q ? q.c() : 0f),
                    Codec.FLOAT.optionalFieldOf("mult", 0f).forGetter(c ->
                            c instanceof PowerLaw p ? p.mult() : 0f),
                    Codec.INT.optionalFieldOf("costPerBlock", 0).forGetter(c ->
                            c instanceof BlockCount b ? b.costPerBlock() : 0)
            ).apply(inst, AbilityCost::fromFields));

    /**
     * Computes the cost in mB for throwing blob number {@code n} in the sequence.
     *
     * @param n               the 0-based sequence position (0 = first throw)
     * @param footprintBlocks the blocks the ability's footprint covers at a stack count
     * @return the cost in microblobs
     */
    int costForStack(int n, IntUnaryOperator footprintBlocks);

    /**
     * Accepts a formula name the cost variants hold and refuses any other.
     *
     * @param formula the formula name
     * @return the name, or an error naming it
     */
    private static DataResult<String> knownFormula(String formula) {
        return FORMULAS.contains(formula)
                ? DataResult.success(formula)
                : DataResult.error(() -> UNKNOWN_FORMULA.formatted(formula, FORMULAS));
    }

    /** Constructs the correct AbilityCost variant from flat codec fields.
     *
     * @param formula     the formula name
     * @param baseCost    the base cost (quadratic/power_law)
     * @param a           quadratic coefficient
     * @param b           linear coefficient
     * @param c           constant term
     * @param mult        power law multiplier
     * @param costPerBlock block count cost
     * @return the constructed cost variant
     */
    static AbilityCost fromFields(String formula, int baseCost,
            float a, float b, float c, float mult, int costPerBlock) {
        return switch (formula) {
            case FORMULA_QUADRATIC -> new Quadratic(baseCost, a, b, c);
            case FORMULA_BLOCK_COUNT -> new BlockCount(costPerBlock);
            case FORMULA_POWER_LAW -> new PowerLaw(baseCost, mult);
            default -> throw new IllegalArgumentException(formula);
        };
    }

    /**
     * Returns the formula name for codec dispatch.
     *
     * @return the formula identifier string
     */
    String formulaName();

    /**
     * Quadratic: {@code baseCost * (a * N^2 + b * N + c)}.
     * Covers constant (a=0, b=0, c=1), linear (a=0, b=1, c=1),
     * and quadratic scaling.
     *
     * @param baseCost the base cost in mB
     * @param a        quadratic coefficient
     * @param b        linear coefficient
     * @param c        constant term
     */
    record Quadratic(int baseCost, float a, float b, float c) implements AbilityCost {

        static final MapCodec<Quadratic> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("baseCost").forGetter(Quadratic::baseCost),
                Codec.FLOAT.fieldOf("a").forGetter(Quadratic::a),
                Codec.FLOAT.fieldOf("b").forGetter(Quadratic::b),
                Codec.FLOAT.fieldOf("c").forGetter(Quadratic::c)
        ).apply(inst, Quadratic::new));

        @Override
        public int costForStack(int n, IntUnaryOperator footprintBlocks) {
            return (int) (baseCost * (a * n * n + b * n + c));
        }

        @Override
        public String formulaName() { return FORMULA_QUADRATIC; }
    }

    /**
     * Block-count: {@code costPerBlock * marginalBlocks(N)}, the blocks
     * stack {@code N + 1} adds to the ability's footprint over stack {@code N}.
     *
     * @param costPerBlock mB cost per additional block in the footprint
     */
    record BlockCount(int costPerBlock) implements AbilityCost {

        static final MapCodec<BlockCount> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("costPerBlock").forGetter(BlockCount::costPerBlock)
        ).apply(inst, BlockCount::new));

        @Override
        public int costForStack(int n, IntUnaryOperator footprintBlocks) {
            int marginalBlocks = footprintBlocks.applyAsInt(n + 1) - footprintBlocks.applyAsInt(n);
            return costPerBlock * marginalBlocks;
        }

        @Override
        public String formulaName() { return FORMULA_BLOCK_COUNT; }
    }

    /**
     * Power law: {@code baseCost * mult^N}.
     * Each successive stack costs {@code mult} times more than the last.
     *
     * @param baseCost the cost of the first throw (N=0)
     * @param mult     the per-stack multiplier
     */
    record PowerLaw(int baseCost, float mult) implements AbilityCost {

        static final MapCodec<PowerLaw> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("baseCost").forGetter(PowerLaw::baseCost),
                Codec.FLOAT.fieldOf("mult").forGetter(PowerLaw::mult)
        ).apply(inst, PowerLaw::new));

        @Override
        public int costForStack(int n, IntUnaryOperator footprintBlocks) {
            return (int) (baseCost * Math.pow(mult, n));
        }

        @Override
        public String formulaName() { return FORMULA_POWER_LAW; }
    }
}
