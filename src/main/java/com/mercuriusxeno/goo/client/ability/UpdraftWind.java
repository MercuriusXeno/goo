package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Updraft's look: while an updraft's blob stands, frost's wind lines without
 * snowflakes rise from it up through its column and curl out near the top,
 * the column sized from the ability's updraft step. The blob itself stays
 * drawn at the column's foot by the marker's own visual.
 * updraft-blob-stands-a-column-of-wind
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
     * A column's size.
     *
     * @param radius the column's half width in blocks
     * @param height the column's height in blocks
     */
    record Column(double radius, double height) {
    }

    /**
     * Notes a standing marker as the renderer reads it: an updraft's blows
     * this tick's line up its column.
     *
     * @param pos       the marker's block
     * @param abilityId the marker's ability
     * @param gameTime  the game time
     */
    public static void see(BlockPos pos, String abilityId, long gameTime) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        Optional<Column> column = ability == null ? Optional.empty() : columnOf(ability.behaviors());
        if (column.isEmpty() || !blowsNow(pos.immutable(), gameTime)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            WindLines.CLIENT.rise(mc.level.getRandom(), Vec3.atBottomCenterOf(pos), column.get().radius(),
                    column.get().height(), gameTime);
        }
    }

    /**
     * The column an ability's lingering body stands, read from its updraft step.
     *
     * @param behaviors the ability's program
     * @return the column, or empty for an ability with no updraft
     */
    static Optional<Column> columnOf(List<Step> behaviors) {
        return LingerStep.bodyOf(behaviors).flatMap(body -> body.stream().flatMap(UpdraftWind::withDescendants)
                .filter(UpdraftStep.class::isInstance).map(UpdraftStep.class::cast).findFirst())
                .map(updraft -> new Column(updraft.radius().evaluate(NONE), updraft.height().evaluate(NONE)));
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
