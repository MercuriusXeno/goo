package com.mercuriusxeno.goo.ability;

import org.jspecify.annotations.Nullable;

/**
 * The key a thrown blob and a standing chain marker share when the marker
 * is its target, fuse stall and stack position: the ability id. A throw of
 * another ability meets the marker as a solid block (decision
 * diagnose-then-fix-stack-key-match).
 */
public final class StackKey {

    private StackKey() {
    }

    /**
     * Whether a throw naming an ability keys onto a marker running one.
     *
     * @param markerAbilityId the standing marker's ability id
     * @param thrownAbilityId the thrown blob's ability id, or null when the glove names none
     * @return true when both name the same ability
     */
    public static boolean matches(@Nullable String markerAbilityId, @Nullable String thrownAbilityId) {
        return thrownAbilityId != null && !thrownAbilityId.isEmpty() && thrownAbilityId.equals(markerAbilityId);
    }
}
