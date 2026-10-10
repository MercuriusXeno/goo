package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.ReapSwellPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Growth's tap step, run as each drip lands: the landing block counts the
 * drip, and once the drips the JSON names have landed, the count starts over
 * and every plant Growth grows within the reach of the landing, a cube a
 * block out every way at reach 1, takes one random tick on Growth's own
 * rule, while the channel's green breeze swells small from where the drip
 * struck. Leaf Growth (tap) is
 * {@code pulse_plants drips=4 reach=1 swell_radius=1.5 swell_ticks=6}.
 * growth-drip-pulses-plants-below
 *
 * @param drips       the drips that make one pulse
 * @param reach       the blocks out from the landing every way the pulse ticks plants
 * @param swellRadius the radius the breeze swells to, in blocks
 * @param swellTicks  the ticks the breeze takes to swell
 */
public record PulsePlantsStep(int drips, int reach, float swellRadius, int swellTicks) implements Step {

    private static final String NAME = "pulse_plants";
    private static final String FIELD_DRIPS = "drips";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_SWELL_RADIUS = "swell_radius";
    private static final String FIELD_SWELL_TICKS = "swell_ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PulsePlantsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_DRIPS).forGetter(PulsePlantsStep::drips),
            Codec.INT.fieldOf(FIELD_REACH).forGetter(PulsePlantsStep::reach),
            Codec.FLOAT.fieldOf(FIELD_SWELL_RADIUS).forGetter(PulsePlantsStep::swellRadius),
            Codec.INT.fieldOf(FIELD_SWELL_TICKS).forGetter(PulsePlantsStep::swellTicks)
    ).apply(inst, PulsePlantsStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PulsePlantsStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PulsePlantsStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        DripHost host = context.hostAs(DripHost.class);
        if (host.countDrip() < drips) {
            return true;
        }
        host.resetDrips();
        ServerLevel level = host.level();
        BlockPos landing = host.position();
        for (BlockPos pos : BlockPos.betweenClosed(landing.offset(-reach, -reach, -reach),
                landing.offset(reach, reach, reach))) {
            TickPlantsStep.tickPlant(level, pos, level.getRandom());
        }
        Vec3 struck = context.hostAs(AnchoredWorldHost.class).anchor();
        BlockVisuals.sendToWatchers(level, landing, new ReapSwellPayload(struck, swellRadius, swellTicks));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.DRIP);
    }
}
