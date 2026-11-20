package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Keeps the glove's aim on the mob a Grab press holds: while right click
 * holds a Grab press, every mob the press outlined is remembered, and once
 * one of them is held the aim and its outline stay on that mob wherever the
 * crosshair turns, and outline no other until the press lets go or the mob is thrown. Before
 * the hold lands and after the press ends, the frame's own resolve names
 * the target, so the outlined mob is still the one right click acts on.
 * decision outlined-target-is-the-one-pick
 */
final class HeldPick {

    private final Set<Entity> outlinedThisPress = new LinkedHashSet<>();
    private @Nullable Entity pinned;

    /**
     * One frame's aim.
     *
     * @param grabPressed whether right click holds a live press with Grab selected
     * @param heldNow     whether an entity is held by a Grab now, as its ailment shows
     * @param fresh       the frame's own resolve
     * @return the held mob as the target while this press holds one, else the frame's own resolve
     */
    AimState.Resolution resolve(boolean grabPressed, Predicate<Entity> heldNow, Supplier<AimState.Resolution> fresh) {
        if (!grabPressed) {
            outlinedThisPress.clear();
            pinned = null;
            return fresh.get();
        }
        pinned = stillPinned(heldNow) ? pinned : heldOfThisPress(heldNow);
        if (pinned != null) {
            return new AimState.Resolution(TargetResult.entity(pinned, pinned.getBoundingBox().getCenter()),
                    new AimAssistResolver.AimHit.EntityHit(pinned));
        }
        AimState.Resolution frame = fresh.get();
        if (frame.target() instanceof TargetResult.EntityTarget outlined) {
            outlinedThisPress.add(outlined.entity());
        }
        return frame;
    }

    private boolean stillPinned(Predicate<Entity> heldNow) {
        return pinned != null && pinned.isAlive() && heldNow.test(pinned);
    }

    /**
     * The first mob this press outlined that a Grab holds now; a locked item
     * wears the same ailment and is passed over, so the aim stays free to
     * pick the next item.
     * decision sweeping-grab-locks-every-item-the-highlight-passes
     *
     * @param heldNow whether an entity is held by a Grab now
     * @return the held mob, or null where this press holds none
     */
    private @Nullable Entity heldOfThisPress(Predicate<Entity> heldNow) {
        return outlinedThisPress.stream().filter(Entity::isAlive).filter(entity -> !(entity instanceof ItemEntity))
                .filter(heldNow).findFirst().orElse(null);
    }

    /**
     * Whether the press the last frame resolved holds a mob, the one the aim stays on.
     * grab-hides-the-reticle-once-a-mob-is-held
     *
     * @return true while this Grab press holds a mob
     */
    boolean holds() {
        return pinned != null;
    }
}
