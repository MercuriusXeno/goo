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

    private static final double HALF = 0.5;

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
        float partial = delta.getGameTimeDeltaPartialTick(true);
        Vec3 feetBefore = new Vec3(player.xo, player.yo, player.zo);
        Vec3 feetNow = player.getPosition(partial);
        Vec3 eye = new Vec3(0, player.getEyeHeight(), 0);
        Vec3 middle = new Vec3(0, player.getBbHeight() * HALF, 0);
        Optional<GatePassage.Pair> crossed = GatePassage.crossing(GatePassage.clientPairs(), mc.level.dimension(),
                feetBefore.add(eye), feetNow.add(eye))
                .or(() -> GatePassage.crossing(GatePassage.clientPairs(), mc.level.dimension(),
                        feetBefore.add(middle), feetNow.add(middle)));
        crossed.ifPresent(way -> carry(player, way));
    }

    /**
     * Carries the player through, by the point it crossed by, as the server
     * checks it: its place now and its place last tick both moved by the
     * same carry, so the interpolated frame lands where it should.
     *
     * @param player the local player
     * @param way    the gate crossed, then its partner
     */
    private static void carry(LocalPlayer player, GatePassage.Pair way) {
        Vec3 feet = player.position();
        Optional<GatePassage.Held> held = GatePassage.heldBehind(way.one(), feet, player.getEyeHeight(),
                player.getBbHeight(), player.getDeltaMovement());
        if (held.isEmpty()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        GatePassage.Landing now = GatePassage.landingThrough(way.one(), way.other(), held.get(),
                player.getBbHeight(), motion, yaw, pitch);
        GatePassage.Landing before = landedBefore(player, way, held.get(), now);
        placeAt(player, now, before);
        player.connection.send(new ServerboundCustomPayloadPacket(new GateCrossedPayload(way.one(), feet, motion,
                yaw, pitch)));
    }

    /**
     * Where the player's place and look last tick land, carried by the same
     * point and set clear by the same amount as its place now.
     *
     * @param player the local player
     * @param way    the gate crossed, then its partner
     * @param held   the point it crossed by now
     * @param now    where it lands now
     * @return where its last tick lands
     */
    private static GatePassage.Landing landedBefore(LocalPlayer player, GatePassage.Pair way, GatePassage.Held held,
            GatePassage.Landing now) {
        PortalTransform through = PortalTransform.between(way.one(), way.other());
        Vec3 heldBefore = held.point().subtract(player.position()).add(player.xo, player.yo, player.zo);
        GatePassage.Landing before = GatePassage.landing(through, heldBefore, held.drop(), Vec3.ZERO,
                player.yRotO, player.xRotO);
        Vec3 setClear = now.feet().subtract(GatePassage.landing(through, held.point(), held.drop(), Vec3.ZERO,
                player.getYRot(), player.getXRot()).feet());
        return new GatePassage.Landing(before.feet().add(setClear), before.motion(), before.yaw(), before.pitch());
    }

    private static void placeAt(LocalPlayer player, GatePassage.Landing now, GatePassage.Landing before) {
        Vec3 feetBefore = before.feet();
        float turn = Mth.wrapDegrees(now.yaw() - player.getYRot());
        player.setPos(now.feet());
        player.xo = feetBefore.x();
        player.yo = feetBefore.y();
        player.zo = feetBefore.z();
        player.xOld = feetBefore.x();
        player.yOld = feetBefore.y();
        player.zOld = feetBefore.z();
        player.setDeltaMovement(now.motion());
        player.setYRot(now.yaw());
        player.setXRot(now.pitch());
        player.yRotO = now.yaw() + Mth.wrapDegrees(before.yaw() - now.yaw());
        player.xRotO = before.pitch();
        turnBodyBy(player, turn);
        player.resetFallDistance();
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
