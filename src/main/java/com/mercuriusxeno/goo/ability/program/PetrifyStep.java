package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityPrice;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.petrify.PetrifyEvents;
import com.mercuriusxeno.goo.ability.petrify.Statues;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock petrify's mob step, run on each mob the fog holds each held tick: the
 * mob's petrify gauge fills by the JSON's amount and the mob slows with it,
 * and the tick it fills the mob becomes a statue block, which stands until
 * mined or until Heal's stream wakes it. A target that is no mob is left alone.
 * decision petrify-stone-encasement-and-calcify-map
 * decision heal-wakes-a-statue-at-a-nearly-full-gauge
 *
 * @param fill      how much the gauge fills each tick, evaluated on the mob
 * @param pointCost the mB each point of the gauge the step fills pays, through the host's placement price; zero
 *                  for a fill the ticks held already pay for
 */
public record PetrifyStep(Expr fill, Expr pointCost) implements Step {

    private static final String NAME = "petrify";
    private static final String FIELD_FILL = "fill";
    private static final String FIELD_POINT_COST = "point_cost";
    /** The point cost of a fill paying nothing of its own. */
    private static final Expr NO_POINT_COST = Expr.literal(0);
    /** The parts of a point a fill's price reads its points in, a power of two so a float's points read exactly. */
    private static final long POINT_PARTS = 1L << 20;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PetrifyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FILL).forGetter(PetrifyStep::fill),
            // petrify-pays-per-block-calcified-and-gauge-point
            Expr.CODEC.validate(AbilityPrice::checkedUnitPrice).optionalFieldOf(FIELD_POINT_COST, NO_POINT_COST)
                    .forGetter(PetrifyStep::pointCost)
    ).apply(inst, PetrifyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PetrifyStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * A petrify step whose fill pays nothing of its own.
     *
     * @param fill how much the gauge fills each tick, evaluated on the mob
     */
    public PetrifyStep(Expr fill) {
        this(fill, NO_POINT_COST);
    }

    @Override
    public StepType<PetrifyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        if (takesEntity(target) && target.level() instanceof ServerLevel level) {
            Mob mob = (Mob) target;
            Petrification before = mob.getData(GooAttachments.PETRIFICATION);
            Petrification after = before.fill((float) fill.evaluate(context), level.getGameTime());
            // petrify-pays-per-block-calcified-and-gauge-point: a fill the player cannot pay for does not happen
            if (!host.placementPrice().pay(priceOf(after.gauge() - before.gauge()))) {
                return true;
            }
            mob.setData(GooAttachments.PETRIFICATION, after);
            PetrifyEvents.slowBy(mob, after);
            PetrifyEvents.crackle(level, mob, after);
            if (after.full()) {
                Statues.encase(level, mob, host.thrower());
            }
        }
        return true;
    }

    /**
     * The price of the points a tick fills, the point cost times the points,
     * the points read in {@link #POINT_PARTS} parts so the price stays an exact fraction.
     * petrify-pays-per-block-calcified-and-gauge-point
     *
     * @param points the points of the gauge the tick fills
     * @return the exact mB they cost
     */
    private GooFraction priceOf(float points) {
        return AbilityPrice.exactUnitPrice(pointCost)
                .times(GooFraction.of(Math.round((double) points * POINT_PARTS), POINT_PARTS));
    }

    @Override
    public Stream<Expr> unitPrices() {
        return AbilityPrice.isNothing(pointCost) ? Stream.empty() : Stream.of(pointCost);
    }

    /**
     * Petrify takes a mob.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof Mob;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fill);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
