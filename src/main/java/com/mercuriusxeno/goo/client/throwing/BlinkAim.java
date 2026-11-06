package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * The local player's blink as the client sees it: the trip it would make
 * this frame along the look, which the cursor stands at and the HUD prices.
 * Decision blink-lands-safely-costed-by-distance.
 * Decision blink-drops-the-face-axis-pin.
 */
public final class BlinkAim {

    private BlinkAim() {
    }

    /**
     * What a trip costs on this client, as the server will drain it: its
     * price, a share of it where it snaps to an oculus.
     * Decision oculus-prism-becomes-a-hovering-eye.
     *
     * @param distancePrice the ability's per-block and wall prices
     * @param base          the ability's flat cost
     * @param trip          the trip, empty for an ability making none
     * @return the cost in mB
     */
    public static int tripCost(DistancePrice distancePrice, int base, Optional<BlinkLanding> trip) {
        return distancePrice.priceOf(base, trip);
    }

    /**
     * The trip the selected blink would make this frame.
     *
     * @param player      the local player
     * @param ability     the selected ability's synced copy, or null when none
     * @param partialTick the frame's partial tick
     * @return the landing, empty where the ability blinks along no look or lands nowhere
     */
    public static Optional<BlinkLanding> trip(Player player, @Nullable ClientAbility ability, float partialTick) {
        return ability == null ? Optional.empty()
                : TeleportStep.tripOf(ability.behaviors(), player, player.getPosition(partialTick),
                        player.getViewVector(partialTick));
    }
}
