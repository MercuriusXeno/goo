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
 * Leaf's Vines block step: Leaf's goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Leech its hex goo. A living thing
 * crossing the goo is held by its vines; the step names the thorn damage a
 * held thing takes each tick it moves and what fire damage to it is
 * multiplied by: {@code vines thorns=1 fire_factor=1.5}.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-unpack-root-and-thorn
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param thorns     the thorn damage a held thing takes each tick it moves
 * @param fireFactor what fire damage to a held thing that is not fire immune is multiplied by
 */
public record VinesStep(float thorns, float fireFactor) implements TakesNoEntity, DripLanding {

    private static final String NAME = "vines";
    private static final String FIELD_THORNS = "thorns";
    private static final String FIELD_FIRE_FACTOR = "fire_factor";
    /** The most thorn damage a tick deals: a whole player's health. */
    private static final float MOST_THORNS = 20.0F;
    /** The most fire damage is multiplied by. */
    private static final float MOST_FIRE_FACTOR = 10.0F;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VinesStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.floatRange(0, MOST_THORNS).fieldOf(FIELD_THORNS).forGetter(VinesStep::thorns),
            Codec.floatRange(1, MOST_FIRE_FACTOR).fieldOf(FIELD_FIRE_FACTOR).forGetter(VinesStep::fireFactor)
    ).apply(inst, VinesStep::new));

    /**
     * The registered type.
     */
    public static final StepType<VinesStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The vines step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the vines step, empty where the program lays no Vines goo
     */
    public static Optional<VinesStep> in(List<Step> program) {
        return program.stream().filter(VinesStep.class::isInstance).map(VinesStep.class::cast).findFirst();
    }

    @Override
    public StepType<VinesStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Vines goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.LEAF_VINES.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.LEAF_VINES.get());
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
