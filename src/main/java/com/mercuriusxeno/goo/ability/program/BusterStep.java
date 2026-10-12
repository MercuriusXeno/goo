package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.unstable.BusterEvents;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lays Buster on the host's target player and finishes: while its held
 * effect stands, each melee hit the player lands strikes every living thing
 * within the radius of the struck one, and each block the player breaks
 * breaks the 3x3x3 around it no harder than itself. Cast from the glove it
 * stands on the unstable upkeep until ended; drunk as a brew it stands for
 * the brew's hour, and the held effect's end clears it.
 * buster-makes-tools-and-attacks-aoe
 *
 * @param radius how far from the struck entity, in blocks, a hit's blast reaches
 */
public record BusterStep(Expr radius) implements Step {

    private static final String NAME = "buster";
    private static final String FIELD_RADIUS = "radius";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BusterStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(BusterStep::radius)
    ).apply(inst, BusterStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BusterStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BusterStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof Player player) {
            BusterEvents.lay(player, radius.evaluateFloat(context));
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
