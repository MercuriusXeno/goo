package com.mercuriusxeno.goo.ability.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The oculi standing in each loaded level, kept by the oculus prisms
 * themselves as they load, take the combo and unload, so Blink finds every
 * oculus it can lock to however far off without searching the chunks in
 * between. The client's levels and the server's each keep their own.
 * Decision oculus-prism-becomes-a-hovering-eye.
 */
public final class OculusRegistry {

    private static final Map<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();

    private OculusRegistry() {
    }

    /**
     * Records an oculus standing in a level.
     *
     * @param level the level
     * @param pos   the oculus's cell
     */
    public static synchronized void add(Level level, BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, key -> new HashSet<>()).add(pos.immutable());
    }

    /**
     * Forgets an oculus that unloaded or stopped being one.
     *
     * @param level the level
     * @param pos   the cell
     */
    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> cells = BY_LEVEL.get(level);
        if (cells != null) {
            cells.remove(pos);
        }
    }

    /**
     * The oculi standing in a level.
     *
     * @param level the level
     * @return their cells, a copy
     */
    public static synchronized Set<BlockPos> in(Level level) {
        Set<BlockPos> cells = BY_LEVEL.get(level);
        return cells == null ? Collections.emptySet() : Set.copyOf(cells);
    }
}
