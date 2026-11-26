package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * The spot a charge-sized cast roots at: the target the crosshair aimed as
 * the charge began, held for the whole hold whatever the crosshair does, so
 * the spheroid draws there and the release sends it, beside where the
 * caster's eye stood as it rooted; a cancel or the next press lets the next
 * charge root afresh.
 * decision blast-roots-to-the-spot-the-cast-began
 * decision what-starts-in-range-resolves-out-of-range
 */
final class ChargeRoot {

    private @Nullable TargetResult rooted;
    private @Nullable Vec3 eyeAsRooted;

    /**
     * Roots the charge at the aimed target, when it is not rooted yet and the
     * aim rests on something.
     *
     * @param aimed the crosshair's target
     * @param eye   where the caster's eye stands
     */
    void holdAt(TargetResult aimed, Vec3 eye) {
        if (rooted == null && !(aimed instanceof TargetResult.None)) {
            rooted = aimed;
            eyeAsRooted = eye;
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

    /**
     * Where the caster's eye stood as the charge rooted.
     *
     * @return the eye, empty where the charge rooted nowhere
     */
    Optional<Vec3> eyeAsRooted() {
        return Optional.ofNullable(eyeAsRooted);
    }

    /** Lets the next charge root afresh. */
    void clear() {
        rooted = null;
        eyeAsRooted = null;
    }
}
