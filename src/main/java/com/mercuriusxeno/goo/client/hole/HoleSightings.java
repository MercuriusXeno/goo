package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The portable holes a client draws and how far open each stands: a hole
 * grows open from nothing when first seen, and once the server stops listing
 * it, closed by its timer, a punch or a second cast alike, it stays drawn
 * while it shrinks shut, then is drawn no more. Both spans run from what the
 * client saw, not from a lifetime a swallow or a punch can change.
 * decision hole-animates-open-and-closed
 */
final class HoleSightings {

    /** Ticks a mouth takes to grow open, no fewer than the tunnel's. */
    static final float OPENING_TICKS = 16F;
    /** Ticks a closed mouth takes to shrink shut. */
    static final float CLOSING_TICKS = 20F;

    private List<PortableHolesSyncPayload.Mouth> standing = List.of();
    private final Map<Spot, Long> firstSeen = new HashMap<>();
    private final Map<Spot, Closing> closing = new LinkedHashMap<>();

    /**
     * Where a mouth stands and when it opened, which a resync after a swallow
     * keeps though the swallow moved its closing, so the mouth does not grow
     * open again.
     *
     * @param footprint the blocks whose faces it opens across
     * @param face      the face it opens on
     * @param openedAt  the server's game time it opened
     */
    private record Spot(List<BlockPos> footprint, Direction face, long openedAt) {

        static Spot of(PortableHolesSyncPayload.Mouth mouth) {
            return new Spot(mouth.footprint(), mouth.face(), mouth.openedAt());
        }
    }

    /**
     * A mouth the server stopped listing, drawn while it shrinks shut.
     *
     * @param mouth the mouth as last listed
     * @param since the game time the client saw it close
     * @param from  how far open it stood as it closed
     */
    private record Closing(PortableHolesSyncPayload.Mouth mouth, long since, float from) {
    }

    /**
     * Takes the holes the server lists now: a new one starts growing open,
     * and one no longer listed starts shrinking shut.
     *
     * @param listed   the holes the server listed
     * @param gameTime the client's game time
     */
    void seen(List<PortableHolesSyncPayload.Mouth> listed, long gameTime) {
        Set<Spot> listedSpots = listed.stream().map(Spot::of).collect(Collectors.toSet());
        for (PortableHolesSyncPayload.Mouth gone : standing) {
            if (!listedSpots.contains(Spot.of(gone))) {
                closing.put(Spot.of(gone), new Closing(gone, gameTime, openShare(gone, gameTime)));
            }
        }
        closing.keySet().removeAll(listedSpots);
        standing = List.copyOf(listed);
        firstSeen.keySet().retainAll(listedSpots);
        listedSpots.forEach(spot -> firstSeen.putIfAbsent(spot, gameTime));
    }

    /**
     * Forgets every hole, as the client leaves the level they stand in.
     */
    void forget() {
        standing = List.of();
        firstSeen.clear();
        closing.clear();
    }

    /**
     * The mouths to draw: every listed one and every closed one still
     * shrinking shut, letting go of those shut.
     *
     * @param time the client's game time, partial tick included
     * @return the mouths
     */
    List<PortableHolesSyncPayload.Mouth> drawn(float time) {
        closing.values().removeIf(shut -> time - shut.since() >= CLOSING_TICKS);
        List<PortableHolesSyncPayload.Mouth> drawn = new ArrayList<>(standing);
        closing.values().forEach(shut -> drawn.add(shut.mouth()));
        return drawn;
    }

    /**
     * How far open a mouth stands.
     *
     * @param mouth a drawn mouth
     * @param time  the client's game time, partial tick included
     * @return the share of its full size, in [0, 1]
     */
    float openShare(PortableHolesSyncPayload.Mouth mouth, float time) {
        Spot spot = Spot.of(mouth);
        Closing shut = closing.get(spot);
        if (shut != null) {
            return shut.from() * closingShare(time - shut.since());
        }
        Long since = firstSeen.get(spot);
        return since == null ? 0F : openingShare(time - since);
    }

    /**
     * The share a mouth stands open at an age since first seen: rising from
     * nothing, easing out, and full from the opening span's end.
     *
     * @param age ticks since first seen
     * @return the share, in [0, 1]
     */
    static float openingShare(float age) {
        float done = Mth.clamp(age / OPENING_TICKS, 0F, 1F);
        float left = 1F - done;
        return 1F - left * left * left;
    }

    /**
     * The share of its closing size a mouth keeps a while after it closed:
     * full at first, easing down to nothing at the closing span's end.
     *
     * @param since ticks since it closed
     * @return the share, in [0, 1]
     */
    static float closingShare(float since) {
        return 1F - (float) Mth.smoothstep(Mth.clamp(since / CLOSING_TICKS, 0F, 1F));
    }
}
