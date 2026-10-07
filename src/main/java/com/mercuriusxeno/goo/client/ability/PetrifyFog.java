package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Petrify's fog: while right click holds Petrify, undulating waves of dust
 * fog wash forward over everything in its cone, a medusa's gaze miasma. The
 * cone is drawn as stacked cross-sections square to the look, each filled by
 * {@code petrify_fog.fsh}; the cone itself, the area of effect, is never
 * outlined (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PetrifyFog {

    /** Cross-sections stacked along the cone. */
    static final int SECTIONS = 12;
    private static final double HALF = 0.5;

    private PetrifyFog() {
    }

    /**
     * Draws the fog after the translucent blocks while Petrify is held.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility petrify = player == null ? null : heldPetrify(player);
        if (petrify == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 apex = player.getEyePosition(partialTick).subtract(camera);
        Vec3 axis = player.getViewVector(partialTick);
        double range = petrify.delivery().range();
        double halfAngle = Math.toRadians(petrify.delivery().coneDegrees() * HALF);
        double tan = Math.tan(halfAngle);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.PETRIFY_FOG_TYPE));
        ConeSections.emit(quads, new ConeSections.Volume(apex, axis, range, SECTIONS, distance -> distance * tan));
        buffers.endBatch(GooRenderTypes.PETRIFY_FOG_TYPE);
    }

    /**
     * The Petrify the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Petrify is held
     */
    private static @Nullable ClientAbility heldPetrify(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean petrifies = ability != null && ability.behaviors().stream().anyMatch(PetrifyStep.class::isInstance);
        return petrifies && GloveUseTracker.showsArea() ? ability : null;
    }
}
