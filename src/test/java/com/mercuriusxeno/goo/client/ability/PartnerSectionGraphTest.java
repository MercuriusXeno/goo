package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The streamed far side draws through its own section graph, walked out from
 * the partner's mouth the way the game walks its own sections: a section
 * sealed off behind solid stone is not drawn, nor one outside the view's
 * frustum (decision linked-partner-chunks-stream-to-any-viewer).
 */
class PartnerSectionGraphTest {

    private static final SectionPos MOUTH = SectionPos.of(0, 4, 0);
    private static final SectionPos NEXT = MOUTH.offset(1, 0, 0);
    private static final SectionPos BEYOND = MOUTH.offset(2, 0, 0);

    private static VisibilitySet open() {
        VisibilitySet open = new VisibilitySet();
        open.setAll(true);
        return open;
    }

    private static Map<SectionPos, VisibilitySet> row(VisibilitySet middle) {
        Map<SectionPos, VisibilitySet> held = new HashMap<>();
        held.put(MOUTH, open());
        held.put(NEXT, middle);
        held.put(BEYOND, open());
        return held;
    }

    @Test
    void openSectionsAreAllSeen() {
        assertEquals(Set.of(MOUTH, NEXT, BEYOND), PartnerSectionGraph.visible(MOUTH, row(open()), section -> true),
                "Every section open air reaches from the mouth should be seen");
    }

    @Test
    void aSectionSealedBehindSolidStoneIsNotSeen() {
        assertEquals(Set.of(MOUTH, NEXT), PartnerSectionGraph.visible(MOUTH, row(new VisibilitySet()),
                section -> true), "A section reached only through a section of solid stone should not be seen, "
                + "while the stone section's face is");
    }

    @Test
    void aSectionOutsideTheFrustumIsNotSeenNorWalkedThrough() {
        assertEquals(Set.of(MOUTH), PartnerSectionGraph.visible(MOUTH, row(open()), section -> !section.equals(NEXT)),
                "A section outside the view's frustum should not be seen, nor a section reached only through it");
    }

    @Test
    void aSectionTheStreamDoesNotHoldIsNotSeen() {
        Map<SectionPos, VisibilitySet> held = row(open());
        held.remove(NEXT);
        assertEquals(Set.of(MOUTH), PartnerSectionGraph.visible(MOUTH, held, section -> true),
                "The walk should stop at a section the stream does not hold");
    }
}
