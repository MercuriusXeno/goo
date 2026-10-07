package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The mobs an unmake is melting on this client, as {@link MeltingBlocks}
 * holds blocks: a standing mob's body liquefies as its share rises, and once
 * it is gone its goo collapses where it last stood for
 * {@link MeltingBlocks#COLLAPSE_TICKS} and is forgotten.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class MeltingMobs {

    /** The store the client's unmake handler and the melt renderer share. */
    public static final MeltingMobs CLIENT = new MeltingMobs();

    private static final long STANDING = -1;

    /**
     * One mob's melt as this frame draws it.
     *
     * @param box       the mob's body, or where it last stood once gone
     * @param liquefied how much of its body has turned to goo, 0 to 1
     * @param collapse  how far its goo has collapsed once it is gone, 0 while it stands, to 1
     */
    public record Melt(AABB box, float liquefied, float collapse) {
    }

    private record Heard(float liquefied, long tick, long goneAt, @Nullable AABB lastBox) {
    }

    private final Map<Integer, Heard> melting = new HashMap<>();

    /**
     * Records the share of a mob melted.
     *
     * @param entityId the mob's entity id
     * @param fraction the share melted, from 0 whole to 1 gone
     * @param now      the game time the share arrived
     */
    public void record(int entityId, float fraction, long now) {
        Heard last = melting.get(entityId);
        melting.put(entityId, new Heard(fraction, now, STANDING, last == null ? null : last.lastBox()));
    }

    /**
     * The melts to draw now: each standing mob still being worked, and each
     * gone mob's collapsing goo where it last stood.
     *
     * @param now   the game time including the partial tick
     * @param boxOf a mob's body now, or null once it has gone from the level
     * @return the melts
     */
    public List<Melt> melts(float now, IntFunction<@Nullable AABB> boxOf) {
        List<Melt> melts = new ArrayList<>();
        Iterator<Map.Entry<Integer, Heard>> entries = melting.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, Heard> entry = entries.next();
            Heard heard = track(entry.getValue(), boxOf.apply(entry.getKey()), now);
            entry.setValue(heard);
            Melt melt = meltOf(heard, now);
            if (melt == null) {
                entries.remove();
            } else {
                melts.add(melt);
            }
        }
        return melts;
    }

    /**
     * Follows a standing mob's body, or marks it gone once it has left.
     *
     * @param heard what was last heard of the mob
     * @param box   its body now, or null once gone
     * @param now   the game time including the partial tick
     * @return what is known of it now
     */
    private static Heard track(Heard heard, @Nullable AABB box, float now) {
        if (heard.goneAt() != STANDING) {
            return heard;
        }
        if (box != null) {
            return new Heard(heard.liquefied(), heard.tick(), STANDING, box);
        }
        return new Heard(1f, heard.tick(), (long) now, heard.lastBox());
    }

    /**
     * One mob's melt now.
     *
     * @param heard what is known of the mob
     * @param now   the game time including the partial tick
     * @return the melt, or null once it has collapsed, gone quiet, or never been seen
     */
    private static @Nullable Melt meltOf(Heard heard, float now) {
        if (heard.lastBox() == null) {
            return null;
        }
        if (heard.goneAt() == STANDING) {
            return now - heard.tick() > MeltingBlocks.STALE_TICKS ? null
                    : new Melt(heard.lastBox(), heard.liquefied(), 0f);
        }
        float collapse = (now - heard.goneAt()) / MeltingBlocks.COLLAPSE_TICKS;
        return collapse >= 1f ? null : new Melt(heard.lastBox(), 1f, Math.max(collapse, 0f));
    }
}
