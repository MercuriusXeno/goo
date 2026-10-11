package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.world.Reflectors;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes the prism the program runs on a reflector, and refreshes its links
 * every few ticks for as long as the prism stands: it never finishes, so the
 * prism keeps running it. Glow's Reflector is {@code reflector every=20}.
 * decision reflector-rails-carry-the-brightest-light
 *
 * @param every the ticks between refreshes
 */
public record ReflectorStep(int every) implements Step {

    private static final String NAME = "reflector";
    private static final String FIELD_EVERY = "every";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ReflectorStep> CODEC = Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_EVERY)
            .xmap(ReflectorStep::new, ReflectorStep::every);

    /**
     * The registered type.
     */
    public static final StepType<ReflectorStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ReflectorStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.stepTicks() % every == 0) {
            LevelHost host = context.hostAs(LevelHost.class);
            ServerLevel level = host.level();
            BlockPos pos = host.position();
            if (level.getBlockEntity(pos) instanceof PrismBlockEntity prism) {
                prism.markReflector();
                Reflectors.refresh(level, pos);
            }
        }
        return false;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.LEVEL, HostCapability.TICKING);
    }
}
