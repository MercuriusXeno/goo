package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.ability.hole.HoleDrift;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The items a client draws easing into each hole as it swallows them: each
 * swallowed item travels for a short span from the place it stood to its
 * kind's place in the soup, growing or shrinking from a dropped item's size
 * to the soup's, and a kind the swallow stored first waits out the span
 * before the soup draws it, so the kind arrives once.
 * decision swallowed-item-transitions-into-the-hole-smoothly
 */
final class HoleSwallows {

    /** Ticks a swallowed item takes to ease into the soup, about half a second. */
    static final float TRANSIT_TICKS = 10F;
    /** The shown index of a kind the mouth does not store. */
    static final int NOT_SHOWN = -1;

    private final Map<MouthKey, List<Transit>> running = new HashMap<>();
    /** The kinds each mouth stored as last heard, which tell a swallow that stores its kind first. */
    private final Map<MouthKey, Set<Identifier>> kindsBefore = new HashMap<>();
    /** Each mouth's count of kinds easing from the count it stored before to the count it stores now. */
    private final Map<MouthKey, KindsEase> easing = new HashMap<>();

    /**
     * A mouth's count of kinds easing from one count to the next.
     *
     * @param from  the count the soup spread over as the count changed
     * @param to    the count the mouth stores now
     * @param since the client's game time the count changed
     */
    private record KindsEase(float from, int to, long since) {

        float at(float time) {
            return (float) Mth.lerp(Mth.smoothstep(Mth.clamp((time - since) / TRANSIT_TICKS, 0F, 1F)), from, to);
        }
    }

    /**
     * One swallowed item easing into its mouth.
     *
     * @param swallowed      the item as the server told of it
     * @param firstOfItsKind whether the swallow stored its kind first, so the soup holds the kind back
     */
    record Transit(PortableHolesSyncPayload.Swallowed swallowed, boolean firstOfItsKind) {
    }

    /**
     * Where a mouth stands and when it opened, which tells one hole from the
     * next opened in its place.
     *
     * @param footprint the blocks whose faces it opens across
     * @param face      the face it opens on
     * @param openedAt  the server's game time it opened
     */
    private record MouthKey(List<BlockPos> footprint, Direction face, long openedAt) {

        static MouthKey of(PortableHolesSyncPayload.Mouth mouth) {
            return new MouthKey(mouth.footprint(), mouth.face(), mouth.openedAt());
        }
    }

    /**
     * Takes the holes the server lists now, starting a transit for each item
     * swallowed within the span and not yet easing, and dropping the transits
     * of every mouth no longer listed.
     *
     * @param listed   the holes the server listed
     * @param gameTime the client's game time
     */
    void heard(List<PortableHolesSyncPayload.Mouth> listed, long gameTime) {
        Map<MouthKey, Set<Identifier>> kindsNow = new HashMap<>();
        for (PortableHolesSyncPayload.Mouth mouth : listed) {
            MouthKey key = MouthKey.of(mouth);
            // a mouth first heard of has no earlier kinds to tell a first swallow by, so none holds its kind back
            Set<Identifier> before = kindsBefore.getOrDefault(key, kindsOf(mouth));
            List<Transit> transits = running.computeIfAbsent(key, unused -> new ArrayList<>());
            transits.removeIf(transit -> gameTime - transit.swallowed().time() >= TRANSIT_TICKS);
            for (PortableHolesSyncPayload.Swallowed swallowed : mouth.swallowed()) {
                boolean easing = transits.stream().anyMatch(transit -> transit.swallowed().equals(swallowed));
                if (!easing && gameTime - swallowed.time() < TRANSIT_TICKS) {
                    transits.add(new Transit(swallowed, !before.contains(swallowed.item())));
                }
            }
            kindsNow.put(key, kindsOf(mouth));
            easeKinds(key, before.size(), mouth.stored().size(), gameTime);
        }
        running.keySet().retainAll(kindsNow.keySet());
        easing.keySet().retainAll(kindsNow.keySet());
        kindsBefore.clear();
        kindsBefore.putAll(kindsNow);
    }

    /**
     * Starts a mouth's count easing toward the count it stores now, from the
     * count its soup spreads over at this moment, where the count changed.
     *
     * @param key      the mouth
     * @param before   the count it stored as last heard
     * @param now      the count it stores now
     * @param gameTime the client's game time
     */
    private void easeKinds(MouthKey key, int before, int now, long gameTime) {
        KindsEase ease = easing.get(key);
        int heading = ease == null ? before : ease.to();
        if (heading != now) {
            easing.put(key, new KindsEase(ease == null ? before : ease.at(gameTime), now, gameTime));
        }
    }

