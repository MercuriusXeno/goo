package com.mercuriusxeno.goo.client.model;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Goo items joining by surface tension, as a pure function of the ease's
 * share: each absorbed cuboid stretches into a bridge whose leading face
 * reaches the anchor first and whose trailing face follows, thinning as it
 * feeds the anchor, while the anchor grows from its own volume's size to the
 * summed volume's size.
 * decision combining-flecks-join-into-one-cuboid
 *
 * @param anchor       the anchor cuboid's center
 * @param fromVolume   the anchor's volume before the join, in mB
 * @param joinedVolume the anchor's volume after the join, in mB
 * @param absorbed     each absorbed cuboid
 */
record GooJoin(Vec3 anchor, double fromVolume, double joinedVolume, List<Absorbed> absorbed) {

    /**
     * The share of the ease by which a bridge's leading face reaches the
     * anchor; its trailing face takes the whole ease.
     */
    private static final float LEADING_ARRIVES_AT = 0.5f;

    /**
     * An absorbed cuboid as it stood when absorbed.
     *
     * @param place  its center
     * @param volume the goo it held, in mB
     */
    record Absorbed(Vec3 place, double volume) {
    }

    /**
     * An absorbed cuboid on its way into the anchor: the axis-aligned hull of
     * a cube about its leading center and one about its trailing center.
     *
     * @param leading  the center of the cube nearest the anchor
     * @param trailing the center of the cube farthest from it
     * @param size     each cube's size against a thrown blob's
     */
    record Bridge(Vec3 leading, Vec3 trailing, float size) {
    }

    /**
     * What a join draws at a share.
     *
     * @param anchorSize the anchor's size against a thrown blob's
     * @param bridges    each absorbed cuboid still flowing in
     */
    record Drawn(float anchorSize, List<Bridge> bridges) {
    }

    /**
     * What the join draws at a share of its ease.
     *
     * @param share the ease's share, 0 at the absorb to 1 once joined
     * @return the anchor's size and each bridge
     */
    Drawn drawnAt(float share) {
        float clamped = Math.clamp(share, 0f, 1f);
        float fromSize = GooCuboidSize.sizeOf(fromVolume);
        float anchorSize = fromSize + (GooCuboidSize.sizeOf(joinedVolume) - fromSize) * clamped;
        if (clamped >= 1f) {
            return new Drawn(anchorSize, List.of());
        }
        float leadingShare = Math.min(1f, clamped / LEADING_ARRIVES_AT);
        List<Bridge> bridges = new ArrayList<>(absorbed.size());
        for (Absorbed each : absorbed) {
            bridges.add(new Bridge(each.place().lerp(anchor, leadingShare), each.place().lerp(anchor, clamped),
                    GooCuboidSize.sizeOf(each.volume()) * (1f - clamped)));
        }
        return new Drawn(anchorSize, bridges);
    }
}
