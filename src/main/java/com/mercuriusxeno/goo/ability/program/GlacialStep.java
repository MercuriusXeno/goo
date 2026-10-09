package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.NovaRingPayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Frost's combo on a prism: for as long as the prism stands, every frozen
 * gauge within the radius holds and never thaws, and the still water and
 * lava within it stand frozen, water as magicked ice and lava as obsidian,
 * and every few seconds a flat frost ring pulses out from the prism to the
 * radius, Nova's ring, for the players watching. The step never finishes, so
 * the prism keeps its area frozen until mined:
 * {@code glacial radius=5}.
 * glacial-prism-holds-the-area-frozen
 *
 * @param radius the area's reach in blocks
 */
public record GlacialStep(double radius) implements Step {

    private static final String NAME = "glacial";
    private static final String FIELD_RADIUS = "radius";
    /** Ticks between the prism's passes over its water and lava. */
    static final int FREEZE_EVERY_TICKS = 20;
    /** Ticks between the prism's frost rings. */
    static final int PULSE_EVERY_TICKS = 60;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<GlacialStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(GlacialStep::radius)
    ).apply(inst, GlacialStep::new));

    /**
     * The registered type.
     */
    public static final StepType<GlacialStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<GlacialStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        FrostHost host = context.hostAs(FrostHost.class);
        ServerLevel level = host.level();
        BlockPos prism = host.position();
        GooServerState.of(level.getServer()).glacialFields().renew(level.dimension(), prism, radius,
                level.getGameTime());
        if (context.stepTicks() % FREEZE_EVERY_TICKS == 0) {
            FreezeBlocksStep.freezeWithin(level, host.frostCenter(), radius, true);
        }
        if (pulsesOn(context.stepTicks())) {
            EntityVisuals.sendToWatchersOf(level, host.frostCenter(),
                    new NovaRingPayload(host.frostCenter(), (float) radius));
        }
        return false;
    }

    /**
     * Whether a tick of the combo pulses the prism's frost ring: the first,
     * then every PULSE_EVERY_TICKS.
     *
     * @param stepTicks the ticks the step has run
     * @return true on a pulse tick
     */
    static boolean pulsesOn(int stepTicks) {
        return stepTicks % PULSE_EVERY_TICKS == 0;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.FROST);
    }
}
