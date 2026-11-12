package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The streamed far side's own section graph, walked as the game walks its
 * own sections: out from the section at the partner's mouth, where a view
 * through the portal comes out, through each face of a section that open
 * space joins to the face the walk came in by, never back out the face it
 * came in by, and only into sections the stream holds within the view's
 * frustum. A section sealed behind solid stone, or out of sight, is never
 * drawn.
 * linked-partner-chunks-stream-to-any-viewer
 */
final class PartnerSectionGraph {

    private PartnerSectionGraph() {
    }

    /** A section the walk reached, and the face it came in by, none for the mouth. */
    private record Step(SectionPos section, @Nullable Direction inBy) {
    }

    /**
     * The streamed sections a view through a portal sees.
     *
     * @param start  the section at the partner's mouth, where the view comes out
     * @param held   each streamed section, with which of its faces see through to which
     * @param inView whether a section stands within the view's camera frustum
     * @return the sections the view sees
     */
    static Set<SectionPos> visible(SectionPos start, Map<SectionPos, VisibilitySet> held,
            Predicate<SectionPos> inView) {
        Map<SectionPos, Set<Direction>> reached = new HashMap<>();
        if (!held.containsKey(start)) {
            return reached.keySet();
        }
        reached.put(start, EnumSet.noneOf(Direction.class));
        Deque<Step> walk = new ArrayDeque<>();
        walk.add(new Step(start, null));
        while (!walk.isEmpty()) {
            Step step = walk.poll();
            for (Direction out : Direction.values()) {
                if (seesOut(step, held.get(step.section()), out)) {
                    SectionPos next = step.section().offset(out.getStepX(), out.getStepY(), out.getStepZ());
                    Direction inBy = out.getOpposite();
                    if (held.containsKey(next) && inView.test(next)
                            && reached.computeIfAbsent(next, any -> EnumSet.noneOf(Direction.class)).add(inBy)) {
                        walk.add(new Step(next, inBy));
                    }
                }
            }
        }
        return reached.keySet();
    }

    /**
     * Whether the walk carries on out of a section's face: anywhere out of
     * the mouth, else out of any face but the one it came in by that open
     * space joins to it.
     *
     * @param step       the section reached and the face it came in by
     * @param visibility which of the section's faces see through to which
     * @param out        the face the walk would carry on out of
     * @return true where the walk carries on out of that face
     */
    private static boolean seesOut(Step step, VisibilitySet visibility, Direction out) {
        Direction inBy = step.inBy();
        return inBy == null || out != inBy && visibility.visibilityBetween(inBy, out);
    }
}
