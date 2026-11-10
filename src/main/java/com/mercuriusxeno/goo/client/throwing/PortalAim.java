package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.PortalStep;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.client.ability.ClientPortals;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.PortalChoosePayload;
import com.mercuriusxeno.goo.network.PortalLinkPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * A right click with Portal in the glove among the player's own portals:
 * sneaking on one starts choosing its partner, and sneaking on another while
 * choosing links the two; while choosing, a right click anywhere else, or on
 * the chosen portal again, cancels, the portal keeping any link it held;
 * otherwise a right click places a portal.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalAim {

    /** The sneak held through a link, which stops counting until let go. */
    private static final SneakLatch LATCH = new SneakLatch();

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

    /** What a right click with Portal in the glove does. */
    public sealed interface Press permits Choose, Link, Cancel, Place {
    }

    /**
     * Starts choosing a portal's partner.
     *
     * @param portal the portal chosen from
     */
    public record Choose(Portal portal) implements Press {
    }

    /**
     * Links the portal being chosen from to another.
     *
     * @param target the partner
     */
    public record Link(Portal target) implements Press {
    }

    /** Cancels choosing; a portal linked already keeps its link. */
    public record Cancel() implements Press {
    }

    /** Throws Portal, placing a portal. */
    public record Place() implements Press {
    }

    /**
     * What a right click with Portal in the glove does.
     *
     * @param choosing the portal being chosen from, empty while none is
     * @param sneaking whether the player sneaks
     * @param faced    the player's portal its look faces, at any distance, empty for none
     * @return the press
     */
    public static Press pressed(Optional<Portal> choosing, boolean sneaking, Optional<Portal> faced) {
        if (choosing.isPresent()) {
            boolean another = sneaking && faced.isPresent() && !faced.get().equals(choosing.get());
            return another ? new Link(faced.get()) : new Cancel();
        }
        return sneaking && faced.isPresent() ? new Choose(faced.get()) : new Place();
    }

    /**
     * Sends what a press with Portal in hand does among the player's portals.
     *
     * @param player the local player
     * @return true when the press chose, linked or cancelled, so nothing is thrown
     */
    public static boolean press(Player player) {
        Press press = pressed(ClientPortals.choosing(), sneaks(player), faced(player));
        CustomPacketPayload payload = switch (press) {
            case Choose choose -> new PortalChoosePayload(Optional.of(choose.portal()));
            case Link link -> {
                LATCH.linked();
                yield new PortalLinkPayload(link.target());
            }
            case Cancel cancel -> new PortalChoosePayload(Optional.empty());
            case Place place -> null;
        };
        if (payload == null) {
            return false;
        }
        GloveThrowSender.sendPayload(payload);
        return true;
    }

    /**
     * The player's portal its look faces, at any distance.
     *
     * @param player the local player
     * @return the portal, empty for none
     */
    public static Optional<Portal> faced(Player player) {
        return ClientPortals.facedOnLook(ClientPortals.portals(), player.level().dimension(),
                player.getEyePosition(), player.getLookAngle());
    }

    /**
     * Whether the player holds Portal in its glove and sneaks, when its
     * portals light up to show what a right click works on.
     *
     * @param player the local player
     * @return true while it does
     */
    public static boolean working(Player player) {
        return sneaks(player) && selected(GloveAim.selectedAbilityId(player));
    }

    /**
     * Whether the player's sneak counts for working on portals: not while the
     * sneak held through a link stays held.
     *
     * @param player the local player
     * @return true where it counts
     */
    private static boolean sneaks(Player player) {
        return LATCH.sneaking(player.isShiftKeyDown());
    }

    /**
     * Frees the sneak once the player lets go of it.
     *
     * @param event the client's tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        LATCH.update(player != null && player.isShiftKeyDown());
    }
}
