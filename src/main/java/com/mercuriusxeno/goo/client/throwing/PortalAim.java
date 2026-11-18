package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.program.PortalStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import org.jspecify.annotations.Nullable;

/**
 * Whether the glove holds Portal, whose left click shoots the blue portal
 * and right click the orange one.
 * portal-takes-over-left-and-right-click-for-blue-and-orange
 */
public final class PortalAim {

    private PortalAim() {
    }

    /**
     * Whether the selected ability places portals.
     *
     * @param abilityId the selected ability id string
     * @return true for Portal
     */
    public static boolean selected(@Nullable String abilityId) {
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability != null && PortalStep.placesPortals(ability.behaviors());
    }
}
