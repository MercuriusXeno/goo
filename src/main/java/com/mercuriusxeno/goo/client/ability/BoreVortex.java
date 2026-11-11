package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreStep;
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
 * Bore's vortex: while right click holds a bore stream, a spiralling dust
 * vortex runs down the tunnel from the glove to the bore's reach on the eye
 * line, a tube one block wide, as wide as the line the bore drills, its
 * walls rifled by {@code bore_vortex.fsh}.
 * decision bore-vortex-with-a-worldspace-shake
 * decision bore-breaks-a-15-degree-cone
 * decision diagnose-and-correct-bores-small-distant-vortex
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreVortex {

    /** Rings the tube's walls run between, down the tunnel. */
    static final int SECTIONS = 24;
    private static final double HALF = 0.5;
    /** The share of its full width the vortex leaves the glove at. */
    private static final double AT_THE_GLOVE = 0.2;
    /** How far from the glove, in blocks, the vortex widens to the line it drills. */
    private static final double WIDENS_OVER = 1;

    private BoreVortex() {
    }

    /**
     * The vortex's radius a distance down the tunnel: it leaves the glove
     * narrow and widens over the first block to half a block, so it draws
     * the one-block line the bore drills from there to the reach.
     * decision bore-breaks-a-15-degree-cone
     *
     * @param distance the distance from the glove in blocks
     * @return the radius in blocks
     */
    static double radiusAt(double distance) {
        double widened = Math.min(1, distance / WIDENS_OVER);
        return HALF * (AT_THE_GLOVE + (1 - AT_THE_GLOVE) * widened);
    }

    /**
     * Draws the vortex after the translucent blocks while a bore runs and a
     * block stands within its reach.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility bore = player == null ? null : runningBore(player);
        if (bore == null || mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.BORE_VORTEX_TYPE));
        ConeSections.emitWalls(quads, volume(player.getEyePosition(partialTick).subtract(camera),
                GloveAim.handPosition(mc.gameRenderer.getMainCamera()).subtract(camera),
                player.getViewVector(partialTick), player.getViewYRot(partialTick), bore.delivery().range()));
        buffers.endBatch(GooRenderTypes.BORE_VORTEX_TYPE);
    }

    /**
     * The tube the vortex fills: from the glove to the bore's reach on the
     * eye line it drills, so the player sees it run long across the view
     * rather than end on down their own line of sight.
     * decision diagnose-and-correct-bores-small-distant-vortex
     *
     * @param eye   the eye, camera relative
     * @param hand  the glove, camera relative
     * @param look  the look's unit vector
     * @param yaw   the look's yaw in degrees
     * @param reach the bore's reach from the eye
     * @return the volume, its apex at the glove
     */
    static ConeSections.Volume volume(Vec3 eye, Vec3 hand, Vec3 look, float yaw, double reach) {
        Vec3 toEnd = eye.add(look.scale(reach)).subtract(hand);
        return new ConeSections.Volume(hand, toEnd.normalize(), yaw, 0, toEnd.length(), SECTIONS,
                BoreVortex::radiusAt);
    }

    /**
     * The bore the local player's glove runs now.
     *
     * @param player the local player
     * @return the selected ability while right click holds it and it bores, otherwise null
     */
    static @Nullable ClientAbility runningBore(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return bores(ability, GloveUseTracker.runsHeld(player)) ? ability : null;
    }

    /**
     * Whether a bore runs: the selected ability bores and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the bore runs
     */
    static boolean bores(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && ability.behaviors().stream().anyMatch(BoreStep.class::isInstance);
    }
}
