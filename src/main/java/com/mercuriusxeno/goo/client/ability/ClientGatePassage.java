package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import com.mercuriusxeno.goo.network.GateCrossedPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Carries the local player through a linked gate on the frame its eye
 * crosses the gate's face, before the frame is drawn: where it stands now,
 * where it stood last tick and where it looks, both now and last tick, are
 * turned through the pair together, so the frame drawn from the
 * interpolated camera lands at the matching point past the partner and the
 * view carries on unbroken, momentum and all. The server is told after.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
public final class ClientGatePassage {

    private ClientGatePassage() {
    }

    /**
     * Carries the local player through the gate its eye crossed this frame, if any.
     *
     * @param delta the frame's delta tracker
     */
    public static void crossBeforeFrame(DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || GatePassage.clientPairs().isEmpty()) {
            return;
        }
        double eyeHeight = player.getEyeHeight();
        Vec3 eyeBefore = new Vec3(player.xo, player.yo + eyeHeight, player.zo);
        Vec3 eyeNow = player.getEyePosition(delta.getGameTimeDeltaPartialTick(true));
        Optional<GatePassage.Pair> crossed = GatePassage.crossing(GatePassage.clientPairs(), mc.level.dimension(),
                eyeBefore, eyeNow);
        crossed.ifPresent(way -> carry(player, way, eyeHeight));
    }

    private static void carry(LocalPlayer player, GatePassage.Pair way, double eyeHeight) {
        PortalTransform through = PortalTransform.between(way.one(), way.other());
        Vec3 feet = player.position();
        Vec3 motion = player.getDeltaMovement();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        GatePassage.Landing now = GatePassage.landing(through, player.getEyePosition(), eyeHeight, motion, yaw,
                pitch);
        GatePassage.Landing before = GatePassage.landing(through, new Vec3(player.xo, player.yo + eyeHeight,
                player.zo), eyeHeight, Vec3.ZERO, player.yRotO, player.xRotO);
        float turn = Mth.wrapDegrees(now.yaw() - yaw);

        player.setPos(now.feet());
        player.xo = before.feet().x();
        player.yo = before.feet().y();
        player.zo = before.feet().z();
        player.xOld = before.feet().x();
        player.yOld = before.feet().y();
        player.zOld = before.feet().z();
        player.setDeltaMovement(now.motion());
        player.setYRot(now.yaw());
        player.setXRot(now.pitch());
        player.yRotO = now.yaw() + Mth.wrapDegrees(before.yaw() - now.yaw());
        player.xRotO = before.pitch();
        turnBodyBy(player, turn);
        player.resetFallDistance();
        player.connection.send(new ServerboundCustomPayloadPacket(new GateCrossedPayload(way.one(), feet, motion,
                yaw, pitch)));
    }

    private static void turnBodyBy(LocalPlayer player, float turn) {
        player.yHeadRot += turn;
        player.yHeadRotO += turn;
        player.yBodyRot += turn;
        player.yBodyRotO += turn;
        player.yBob += turn;
        player.yBobO += turn;
    }
}
