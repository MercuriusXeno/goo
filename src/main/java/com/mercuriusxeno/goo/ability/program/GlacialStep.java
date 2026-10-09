package com.mercuriusxeno.goo.ability.program;

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
 * lava within it stand frozen, water as magicked ice and lava as obsidian.
 * The step never finishes, so the prism keeps its area frozen until mined:
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
        return false;
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
