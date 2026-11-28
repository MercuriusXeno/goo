package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.durable.ShieldHolds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Durable's Shield, run each tick of a held channel on the channeling
 * player: the goo stands as a shield on the glove arm, and the player blocks
 * as with a vanilla shield raised, by vanilla's own blocking rules. A left
 * click on the mob in front while it stands bashes that mob. It spends
 * durable goo for each point of damage it blocks and for each bash, and
 * nothing for the time it stands.
 * durable-goo-forms-a-shield-at-will-and-shield-bashes
 *
 * @param costPerPointBlocked the durable goo each point of blocked damage costs, in mB
 * @param bashCost            the durable goo each bash costs, in mB
 * @param bashDamage          the damage a bash deals
 * @param bashKnockback       the knockback a bash deals, as vanilla's knockback strength
 */
public record ShieldStep(int costPerPointBlocked, int bashCost, float bashDamage, float bashKnockback)
        implements Step {

    private static final String NAME = "shield";
    private static final String FIELD_COST_PER_POINT_BLOCKED = "cost_per_point_blocked";
    private static final String FIELD_BASH_COST = "bash_cost";
    private static final String FIELD_BASH_DAMAGE = "bash_damage";
    private static final String FIELD_BASH_KNOCKBACK = "bash_knockback";

    /** Codec for the step's params. */
    public static final MapCodec<ShieldStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_COST_PER_POINT_BLOCKED)
                    .forGetter(ShieldStep::costPerPointBlocked),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_BASH_COST).forGetter(ShieldStep::bashCost),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf(FIELD_BASH_DAMAGE).forGetter(ShieldStep::bashDamage),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf(FIELD_BASH_KNOCKBACK).forGetter(ShieldStep::bashKnockback)
    ).apply(inst, ShieldStep::new));

    /** The registered type. */
    public static final StepType<ShieldStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The shield step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the shield step, empty where the program raises no shield
     */
    public static Optional<ShieldStep> in(List<Step> program) {
        return program.stream().filter(ShieldStep.class::isInstance).map(ShieldStep.class::cast).findFirst();
    }

    /**
     * The durable goo a block costs: each point blocked at the step's price, rounded up to the whole mB.
     *
     * @param blocked the damage blocked
     * @return the cost in mB
     */
    public int blockCost(float blocked) {
        return (int) Math.ceil(blocked * costPerPointBlocked);
    }

    @Override
    public StepType<ShieldStep> type() {
        return TYPE;
    }

    /**
     * Stands the shield up for this tick of the hold.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof ServerPlayer player) {
            ShieldHolds.raise(player, this);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.TARGET);
    }
}
