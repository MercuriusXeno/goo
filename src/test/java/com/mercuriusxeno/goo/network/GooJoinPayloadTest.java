package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.item.GooAbsorb;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The join message an absorb sends tracking clients: the anchor's id and
 * place, the summed volume, and each discarded goo item's place and volume
 * (decision combining-flecks-join-into-one-cuboid).
 */
class GooJoinPayloadTest {

    private static final int ANCHOR = 3;
    private static final Vec3 ANCHOR_PLACE = new Vec3(10, 64, 10);
    private static final Vec3 KEPT_PLACE = new Vec3(10.2, 64, 10);
    private static final Vec3 FIRST_PLACE = new Vec3(10.1, 64, 10.2);
    private static final Vec3 SECOND_PLACE = new Vec3(9.8, 64.1, 10);

    @Test
    void anAbsorbNamesTheAnchorAndEachDiscardedGooItemsPlaceAndVolume() {
        GooAbsorb.Candidate kept = new GooAbsorb.Candidate(2, 5, 0);
        GooAbsorb.Candidate first = new GooAbsorb.Candidate(7, 11, 0);
        GooAbsorb.Candidate second = new GooAbsorb.Candidate(9, 13, 0);
        GooAbsorb.Result result = new GooAbsorb.Result(44, 0, List.of(7, 9));

        GooJoinPayload payload = GooJoinPayload.of(ANCHOR, ANCHOR_PLACE, result, List.of(
                new GooJoinPayload.Placed(kept, KEPT_PLACE),
                new GooJoinPayload.Placed(first, FIRST_PLACE),
                new GooJoinPayload.Placed(second, SECOND_PLACE)));

        assertEquals(ANCHOR, payload.anchorId());
        assertEquals(ANCHOR_PLACE, payload.anchorPlace());
        assertEquals(44, payload.joinedVolume());
        assertEquals(List.of(new GooJoinPayload.Absorbed(FIRST_PLACE, 11), new GooJoinPayload.Absorbed(SECOND_PLACE, 13)),
                payload.absorbed(), "only the discarded goo items should join, each at its place with its volume");
    }
}
