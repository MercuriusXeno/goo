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
 * Metal's Spikes block step: metal goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Leech its hex goo, so a held stream
 * lays a thick coat. A living thing every filter keeps walking on a cell of
 * it is impaled: spikes rise under it and the strike runs on it, again each
 * time the cooldown has passed since the last, and each strike has the
 * spend chance of taking one layer from the cell under it, so a thicker
 * coat lands more strikes. The spikes retract on the retract frame,
 * sounding the retract shink.
 * metal-world-goo-is-named-spikes
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * metal-spends-charge-by-chance
 * urchin-rests-bristled-and-spears
 * urchin-spikes-shink-out-and-shink-back
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param where        the filters a thing walking on the goo must pass to be struck
 * @param cooldown     the ticks after a strike before the goo strikes the same thing again
 * @param spendChance  the chance in [0, 1] that a strike takes one layer from the cell under the struck thing
 * @param strike       the steps run on the thing a strike lands on
 * @param retractShink the shink the spikes sound as they retract
 * @param retractFrame the ticks after a strike its spikes start retracting and sound the retract shink
 */
public record SpikesStep(List<EntityFilter> where, int cooldown, double spendChance, List<Step> strike,
                         SoundCue retractShink, int retractFrame) implements TakesNoEntity, DripLanding {

    private static final String NAME = "spikes";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_COOLDOWN = "cooldown";
    private static final String FIELD_SPEND_CHANCE = "spend_chance";
    private static final String FIELD_STRIKE = "strike";
    private static final String FIELD_RETRACT_SHINK = "retract_shink";
    private static final String FIELD_RETRACT_FRAME = "retract_frame";

    /**
     * Codec for the step's params. The strike list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<SpikesStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(SpikesStep::where),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_COOLDOWN).forGetter(SpikesStep::cooldown),
            Codec.doubleRange(0, 1).fieldOf(FIELD_SPEND_CHANCE).forGetter(SpikesStep::spendChance),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_STRIKE, List.of())
                    .forGetter(SpikesStep::strike),
            SoundCue.CODEC.fieldOf(FIELD_RETRACT_SHINK).forGetter(SpikesStep::retractShink),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_RETRACT_FRAME).forGetter(SpikesStep::retractFrame)
    ).apply(inst, SpikesStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SpikesStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The spikes step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the spikes step, empty where the program lays no Spikes goo
     */
    public static Optional<SpikesStep> in(List<Step> program) {
        return program.stream().filter(SpikesStep.class::isInstance).map(SpikesStep.class::cast).findFirst();
    }

    @Override
    public StepType<SpikesStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Spikes goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.METAL_SPIKES.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.METAL_SPIKES.get());
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
