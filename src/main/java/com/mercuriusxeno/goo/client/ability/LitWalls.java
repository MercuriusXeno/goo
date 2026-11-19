package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every wall the live fronts hold lit, by block, with every live hit on it.
 * A wall draws once, at the brightest glow any live hit leaves on it, a tie
 * going to the oldest pulse. While every hit on a wall draws alike, in one
 * color on one shape, the latest hit is the brightest and whichever hit wins
 * a tie draws the same, so a frame weighs that one hit; a wall lit in two
 * colors weighs each of its hits. The pulses tell it as each hit lights and
 * as each fades, so a frame never gathers the walls from every pulse again.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
final class LitWalls {

    private final Map<BlockPos, Wall> walls = new LinkedHashMap<>();
    private long pulsesMade;

    /**
     * The order the next pulse whose hits join these walls takes, so an
     * older pulse wins a tie between two equally bright hits.
     *
     * @return the order, lower for older
     */
    long nextOrder() {
        return pulsesMade++;
    }

    /**
     * Takes a hit a front just lit.
     *
     * @param hit the hit
     */
    void lit(HeldSweepPulse.LitFace hit) {
        walls.computeIfAbsent(hit.face().pos(), pos -> new Wall()).add(hit);
    }

    /**
     * Lets go a hit whose glow ran out, and the wall once no hit is left on it.
     *
     * @param hit the hit
     */
    void faded(HeldSweepPulse.LitFace hit) {
        BlockPos pos = hit.face().pos();
        Wall wall = walls.get(pos);
        if (wall != null && wall.remove(hit)) {
            walls.remove(pos);
        }
    }

    /**
     * The walls a frame draws and how brightly: each wall once, at the
     * brightest glow a hit on it leaves now, a tie going to the oldest pulse.
     *
     * @param now the game time with its partial tick
     * @return each wall to draw with its glow and color
     */
    List<HeldSweepVisual.WallGlow> glowingAt(double now) {
        List<HeldSweepVisual.WallGlow> glowing = new ArrayList<>(walls.size());
        for (Wall wall : walls.values()) {
            glowing.add(wall.glowAt(now));
        }
        return glowing;
    }

    /**
     * The hits a frame weighs to find its walls.
     *
     * @return one for each wall whose hits all draw alike, every hit for each other wall
     */
    int weighed() {
        return walls.values().stream().mapToInt(Wall::weighed).sum();
    }

    /** Lets go every wall, as a disconnect or a change of dimension does. */
    void clear() {
        walls.clear();
    }

    /**
     * Whether two hits draw a wall alike: in one color, on one shape.
     *
     * @param one   a hit
     * @param other another hit on the same block
     * @return true when either draws the wall as the other would at the same glow
     */
    static boolean drawsAlike(HeldSweepPulse.LitFace one, HeldSweepPulse.LitFace other) {
        return one.by().tintRgb() == other.by().tintRgb() && one.face().sides().equals(other.face().sides())
                && one.face().boxes().equals(other.face().boxes());
    }

    /** One wall's live hits, the latest of them, and whether they all draw alike. */
    private static final class Wall {

        private final List<HeldSweepPulse.LitFace> hits = new ArrayList<>(1);
        private HeldSweepPulse.LitFace latest;
        private boolean alike = true;

        void add(HeldSweepPulse.LitFace hit) {
            if (!hits.isEmpty() && !drawsAlike(hits.getFirst(), hit)) {
                alike = false;
            }
            hits.add(hit);
            if (latest == null || hit.hitAt() >= latest.hitAt()) {
                latest = hit;
            }
        }

        /**
         * @param hit a hit whose glow ran out
         * @return true once the wall holds no hit
         */
        boolean remove(HeldSweepPulse.LitFace hit) {
            hits.removeIf(held -> held == hit);
            if (hits.isEmpty()) {
                return true;
            }
            if (latest == hit) {
                latest = hits.stream().max((one, other) -> Double.compare(one.hitAt(), other.hitAt())).orElseThrow();
            }
            alike = hits.stream().allMatch(held -> drawsAlike(hits.getFirst(), held));
            return false;
        }

        int weighed() {
            return alike ? 1 : hits.size();
        }

        HeldSweepVisual.WallGlow glowAt(double now) {
            HeldSweepPulse.LitFace best = alike ? latest : brightest(now);
            return new HeldSweepVisual.WallGlow(best.face(), HeldSweepVisual.glowAt(now - best.hitAt()),
                    best.by().tintRgb());
        }

        private HeldSweepPulse.LitFace brightest(double now) {
            HeldSweepPulse.LitFace best = hits.getFirst();
            float bestGlow = HeldSweepVisual.glowAt(now - best.hitAt());
            for (int index = 1; index < hits.size(); index++) {
                HeldSweepPulse.LitFace hit = hits.get(index);
                float glow = HeldSweepVisual.glowAt(now - hit.hitAt());
                if (glow > bestGlow || glow == bestGlow && hit.by().order() < best.by().order()) {
                    best = hit;
                    bestGlow = glow;
                }
            }
            return best;
        }
    }
}
