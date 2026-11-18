package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Nitro cells a blast has lit, each going off on its own tick, a beat
 * after the blast that lit it, so a field of Nitro detonates outward. Each
 * server holds one, so its fuses end with the server (decision
 * type-package-and-per-server-holders).
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 */
public final class NitroFuses {

    private final List<Fuse> lit = new ArrayList<>();

    /**
     * Lights a fuse to go off on its tick.
     *
     * @param fuse the lit fuse
     */
    public void light(Fuse fuse) {
        lit.add(fuse);
    }

    /**
     * @return true while a lit fuse waits to go off
     */
    public boolean hasPending() {
        return !lit.isEmpty();
    }

    /**
     * Sets off every fuse whose tick has come; a blast lighting more fuses
     * lights them for a later tick.
     *
     * @param currentTick the current server tick count
     */
    public void burnDown(int currentTick) {
        List<Fuse> due = lit.stream().filter(fuse -> currentTick >= fuse.goesOffAt()).toList();
        lit.removeAll(due);
        due.forEach(fuse -> fuse.cell().detonate(fuse.level(), fuse.pos(), fuse.power(), fuse.placer()));
    }

    /**
     * Drops every lit fuse, as a server stop does.
     */
    public void clear() {
        lit.clear();
    }

    /**
     * A Nitro cell a blast lit, already taken from the world.
     *
     * @param goesOffAt the server tick it goes off on
     * @param level     the level it stood in
     * @param pos       where it stood
     * @param power     the power its layers blast with
     * @param cell      the Nitro block it was
     * @param placer    the player who placed it, whom its blast credits, or null
     */
    public record Fuse(int goesOffAt, ServerLevel level, BlockPos pos, float power, NitroCellBlock cell,
                       @Nullable ServerPlayer placer) {
    }
}
