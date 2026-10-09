package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.oculus.OculusCharge;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.BlinkResolver;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.LevelBlinkSpace;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The local player's blink as the client sees it: the face its press pins
 * and the trip it would make this frame, which the cursor stands at and the
 * HUD prices.
 * Decision blink-lands-safely-costed-by-distance.
 */
public final class BlinkAim {

    private BlinkAim() {
    }

    /**
     * The face a press pins for a blink: the first block face the look
     * crosses within the blink's range.
     *
     * @param player  the local player
     * @param ability the selected ability's synced copy
     * @return the pinned face plane, empty where the ability blinks along no look or the look crosses no face
     */
    static Optional<ChannelAim.FacePlane> pinAtPress(Player player, @Nullable ClientAbility ability) {
        OptionalDouble range = blinks(ability) ? TeleportStep.lookRange(ability.behaviors()) : OptionalDouble.empty();
        return range.isPresent()
                ? BlinkResolver.pinAt(new LevelBlinkSpace(player.level(), player), player.getEyePosition(),
                        player.getViewVector(1f), range.getAsDouble())
                : Optional.empty();
    }

    /**
     * Whether an ability blinks along the look, which pins its press by range.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @return true for a blink
     */
    static boolean blinks(@Nullable ClientAbility ability) {
        return ability != null && TeleportStep.lookRange(ability.behaviors()).isPresent();
    }

    /**
     * The face plane the live press pinned, empty while no press is live.
     *
     * @return the pinned face plane, or empty for free aim
     */
    public static Optional<ChannelAim.FacePlane> livePin() {
        return GloveUseTracker.showsArea() ? Optional.ofNullable(GloveUseTracker.pressPlane()) : Optional.empty();
    }

    /**
     * What a trip costs on this client, as the server will drain it: its
     * price, or nothing where it snaps to an oculus whose charge covers it.
     * Decision oculus-prism-becomes-a-hovering-eye.
     *
     * @param distancePrice the ability's per-block and wall prices
     * @param base          the ability's flat cost
     * @param trip          the trip, empty for an ability making none
     * @return the cost in mB
     */
    public static int tripCost(DistancePrice distancePrice, int base, Optional<BlinkLanding> trip) {
        int price = distancePrice.priceOf(base, trip);
        Minecraft mc = trip.flatMap(BlinkLanding::node).isPresent() ? Minecraft.getInstance() : null;
        return OculusCharge.costAt(mc == null ? null : mc.level, price, trip);
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
                        player.getViewVector(partialTick), livePin());
    }
}
