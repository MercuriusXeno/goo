package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Osseous's Spurs block step: bone goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Spikes lays its metal goo. A living thing every filter keeps
 * walking on a cell of it is shot: the cell fires a bone spike at it at the
 * shot speed, and the strike runs on what the spike meets, again each time
 * the cooldown has passed since the last shot, and each shot has the spend
 * chance of taking one layer from the cell.
 * spurs-shoots-bone-spikes-at-walkers
 *
 * @param where       the filters a thing walking on the goo must pass to be shot at
 * @param cooldown    the ticks after a shot before the goo shoots at the same thing again
 * @param spendChance the chance in [0, 1] that a shot takes one layer from the cell it leaves
 * @param shotSpeed   the bone spike's launch speed, blocks per tick
 * @param strike      the steps run on the thing a bone spike meets
 */
public record SpursStep(List<EntityFilter> where, int cooldown, double spendChance, double shotSpeed,
                        List<Step> strike) implements TakesNoEntity, DripLanding {

    private static final String NAME = "spurs";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_COOLDOWN = "cooldown";
    private static final String FIELD_SPEND_CHANCE = "spend_chance";
    private static final String FIELD_SHOT_SPEED = "shot_speed";
    private static final String FIELD_STRIKE = "strike";

    /**
     * Codec for the step's params. The strike list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<SpursStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(SpursStep::where),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_COOLDOWN).forGetter(SpursStep::cooldown),
            Codec.doubleRange(0, 1).fieldOf(FIELD_SPEND_CHANCE).forGetter(SpursStep::spendChance),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_SHOT_SPEED).forGetter(SpursStep::shotSpeed),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_STRIKE, List.of())
                    .forGetter(SpursStep::strike)
    ).apply(inst, SpursStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SpursStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The spurs step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the spurs step, empty where the program lays no Spurs goo
     */
    public static Optional<SpursStep> in(List<Step> program) {
        return program.stream().filter(SpursStep.class::isInstance).map(SpursStep.class::cast).findFirst();
    }

    @Override
    public StepType<SpursStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Spurs goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.OSSEOUS_SPURS.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.OSSEOUS_SPURS.get());
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }

    @Override
    public Stream<Step> children() {
        return strike.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return strike.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
    }
}
