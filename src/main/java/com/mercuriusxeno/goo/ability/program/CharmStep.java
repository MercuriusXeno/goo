package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Charms the host's target for the player who threw the goo and finishes:
 * until that player hurts it, the target fights whatever targets that player
 * and follows them otherwise. A throw with no player behind it charms nothing,
 * and a mob {@link OwnMinions#charmRefuses} refuses keeps what it holds.
 * charm-glisten-and-icon-over-the-head
 * charm-holds-until-struck
 * rallied-mobs-are-immune-to-charm
 * other-players-rally-and-charm-refuse-your-minions
 */
public record CharmStep() implements Step {

    private static final String NAME = "charm";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<CharmStep> CODEC = MapCodec.unit(CharmStep::new);

    /**
     * The registered type.
     */
    public static final StepType<CharmStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CharmStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.thrower() instanceof Player charmer && !refusesFor(charmer, host.target())) {
            host.target().setData(GooAttachments.CHARMED, new Charmed(charmer.getUUID()));
        }
        return true;
    }

    /**
     * Charm refuses a led mob and a mob another player charmed.
     * refused-rally-or-charm-spends-no-goo
     *
     * @param caster the caster the Charm would act for
     * @param entity the candidate
     * @return true where the Charm leaves the entity untouched
     */
    @Override
    public boolean refusesFor(Entity caster, Entity entity) {
        return OwnMinions.charmRefuses(caster, entity);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
