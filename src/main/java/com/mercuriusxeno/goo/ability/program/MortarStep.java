package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock's Mortar block step: rock goo lands where each of the stream's drips
 * strikes, one layer for each drip, through the volume cell's one landing,
 * as Infest lays its nether goo and Vines its leaf goo. The step names what a
 * Mortar cell does in place of Petrify's fog: the block map its hardening
 * steps blocks along, Petrify's calcify map; the ticks between its acts; how
 * much a mob standing in it petrifies each act, read on the mob as Petrify's
 * fill is; and the chance each petrifying step and each block hardened takes
 * a layer from the cell:
 * {@code mortar map=goo:calcify ticks=10 fill=7.5/pow(max_health,0.5) deplete_chance=0.1}.
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
 * layer-of-rock-goo-is-a-stone-block
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param map           the id of the block map a hardening steps a block along, Petrify's calcify map
 * @param ticks         the ticks between a cell's acts
 * @param fill          how much a mob standing in the cell petrifies each act, read on the mob
 * @param depleteChance the chance a petrifying step or a block hardened takes a layer from the cell
 */
public record MortarStep(Identifier map, int ticks, Expr fill, double depleteChance)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "mortar";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_TICKS = "ticks";
    private static final String FIELD_FILL = "fill";
    private static final String FIELD_DEPLETE_CHANCE = "deplete_chance";
    /** The longest a cell waits between acts, a minute. */
    private static final int MOST_TICKS = 1200;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<MortarStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(MortarStep::map),
            Codec.intRange(1, MOST_TICKS).fieldOf(FIELD_TICKS).forGetter(MortarStep::ticks),
            Expr.CODEC.fieldOf(FIELD_FILL).forGetter(MortarStep::fill),
            Codec.doubleRange(0.0, 1.0).fieldOf(FIELD_DEPLETE_CHANCE).forGetter(MortarStep::depleteChance)
    ).apply(inst, MortarStep::new));

    /**
     * The registered type.
     */
    public static final StepType<MortarStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The mortar step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the mortar step, empty where the program lays no Mortar goo
     */
    public static Optional<MortarStep> in(List<Step> program) {
        return program.stream().filter(MortarStep.class::isInstance).map(MortarStep.class::cast).findFirst();
    }

    /**
     * How much a living thing standing in a Mortar cell petrifies in one act,
     * its fill read on that thing's health as Petrify's fill is.
     *
     * @param living the living thing standing in the cell
     * @return the gauge it gains
     */
    public float fillFor(LivingEntity living) {
        return fill.evaluateFloat(name -> switch (name) {
            case HostVariables.MAX_HEALTH -> OptionalDouble.of(living.getMaxHealth());
            case HostVariables.HEALTH -> OptionalDouble.of(living.getHealth());
            default -> OptionalDouble.empty();
        });
    }

    @Override
    public StepType<MortarStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Mortar goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.ROCK_MORTAR.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.ROCK_MORTAR.get());
    }

    /**
     * Names no expression for the stream's host to read: the fill is read on
     * the mob standing in a cell, not on the hold.
     *
     * @return no expressions
     */
    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
