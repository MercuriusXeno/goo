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
 * vortex runs down the tunnel from the glove to the bore's reach, stacked
 * sections the width of the 3x3 bore filled by {@code bore_vortex.fsh}.
 * decision bore-vortex-with-a-worldspace-shake
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreVortex {

    /** Sections stacked down the tunnel. */
    static final int SECTIONS = 10;
    /** The vortex's radius in blocks, out to the corners of the 3x3 bore. */
    static final double TUNNEL_RADIUS = 1.5;

    private BoreVortex() {
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
        ConeSections.emit(quads, new ConeSections.Volume(player.getEyePosition(partialTick).subtract(camera),
                player.getViewVector(partialTick), bore.delivery().range(), SECTIONS, distance -> TUNNEL_RADIUS));
        buffers.endBatch(GooRenderTypes.BORE_VORTEX_TYPE);
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
        return bores(ability, GloveUseTracker.showsArea()) ? ability : null;
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
