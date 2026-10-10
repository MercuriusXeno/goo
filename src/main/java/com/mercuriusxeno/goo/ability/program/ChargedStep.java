package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Charges the host's target player and finishes: Charged stands while the
 * held effect that ran this step stands, on the glove's unstable trickle
 * until the player ends it or the unstable runs dry, or prepaid for a drunk
 * brew's hour, and the held effect's end clears it; it carries no expiry of
 * its own. While charged, the player's channeled abilities take their JSON's
 * Charged multipliers.
 * decision charged-scales-channel-params-by-json
 */
public record ChargedStep() implements Step {

    private static final String NAME = "charged";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<ChargedStep> CODEC = MapCodec.unit(ChargedStep::new);

    /**
     * The registered type.
     */
    public static final StepType<ChargedStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Whether an entity stands charged now.
     *
     * @param entity the entity
     * @return true while its Charged held effect stands
     */
    public static boolean isCharged(LivingEntity entity) {
        return entity.getData(GooAttachments.CHARGED);
    }

    @Override
    public StepType<ChargedStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof Player player) {
            player.setData(GooAttachments.CHARGED, true);
        }
        return true;
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
