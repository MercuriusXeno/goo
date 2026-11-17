package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether's Infest block step: nether goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Leech its hex goo. The step names what
 * an Infest cell does in place of Decay's motes: the block map its gnaw
 * steps blocks along, the ticks between its gnaws and bites, the magic
 * damage a bite deals or heals an undead target by, and the chance each
 * block stage gnawed and each bite landed takes a layer from the cell:
 * {@code infest map=goo:decay gnaw_ticks=20 damage=2 deplete_chance=0.1}.
 * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
 * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param map           the id of the block map a gnaw steps a block along, Decay's
 * @param gnawTicks     the ticks between a cell's gnaws and bites
 * @param damage        the magic damage a bite deals, or heals an undead target by
 * @param depleteChance the chance a block stage gnawed or a bite landed takes a layer from the cell
 */
public record InfestStep(Identifier map, int gnawTicks, float damage, double depleteChance)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "infest";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_GNAW_TICKS = "gnaw_ticks";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_DEPLETE_CHANCE = "deplete_chance";
    /** The longest a cell waits between gnaws, a minute. */
    private static final int MOST_GNAW_TICKS = 1200;
    /** The hardest a bite lands, ten hearts. */
    private static final float MOST_DAMAGE = 20f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<InfestStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(InfestStep::map),
            Codec.intRange(1, MOST_GNAW_TICKS).fieldOf(FIELD_GNAW_TICKS).forGetter(InfestStep::gnawTicks),
            Codec.floatRange(0f, MOST_DAMAGE).fieldOf(FIELD_DAMAGE).forGetter(InfestStep::damage),
            Codec.doubleRange(0.0, 1.0).fieldOf(FIELD_DEPLETE_CHANCE).forGetter(InfestStep::depleteChance)
    ).apply(inst, InfestStep::new));

    /**
     * The registered type.
     */
    public static final StepType<InfestStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The infest step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the infest step, empty where the program lays no Infest goo
     */
    public static Optional<InfestStep> in(List<Step> program) {
        return program.stream().filter(InfestStep.class::isInstance).map(InfestStep.class::cast).findFirst();
    }

    @Override
    public StepType<InfestStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Infest goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.NETHER_INFEST.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.NETHER_INFEST.get());
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