    private static Set<Identifier> kindsOf(PortableHolesSyncPayload.Mouth mouth) {
        Set<Identifier> kinds = new HashSet<>();
        mouth.stored().forEach(stored -> kinds.add(stored.item()));
        return kinds;
    }

    /**
     * Forgets every transit, as the client leaves the level the holes stand in.
     */
    void forget() {
        running.clear();
        kindsBefore.clear();
        easing.clear();
    }

    /**
     * The transits easing into a mouth at a moment.
     *
     * @param mouth a drawn mouth
     * @param time  the client's game time, partial tick included
     * @return the transits whose span covers the moment
     */
    List<Transit> runningIn(PortableHolesSyncPayload.Mouth mouth, float time) {
        return running.getOrDefault(MouthKey.of(mouth), List.of()).stream()
                .filter(transit -> time - transit.swallowed().time() < TRANSIT_TICKS).toList();
    }

    /**
     * The indices of a mouth's stored kinds the soup draws at a moment: every
     * shown kind but one a running transit stored first.
     *
     * @param mouth a drawn mouth
     * @param time  the client's game time, partial tick included
     * @return the indices into the mouth's stored kinds, in order
     */
    List<Integer> soupDrawn(PortableHolesSyncPayload.Mouth mouth, float time) {
        Set<Identifier> arriving = new HashSet<>();
        runningIn(mouth, time).stream().filter(Transit::firstOfItsKind)
                .forEach(transit -> arriving.add(transit.swallowed().item()));
        List<Integer> drawn = new ArrayList<>();
        for (int index = 0; index < mouth.stored().size(); index++) {
            if (!arriving.contains(mouth.stored().get(index).item())) {
                drawn.add(index);
            }
        }
        return drawn;
    }

    /**
     * The count of kinds a mouth's soup spreads over at a moment: the count it
     * stores, eased over the transit's span from the count it stored as last
     * heard, so a kind stored first or taken away moves the kinds swimming to
     * their places for the new count without a jump.
     * decision hole-shows-every-kind-stored
     *
     * @param mouth a drawn mouth
     * @param time  the client's game time, partial tick included
     * @return the count, between the one last heard and the one stored now
     */
    float kindsShownAt(PortableHolesSyncPayload.Mouth mouth, float time) {
        KindsEase ease = easing.get(MouthKey.of(mouth));
        return ease == null ? mouth.stored().size() : ease.at(time);
    }

    /**
     * A kind's place among a mouth's shown kinds, which are every kind it stores.
     * decision hole-shows-every-kind-stored
     *
     * @param mouth a drawn mouth
     * @param item  the kind's id
     * @return its index, or NOT_SHOWN where the mouth stores no such kind
     */
    static int shownIndexOf(PortableHolesSyncPayload.Mouth mouth, Identifier item) {
        for (int index = 0; index < mouth.stored().size(); index++) {
            if (mouth.stored().get(index).item().equals(item)) {
                return index;
            }
        }
        return NOT_SHOWN;
    }

    /**
     * Where a transit ends at a moment: its kind's soup place at full size.
     *
     * @param index      the kind's shown index
     * @param kinds      the count of kinds the soup spreads over
     * @param time       the game time, partial tick included
     * @param halfAcross the mouth's half span across, along x
     * @param halfAlong  the mouth's half span along, along z
     * @return the transit's end
     */
    static HoleDrift.Placement endOf(int index, float kinds, float time, double halfAcross, double halfAlong) {
        return HoleDrift.placementAt(index, kinds, time, halfAcross, halfAlong, 1F);
    }

    /**
     * Where a swallowed item draws partway through its transit and how large,
     * in the mouth's own frame.
     *
     * @param from        the place it was swallowed, in the mouth's frame
     * @param droppedSize the size it drew at as a dropped item
     * @param end         where the transit ends at this moment
     * @param since       ticks since the swallow
     * @return the item's place and drawn size
     */
    static HoleDrift.Placement transitAt(Vec3 from, double droppedSize, HoleDrift.Placement end, float since) {
        // smoothstep leaves the swallowed place and arrives at the end with no sudden start or stop
        double share = Mth.smoothstep(Mth.clamp(since / TRANSIT_TICKS, 0F, 1F));
        return new HoleDrift.Placement(from.lerp(end.offset(), share), Mth.lerp(share, droppedSize, end.size()));
    }
}
