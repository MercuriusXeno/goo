package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.TravelingStep;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.entity.RollingGoo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;

/**
 * Draws each rolling goo where it is this frame: its type's blob, and about
 * it the swirling nova its traveling step names, frost's Orb rolling with
 * its storm (decision orb-carries-a-swirling-nova). The entity's own
 * renderer draws nothing, so the ball and its swirl draw together here.
 */
public final class RollingGooRenderer {

    private RollingGooRenderer() {
    }

    /**
     * The swirl a rolling ability carries, read off its traveling step.
     *
     * @param abilityId the rolling ability
     * @return the swirl's reach, 0 for none
     */
    static float swirlOf(String abilityId) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? 0f : TravelingStep.of(ability.behaviors()).map(TravelingStep::swirl).orElse(0f);
    }

    /**
     * Hands the shared path one draw per rolling goo, its ball and its swirl,
     * each sorted by where the goo is this frame.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where each rolling goo's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = level.getGameTime() + partialTick;
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RollingGoo goo && goo.gooType() != null) {
                Vec3 at = goo.getPosition(partialTick);
                draws.accept(new SortedDraw(at.distanceToSqr(camera), () -> {
                    drawGoo(level, frame.poseStack(), buffers, camera, goo, at, gameTime);
                    buffers.endBatch();
                }));
            }
        }
    }

    private static void drawGoo(ClientLevel level, PoseStack poseStack, ImmediateBuffers buffers,
                                Vec3 camera, RollingGoo goo, Vec3 at, float gameTime) {
        ResourceKey<GooTypeDefinition> type = goo.gooType();
        poseStack.pushPose();
        poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        GooFlightRenderer.renderBlob(poseStack, buffers, type, gameTime, 1f);
        poseStack.popPose();
        float swirl = swirlOf(goo.abilityId());
        if (swirl > 0f) {
            OrbSwirl.draw(level, poseStack, buffers, camera, at, swirl, gameTime,
                    FrostExplosionVisual.seedOf(goo.getId()));
        }
    }
}
