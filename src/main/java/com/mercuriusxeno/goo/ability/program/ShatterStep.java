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
 * Crystal's Shatter block step: crystal goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Slick its slick, and glass shards
 * hang over every cell of it. A living thing every filter keeps moving
 * through the shards over a cell is cut: the strike runs on it on each tick
 * its interval, read on the thing, divides, and each cut has the deplete
 * chance of taking one layer from the cell under the shards.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * each-shatter-cut-has-a-chance-to-deplete-a-layer
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param where         the filters a thing must pass to be cut
 * @param interval      the tick period on which a thing in the shards is cut, read on the thing
 * @param strike        the steps run on the thing a cut lands on
 * @param depleteChance the chance in [0, 1] that a cut takes one layer from the cell
 */
public record ShatterStep(List<EntityFilter> where, Expr interval, List<Step> strike, Expr depleteChance)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "shatter";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_INTERVAL = "interval";
    private static final String FIELD_STRIKE = "strike";
    private static final String FIELD_DEPLETE_CHANCE = "deplete_chance";

    /**
     * Codec for the step's params. The strike list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<ShatterStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(ShatterStep::where),
            Expr.CODEC.optionalFieldOf(FIELD_INTERVAL, Expr.literal(1)).forGetter(ShatterStep::interval),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_STRIKE, List.of())
                    .forGetter(ShatterStep::strike),
            Expr.CODEC.optionalFieldOf(FIELD_DEPLETE_CHANCE, Expr.literal(0)).forGetter(ShatterStep::depleteChance)
    ).apply(inst, ShatterStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ShatterStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The shatter step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the shatter step, empty where the program lays no shatter goo
     */
    public static Optional<ShatterStep> in(List<Step> program) {
        return program.stream().filter(ShatterStep.class::isInstance).map(ShatterStep.class::cast).findFirst();
    }

    @Override
    public StepType<ShatterStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the crystal goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.CRYSTAL_SHATTER.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.CRYSTAL_SHATTER.get());
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(interval, depleteChance);
    }

    /**
     * Streams the interval and the deplete chance against the cut thing's
     * host, the one both are read on.
     *
     * @param host the host the step itself runs on
     * @return the hosted expressions
     */
    @Override
    public Stream<HostedExpr> hostedExpressions(HostKind host) {
        return expressions().map(expr -> new HostedExpr(expr, HostKind.ENTITY));
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
