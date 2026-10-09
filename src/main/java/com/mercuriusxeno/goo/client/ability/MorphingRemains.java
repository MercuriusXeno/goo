package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The remains of unmade blocks morphing into the goo item on this
 * client, each held from the game time its morph began until it has run
 * {@link #MORPH_TICKS}, when the server drops the item in its place.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class MorphingRemains {

    /** The store the client's unmake handler and the morph renderer share. */
    public static final MorphingRemains CLIENT = new MorphingRemains();

    /** Ticks a morph runs; must match UnmakeDrops.MORPH_TICKS, after which the server drops the item. */
    public static final int MORPH_TICKS = 10;

    /**
     * One morph as this frame draws it.
     *
     * @param at       where the remains stand on the ground and the item drops
     * @param goo      the goo the remains are
     * @param size     how big the remains started, in blocks
     * @param progress how far the morph has run, 0 to 1
     */
    public record Morph(Vec3 at, GooContents goo, float size, float progress) {
    }

    private record Began(Vec3 at, GooContents goo, float size, long tick) {
    }

    private final List<Began> morphing = new ArrayList<>();

    /**
     * Begins a morph.
     *
     * @param at   where the remains stand on the ground
     * @param goo  the goo they are
     * @param size  how big they start, in blocks
     * @param now   the game time the morph began
     */
    public void begin(Vec3 at, GooContents goo, float size, long now) {
        morphing.add(new Began(at, goo, size, now));
    }

    /**
     * The morphs still running, forgetting each that has finished.
     *
     * @param now the game time including the partial tick
     * @return the morphs
     */
    public List<Morph> morphs(float now) {
        morphing.removeIf(began -> now - began.tick() >= MORPH_TICKS);
        List<Morph> morphs = new ArrayList<>();
        for (Began began : morphing) {
            float progress = Math.max(0f, (now - began.tick()) / MORPH_TICKS);
            morphs.add(new Morph(began.at(), began.goo(), began.size(), progress));
        }
        return morphs;
    }
}
