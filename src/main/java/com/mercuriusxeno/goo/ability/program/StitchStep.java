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
 * Yarn's Stitch block step: yarn's goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Vines lays its leaf goo. A passive crossing the goo passes as
 * if it were not there; a hostile in it is tangled in strands, slowed as a
 * cobweb slows, and fire on a tangled hostile is multiplied as on a mob
 * Vines roots. The step names that multiple: {@code stitch fire_factor=1.5}.
 * stitch-is-yarns-residual-that-turns-slimes-into-passive-mobs
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 *
 * @param fireFactor what fire damage to a tangled thing that is not fire immune is multiplied by
 */
public record StitchStep(float fireFactor) implements TakesNoEntity, DripLanding {

    private static final String NAME = "stitch";
    private static final String FIELD_FIRE_FACTOR = "fire_factor";
    /** The most fire damage is multiplied by. */
    private static final float MOST_FIRE_FACTOR = 10.0F;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<StitchStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.floatRange(1, MOST_FIRE_FACTOR).fieldOf(FIELD_FIRE_FACTOR).forGetter(StitchStep::fireFactor)
    ).apply(inst, StitchStep::new));

    /**
     * The registered type.
     */
    public static final StepType<StitchStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The stitch step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the stitch step, empty where the program lays no Stitch goo
     */
    public static Optional<StitchStep> in(List<Step> program) {
        return program.stream().filter(StitchStep.class::isInstance).map(StitchStep.class::cast).findFirst();
    }

    @Override
    public StepType<StitchStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Stitch goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.YARN_STITCH.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.YARN_STITCH.get());
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
