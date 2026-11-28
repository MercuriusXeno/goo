package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.network.BoneBladePayload;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Blade's bone shotels on this client, as the server latches them: each stands at its caster's lifted blob over the
 * casting hand, leaning along the look, in Osseous's goo, drawn in the level for every caster seen there and in the
 * hand's pass for the local first-person player.
 * decision blade-forms-a-bone-shotel-in-hand
 */
public final class HeldBlades {

    /** The casters holding a shotel, by entity id. */
    private static final Set<Integer> HOLDING = new HashSet<>();

    private HeldBlades() {
    }

    /**
     * Takes a shotel the server latched or dissolved.
     *
     * @param payload the shotel's standing
     * @param context the network context
     */
    public static void onPayload(BoneBladePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> standing(payload.casterId(), payload.standing()));
    }

    static void standing(int casterId, boolean standing) {
        if (standing) {
            HOLDING.add(casterId);
        } else {
            HOLDING.remove(casterId);
        }
    }

    /**
     * @param casterId the caster's entity id
     * @return whether the caster holds a shotel on this client
     */
    public static boolean holds(int casterId) {
        return HOLDING.contains(casterId);
    }

    /** Forgets every shotel, as leaving a world does. */
    public static void clear() {
        HOLDING.clear();
    }

    /**
     * Draws each shotel standing in the level, sorted with the other translucent visuals.
     *
     * @param frame the level frame
     * @param draws the sorted draws to add to
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        if (PortalView.drawing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || HOLDING.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Player> casters = castersIn(level);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        draws.accept(SortedDraw.nearestOf(casters.stream().map(caster -> StackAnchor.cubeOf(caster, partial)), camera,
                () -> drawShotels(frame, casters, partial, camera)));
    }

    // Every caster holding a shotel that the level draws.
    private static List<Player> castersIn(ClientLevel level) {
        List<Player> casters = new ArrayList<>();
        for (Integer casterId : HOLDING) {
            if (level.getEntity(casterId) instanceof Player caster && HoveringKnives.drawsInTheLevel(caster)) {
                casters.add(caster);
            }
        }
        return casters;
    }

    private static void drawShotels(LevelFrame frame, List<Player> casters, float partial, Vec3 camera) {
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        for (Player caster : casters) {
            layShotel(frame.poseStack(), caster, partial, camera, pose -> GooFlightRenderer.emitShotel(pose.last(),
                    buffers.getBuffer(GooSubmitter.renderType()), GooTypes.OSSEOUS, headingOf(caster, partial)));
        }
        buffers.endBatch(GooSubmitter.renderType());
    }

    /**
     * Submits the local first-person player's shotel in the hand's pass.
     *
     * @param poseStack the pose stack carried onto the hand's pass
     * @param collector the hand pass's node collector
     * @param caster    the local player
     * @param partial   the frame's partial tick
     * @param camera    the camera's position
     */
    static void submitInHand(PoseStack poseStack, SubmitNodeCollector collector, Player caster, float partial,
                             Vec3 camera) {
        if (!HOLDING.contains(caster.getId())) {
            return;
        }
        Vec3 heading = headingOf(caster, partial);
        layShotel(poseStack, caster, partial, camera, pose -> collector.submitCustomGeometry(pose,
                GooSubmitter.renderType(), (at, consumer) -> GooFlightRenderer.emitShotel(at, consumer,
                        GooTypes.OSSEOUS, heading)));
    }

    // Moves the pose to the shotel's hilt, the caster's lifted blob over the hand, and draws it there.
    private static void layShotel(PoseStack poseStack, Player caster, float partial, Vec3 camera,
                                  Consumer<PoseStack> draw) {
        Vec3 at = StackAnchor.cubeOf(caster, partial).subtract(camera);
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        draw.accept(poseStack);
        poseStack.popPose();
    }

    // The way the shotel leans: from the hilt toward the caster's crosshair.
    private static Vec3 headingOf(Player caster, float partial) {
        return ThrownKnife.headingFrom(StackAnchor.cubeOf(caster, partial), ThrownKnife.aimPointOf(caster, partial));
    }
}
