package com.mercuriusxeno.goo.client.model;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The join's geometry by the ease's share: two cuboids at their own places
 * and sizes at the start, one cuboid at the anchor at the summed volume's size
 * at the end, and between them the absorbed cuboid's leading face reaching
 * the anchor before its trailing face (decision combining-flecks-join-into-one-cuboid).
 */
class GooJoinTest {

    private static final Vec3 ANCHOR = new Vec3(0, 0.1, 0);
    private static final Vec3 ABSORBED = new Vec3(0.3, 0.1, 0);
    private static final double ANCHOR_VOLUME = 1_000;
    private static final double ABSORBED_VOLUME = 100;
    private static final double WITHIN = 1e-6;
    private static final GooJoin JOIN = new GooJoin(ANCHOR, ANCHOR_VOLUME, ANCHOR_VOLUME + ABSORBED_VOLUME,
            List.of(new GooJoin.Absorbed(ABSORBED, ABSORBED_VOLUME)));

    @Test
    void atTheStartEachCuboidStandsAtItsOwnPlaceAndSize() {
        GooJoin.Drawn drawn = JOIN.drawnAt(0f);

        assertEquals(GooCuboidSize.sizeOf(ANCHOR_VOLUME), drawn.anchorSize(), WITHIN);
        assertEquals(1, drawn.bridges().size());
        GooJoin.Bridge bridge = drawn.bridges().get(0);
        assertEquals(0, bridge.leading().distanceTo(ABSORBED), WITHIN);
        assertEquals(0, bridge.trailing().distanceTo(ABSORBED), WITHIN);
        assertEquals(GooCuboidSize.sizeOf(ABSORBED_VOLUME), bridge.size(), WITHIN);
    }

    @Test
    void atTheEndOneCuboidStandsAtTheAnchorAtTheSummedVolumesSize() {
        GooJoin.Drawn drawn = JOIN.drawnAt(1f);

        assertEquals(GooCuboidSize.sizeOf(ANCHOR_VOLUME + ABSORBED_VOLUME), drawn.anchorSize(), WITHIN);
        assertTrue(drawn.bridges().isEmpty(), "the absorbed cuboid should have flowed into the anchor");
    }

    @Test
    void midwayTheLeadingFaceReachesTheAnchorBeforeTheTrailingFace() {
        GooJoin.Bridge bridge = JOIN.drawnAt(0.5f).bridges().get(0);

        assertEquals(0, bridge.leading().distanceTo(ANCHOR), WITHIN, "the leading face should lie at the anchor");
        double trailingShort = bridge.trailing().distanceTo(ANCHOR);
        assertTrue(trailingShort > WITHIN && trailingShort < ABSORBED.distanceTo(ANCHOR),
                "the trailing face should lie between its start and the anchor, at " + trailingShort);
    }
}
