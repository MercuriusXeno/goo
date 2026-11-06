package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.program.PortalStep;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ability.ClientPortals;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.network.PortalChoosePayload;
import com.mercuriusxeno.goo.network.PortalLinkPayload;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * A right click with Portal in hand on the player's own portals: on one of
 * them it enters choosing its partner instead of throwing; while choosing,
 * on a distant one, seen through walls, it links the two, and on none it
 * ends choosing.
 * portals-link-by-looking-through-portal-vision
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

    /**
     * Sends what a press with Portal in hand does among the player's portals.
     *
     * @param player the local player
     * @return true when the press chose or linked, so nothing is thrown
     */
    public static boolean press(Player player) {
        Optional<Portal> choosing = ClientPortals.choosing();
        if (choosing.isPresent()) {
            Optional<Portal> picked = ClientPortals.pickedOnLook(ClientPortals.portals(), choosing.get(),
                    player.getEyePosition(), player.getLookAngle());
            GloveThrowSender.sendPayload(picked.<CustomPacketPayload>map(PortalLinkPayload::new)
                    .orElseGet(() -> new PortalChoosePayload(Optional.empty())));
            return true;
        }
        Optional<Portal> aimed = aimedPortal(player);
        aimed.ifPresent(portal -> GloveThrowSender.sendPayload(new PortalChoosePayload(Optional.of(portal))));
        return aimed.isPresent();
    }

    private static Optional<Portal> aimedPortal(Player player) {
        if (!(AimTracker.currentTarget() instanceof TargetResult.BlockTarget block)) {
            return Optional.empty();
        }
        Portal aimed = new Portal(GlobalPos.of(player.level().dimension(), block.pos()), block.face());
        return ClientPortals.portals().contains(aimed) ? Optional.of(aimed) : Optional.empty();
    }
}
