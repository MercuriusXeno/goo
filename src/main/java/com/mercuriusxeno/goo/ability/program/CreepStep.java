package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sculk's Creep block step: sculk goo lands where each of the stream's drips strikes, one layer for each drip,
 * through the volume cell's one landing, as Goop lays its slime. The step names what a Creep cell does as Host did,
 * the layers a cell spends turning the block under it to sculk when a sculk charge crosses it and how near the goo
 * a mob's death must fall for the goo to take its charge, starting at a sculk catalyst's own reach; and what it does
 * as Spores did, puffing its spore particle on a display tick on a random chance and on its period placing one fungal
 * bud on an edge space, spending a fixed count of layers:
 * {@code creep sculk_layers=3 radius=8 period=20 layers_per_bud=1 particle=goo:spore spore_chance=0.1}.
 * host-and-spores-combine-into-one-residual
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * sculk-charge-runs-along-host-goo-without-weakening
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param sculkLayers  the layers a Creep cell spends turning the block under it to sculk
 * @param radius       how far from a death a Creep cell still takes its charge, in blocks
 * @param period       the ticks between a cell's buddings
 * @param layersPerBud the layers each bud a cell places spends
 * @param particle     the particle a cell puffs
 * @param sporeChance  the chance a cell puffs its particle on a display tick
 */
public record CreepStep(int sculkLayers, int radius, int period, int layersPerBud, Identifier particle,
                        float sporeChance) implements TakesNoEntity, DripLanding {

    private static final String NAME = "creep";
    private static final String FIELD_SCULK_LAYERS = "sculk_layers";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_PERIOD = "period";
    private static final String FIELD_LAYERS_PER_BUD = "layers_per_bud";
    private static final String FIELD_PARTICLE = "particle";
    private static final String FIELD_SPORE_CHANCE = "spore_chance";
    /** The farthest a death may fall from Creep goo, bounding the blocks a death searches. */
    private static final int MOST_RADIUS = 32;
    /** The longest a cell waits between periods, a minute. */
    private static final int MOST_PERIOD = 1200;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CreepStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_SCULK_LAYERS).forGetter(CreepStep::sculkLayers),
            Codec.intRange(1, MOST_RADIUS).fieldOf(FIELD_RADIUS).forGetter(CreepStep::radius),
            Codec.intRange(1, MOST_PERIOD).fieldOf(FIELD_PERIOD).forGetter(CreepStep::period),
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_LAYERS_PER_BUD)
                    .forGetter(CreepStep::layersPerBud),
            Identifier.CODEC.fieldOf(FIELD_PARTICLE).forGetter(CreepStep::particle),
            Codec.floatRange(0f, 1f).fieldOf(FIELD_SPORE_CHANCE).forGetter(CreepStep::sporeChance)
    ).apply(inst, CreepStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CreepStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The particle a cell emits, and where.
     *
     * @param particle the particle's id
     * @param cell     the cell it rises from
     */
    public record SporeEmission(Identifier particle, BlockPos cell) {
    }

    /**
     * The creep step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the creep step, empty where the program lays no Creep goo
     */
    public static Optional<CreepStep> in(List<Step> program) {
        return program.stream().filter(CreepStep.class::isInstance).map(CreepStep.class::cast).findFirst();
    }

    /**
     * Whether a game time falls on the cell's period.
     *
     * @param gameTime the game time
     * @return true on each period's tick
     */
    public boolean onPeriod(long gameTime) {
        return gameTime % period == 0;
    }

    /**
     * The ticks from a game time to the next period's tick.
     *
     * @param gameTime the game time
     * @return the ticks to wait, a whole period on a period's own tick
     */
    public int ticksToNextPeriod(long gameTime) {
        return period - (int) (gameTime % period);
    }

    /**
     * What a cell puffs on a display tick: its spore particle for a roll under the chance, nothing otherwise.
     * operator ruling 2026-11-18: give them a random tick chance on these spores
     *
     * @param roll a random number from 0 below 1
     * @param cell the cell
     * @return the emission, empty for a roll at or over the chance
     */
    public Optional<SporeEmission> emissionFor(float roll, BlockPos cell) {
        return roll < sporeChance ? Optional.of(new SporeEmission(particle, cell)) : Optional.empty();
    }

    /**
     * The fungal bud a Creep cell places, which the block layer reads through the step rather than the registry.
     * spores-goo-spends-on-edge-growth-and-grows-to-buds
     *
     * @return the fungal bud at its first age
     */
    public BlockState budState() {
        return GooBlocks.FUNGAL_BUD.get().defaultBlockState();
    }

    @Override
    public StepType<CreepStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Creep goo as they strike.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Optional<CellFed> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return CellLanding.cellFed(host, strike, GooBlocks.SCULK_CREEP.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.SCULK_CREEP.get());
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
