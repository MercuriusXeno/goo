package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.rewind.RewindEvents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rewinds the host's target one stage, its model shrinking over the JSON's
 * ticks: an adult that has a baby form becomes a baby, and a baby, or a mob
 * with no baby form, shrinks into its spawn egg. A mob already shrinking
 * into its egg, or a target that is no mob, is left alone.
 * rewind-shrinks-adult-to-baby-to-egg
 *
 * @param ticks the game ticks each shrink takes
 */
public record RegressStep(int ticks) implements Step {

    private static final String NAME = "regress";
    private static final String FIELD_TICKS = "ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<RegressStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_TICKS).forGetter(RegressStep::ticks)
    ).apply(inst, RegressStep::new));

    /**
     * The registered type.
     */
    public static final StepType<RegressStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RegressStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (!(context.hostAs(TargetHost.class).target() instanceof Mob mob) || RewindEvents.vanishing(mob)) {
            return true;
        }
        if (EntityScan.hasBabyForm(mob) && !mob.isBaby()) {
            RewindEvents.shrinkIntoBaby(mob, ticks);
        } else {
            RewindEvents.shrinkIntoEgg(mob, mob.level().getGameTime(), ticks);
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
