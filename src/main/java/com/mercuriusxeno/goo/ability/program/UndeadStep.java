package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.nether.Undead;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes the host's target player count as undead and finishes: harming
 * heals it, healing harms it, smite strikes it and direct daylight burns it
 * the JSON's damage each second, until its held effect ends, by the player's
 * press, by running dry or by a brew's expiry. A target that is not a player
 * is left alone. Undead is {@code undead sun_damage=1}
 * (decision undead-nether-hearts-burn-in-sunlight).
 *
 * @param sunDamage the damage each second in direct daylight deals, evaluated when the step runs
 */
public record UndeadStep(Expr sunDamage) implements Step {

    private static final String NAME = "undead";
    private static final String FIELD_SUN_DAMAGE = "sun_damage";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<UndeadStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_SUN_DAMAGE).forGetter(UndeadStep::sunDamage)
    ).apply(inst, UndeadStep::new));

    /**
     * The registered type.
     */
    public static final StepType<UndeadStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<UndeadStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof Player player) {
            player.setData(GooAttachments.UNDEAD, Undead.of(sunDamage.evaluateFloat(context)));
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(sunDamage);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
