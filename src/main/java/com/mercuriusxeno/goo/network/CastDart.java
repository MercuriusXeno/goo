package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import net.minecraft.world.phys.Vec3;

/**
 * The visual a spontaneous cast darts from the lifted palm blob to the place
 * it makes its effect, drawn by every watching client through the visual its
 * program's scan_visual step names; Bulb's light darts forward and puffs its
 * motes out where the crystal forms.
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 */
public final class CastDart {

    /** The ticks a dart takes from the palm blob to the place, quick enough to read as a dart. */
    static final int DART_TICKS = 3;

    private CastDart() {
    }

    /**
     * The wave a dart draws, from the cast origin to the place.
     *
     * @param castOrigin the lifted palm blob the cast leaves
     * @param place      where the cast makes its effect
     * @param visual     the visual the ability's program names
     * @return the payload every watching client draws
     */
    static ScanWavePayload wave(Vec3 castOrigin, Vec3 place, ScanVisualStep visual) {
        return new ScanWavePayload(castOrigin, place, DART_TICKS, visual.visual(), visual.trail());
    }
}
