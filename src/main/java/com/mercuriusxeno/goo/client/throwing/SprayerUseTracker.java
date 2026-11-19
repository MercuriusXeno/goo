package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.network.SprayerTickPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Sends a tick of the sprayer's hold each client tick its use stays down with a canister fitted, naming the
 * sprayer's nozzle end in the caster's view as where the drips leave; the server reads the goo from the canister.
 * decision spray-item-sprays-residuals-beside-the-glove
 * intake's assumption, not an operator ruling: the drips leave the sprayer's nozzle end
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SprayerUseTracker {

    /**
     * How far out along the look the nozzle end stands from the eye, in blocks.
     * operator report 2026-11-18: the spray came from the sprayer's top edge, not its front
     */
    private static final double NOZZLE_AHEAD = 0.75;
    /** How far to the side of the hand holding it the nozzle end stands, in blocks. */
    private static final double NOZZLE_ASIDE = 0.4;
    /** How far under the eye the nozzle end stands, in blocks. */
    private static final double NOZZLE_BELOW = 0.22;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private SprayerUseTracker() {
    }

    /**
     * Sends the hold's tick while the local player holds the sprayer's use down.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.getConnection() != null && spraysALoadedSprayer(player)) {
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new SprayerTickPayload(nozzleEnd(player, 1f))));
        }
    }

    /**
     * Whether the player holds a sprayer's use down with a canister fitted.
     *
     * @param player the local player
     * @return true while the hold sprays
     */
    private static boolean spraysALoadedSprayer(LocalPlayer player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof SprayerItem
                && !SprayerItem.fittedCanister(player.getUseItem()).isEmpty();
    }

    /**
     * The sprayer's nozzle end in the caster's view: out along the look, under the eye, to the side of the hand
     * holding it.
     *
     * @param player      the spraying player
     * @param partialTick the frame's partial tick
     * @return the nozzle end, world space
     */
    public static Vec3 nozzleEnd(Player player, float partialTick) {
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = look.cross(UP);
        right = right.lengthSqr() == 0 ? Vec3.ZERO : right.normalize();
        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm()
                : player.getMainArm().getOpposite();
        double side = arm == HumanoidArm.RIGHT ? NOZZLE_ASIDE : -NOZZLE_ASIDE;
        return player.getEyePosition(partialTick).add(look.scale(NOZZLE_AHEAD)).add(right.scale(side))
                .subtract(0, NOZZLE_BELOW, 0);
    }
}
