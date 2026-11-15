package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.TargetResult;
import org.jspecify.annotations.Nullable;

/**
 * The spot a charge-sized cast roots at: the target the crosshair aimed as
 * the charge began, held for the whole hold whatever the crosshair does, so
 * the spheroid draws there and the release sends it; a cancel or the next
 * press lets the next charge root afresh.
 * decision blast-roots-to-the-spot-the-cast-began
 */
final class ChargeRoot {

    private @Nullable TargetResult rooted;

    /**
     * Roots the charge at the aimed target, when it is not rooted yet and the
     * aim rests on something.
     *
     * @param aimed the crosshair's target
     */
    void holdAt(TargetResult aimed) {
        if (rooted == null && !(aimed instanceof TargetResult.None)) {
            rooted = aimed;
        }
    }

    /**
     * Where the charge anchors this frame.
     *
     * @param current the crosshair's target this frame
     * @return the rooted spot, or the current target where the charge rooted nowhere
     */
    TargetResult anchor(TargetResult current) {
        return rooted == null ? current : rooted;
    }

    /** Lets the next charge root afresh. */
    void clear() {
        rooted = null;
    }
}
