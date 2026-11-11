package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Keeps the glove's aim on the mob a Grab press holds: while right click
 * holds a Grab press, every mob the press outlined is remembered, and once
 * one of them is held the aim, its outline and its reticle stay on that mob
 * and outline no other until the press lets go or the mob is thrown. Before
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
        if (pinned == null || !pinned.isAlive() || !heldNow.test(pinned)) {
            pinned = outlinedThisPress.stream().filter(Entity::isAlive).filter(heldNow).findFirst().orElse(null);
        }
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
}
