package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.LiftStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Lift's look: a lift prism's wind spirals out around it from its base
 * (LiftWind), blowing one line a tick however many frames draw the prism.
 * lift-prism-levitates-the-block-above
 */
public final class UpdraftWind {

    /** Every variable an updraft's sizes might read is zero on the client. */
    private static final Variables NONE = name -> OptionalDouble.of(0);
    /** The game time each standing column last blew a line, so a tick drawn over many frames blows once. */
    private static final Map<BlockPos, Long> BLOWN_AT = new ConcurrentHashMap<>();
    /** Ticks a column goes unseen before its record is dropped. */
    private static final long FORGET_AFTER_TICKS = 40;

    private UpdraftWind() {
    }

    /**
     * Notes a lift prism as the renderer reads it: it blows this tick's line
     * up the shaft above it, as tall as the shaft stands.
     * lift-prism-levitates-the-block-above
     *
     * @param prism    the prism's block
     * @param combo    the prism's combo, its ability's id
     * @param level    the world the shaft stands in
     * @param gameTime the game time
     */
    public static void seeLift(BlockPos prism, String combo, BlockGetter level, long gameTime) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(combo);
        Optional<LiftStep> lift = ability == null ? Optional.empty() : liftOf(ability.comboSteps());
        if (lift.isEmpty() || !blowsNow(prism.immutable(), gameTime)) {
            return;
        }
        int height = LiftStep.shaftHeightAbove(level, prism, lift.get().cap().evaluateInt(NONE));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            LiftWind.blow(prism, height, mc.level.getRandom(), gameTime);
        }
    }

    /**
     * The lift step a combo's program runs.
     *
     * @param behaviors the combo's program
     * @return the lift step, or empty for any other combo
     */
    static Optional<LiftStep> liftOf(List<Step> behaviors) {
        return behaviors.stream().flatMap(UpdraftWind::withDescendants).filter(LiftStep.class::isInstance)
                .map(LiftStep.class::cast).findFirst();
    }

    /**
     * Whether a column blows its line on this tick: the first time it is
     * seen on a tick, never again on the same tick.
     *
     * @param pos      the marker's block
     * @param gameTime the game time
     * @return true the first time this tick
     */
    static boolean blowsNow(BlockPos pos, long gameTime) {
        @Nullable Long last = BLOWN_AT.put(pos, gameTime);
        if (last == null || last != gameTime) {
            BLOWN_AT.values().removeIf(seen -> gameTime - seen > FORGET_AFTER_TICKS);
            return true;
        }
        return false;
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(UpdraftWind::withDescendants));
    }

    /** Drops every column, as a disconnect does. */
    public static void clear() {
        BLOWN_AT.clear();
    }
}
