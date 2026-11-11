package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Void's Outsider, run each held tick of the channel: outside the pocket each
 * held tick stands the door the hold conjures before the player, growing with
 * the charge and moving into them over its last ticks, and the tick the hold
 * reaches its charge carries them into their room for the way in's cost; each
 * held tick inside feeds void into the room, whose extent grows on all sides
 * at once, each ring costing a base plus a step for each block of Manhattan
 * distance from the center for every cell it holds, the feed spreading each
 * ring's cost over the ring time. Void's Outsider is
 * {@code outsider charge=20 enter=6 ring_ticks=20 cell_cost=10 distance_cost=1},
 * a ring a second, so the room grows one block a second on every side.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 * decision outsider-room-grows-one-block-a-second-on-every-side
 *
 * @param charge   the held ticks the channel asks before it carries the caster in
 * @param enter    the void the way in costs (mB)
 * @param ringTicks    the ticks the hold takes to grow each ring
 * @param cellCost     the void a cell costs at the center (mB)
 * @param distanceCost the void each block of Manhattan distance from the center adds to a cell (mB)
 */
public record OutsiderStep(Expr charge, Expr enter, Expr ringTicks, Expr cellCost, Expr distanceCost)
        implements Step {

    private static final String NAME = "outsider";
    private static final String FIELD_CHARGE = "charge";
    private static final String FIELD_ENTER = "enter";
    private static final String FIELD_RING_TICKS = "ring_ticks";
    private static final String FIELD_CELL_COST = "cell_cost";
    private static final String FIELD_DISTANCE_COST = "distance_cost";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<OutsiderStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_CHARGE).forGetter(OutsiderStep::charge),
            Expr.CODEC.fieldOf(FIELD_ENTER).forGetter(OutsiderStep::enter),
            Expr.CODEC.fieldOf(FIELD_RING_TICKS).forGetter(OutsiderStep::ringTicks),
            Expr.CODEC.fieldOf(FIELD_CELL_COST).forGetter(OutsiderStep::cellCost),
            Expr.CODEC.fieldOf(FIELD_DISTANCE_COST).forGetter(OutsiderStep::distanceCost)
    ).apply(inst, OutsiderStep::new));

    /**
     * The registered type.
     */
    public static final StepType<OutsiderStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<OutsiderStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        OutsiderHost host = context.hostAs(OutsiderHost.class);
        if (host.standsInPocket()) {
            host.feedPlatform(ringTicks.evaluateInt(context), cellCost.evaluateInt(context),
                    distanceCost.evaluateInt(context));
        } else {
            channelToward(host, context);
        }
        return true;
    }

    /**
     * One held tick toward the pocket: the door conjured so far stands, the
     * charge shows, and the tick the hold reaches the charge carries the
     * player in.
     * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
     *
     * @param host    the channeling player's host
     * @param context the step's context
     */
    private void channelToward(OutsiderHost host, StepContext context) {
        int held = host.channelAim().map(ChannelAim::held).orElse(0);
        int charged = charge.evaluateInt(context);
        if (held <= 0 || held > charged) {
            return;
        }
        host.conjureDoor(held, charged);
        if (held == charged) {
            host.enterPocket(enter.evaluateInt(context));
        } else {
            host.showCharge(held, charged);
        }
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(charge, enter, ringTicks, cellCost, distanceCost);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.OUTSIDER);
    }
}
