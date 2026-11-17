package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Slime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Jelly's Nectar block step: Jelly's goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Leech its hex goo. The step names the
 * guided throw, an aim passing a mob within the guide radius of the look
 * ray, inside the guide reach, throwing the drips onto that mob; the reach
 * out to which a Nectar cell draws the animals and hostiles about it; and
 * the ticks hostiles reaching it together fight over it:
 * {@code nectar_goo guide_radius=2 guide_reach=16 reach=12 fight_ticks=100}.
 * A drip ends on an animal or a slime it strikes, which the nectar step
 * feeds, and passes through any other living thing.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param guideRadius the blocks off the look ray a mob may lie and still take the guide
 * @param guideReach  the blocks along the look ray out to which a mob takes the guide
 * @param reach       the blocks out to which a Nectar cell draws the animals and hostiles about it
 * @param fightTicks  the ticks hostiles reaching a Nectar cell together fight over it
 */
public record NectarGooStep(double guideRadius, double guideReach, double reach, int fightTicks)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "nectar_goo";
    private static final String FIELD_GUIDE_RADIUS = "guide_radius";
    private static final String FIELD_GUIDE_REACH = "guide_reach";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_FIGHT_TICKS = "fight_ticks";
    /** The farthest a cell draws a mob from. */
    private static final double MOST_REACH = 32;
    /** The longest a fight over a cell lasts, a minute. */
    private static final int MOST_FIGHT_TICKS = 1200;
    /** The widest the guide reaches off the look ray. */
    private static final double MOST_GUIDE_RADIUS = 8;
    /** The farthest the guide reaches along the look ray. */
    private static final double MOST_GUIDE_REACH = 48;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<NectarGooStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, MOST_GUIDE_RADIUS).fieldOf(FIELD_GUIDE_RADIUS).forGetter(NectarGooStep::guideRadius),
            Codec.doubleRange(0, MOST_GUIDE_REACH).fieldOf(FIELD_GUIDE_REACH).forGetter(NectarGooStep::guideReach),
            Codec.doubleRange(0, MOST_REACH).fieldOf(FIELD_REACH).forGetter(NectarGooStep::reach),
            Codec.intRange(0, MOST_FIGHT_TICKS).fieldOf(FIELD_FIGHT_TICKS).forGetter(NectarGooStep::fightTicks)
    ).apply(inst, NectarGooStep::new));

    /**
     * The registered type.
     */
    public static final StepType<NectarGooStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The nectar goo step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the nectar goo step, empty where the program lays no Nectar goo
     */
    public static Optional<NectarGooStep> in(List<Step> program) {
        return program.stream().filter(NectarGooStep.class::isInstance).map(NectarGooStep.class::cast).findFirst();
    }

    @Override
    public StepType<NectarGooStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Nectar goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.JELLY_NECTAR.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.JELLY_NECTAR.get());
    }

    /**
     * A drip ends on the animals and slimes Nectar feeds, and passes through
     * the rest, so a drip thrown onto a hostile lays its goo at its feet.
     * nectar-goo-keeps-feeds-effects-as-a-universal-food
     *
     * @param living a living thing the drip meets
     * @return true for an animal or a slime
     */
    @Override
    public boolean endsOn(LivingEntity living) {
        return living instanceof Animal || living instanceof Slime;
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
