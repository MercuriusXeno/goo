package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.UnmakeStep;
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
 * Unmake's destabilizing waves: while right click holds Unmake, goo colored
 * bands pulse out of the glove and sweep down the cone. The cone is drawn
 * from the glove as stacked cross-sections square to the look, as Petrify's
 * fog is, each filled by {@code unmake_waves.fsh}.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UnmakeWaves {

    /** Cross-sections stacked along the cone, enough that each band reads as a solid front. */
    static final int SECTIONS = 24;
    /** The first section stands right at the glove, so the waves leave it. */
    static final double FROM_THE_GLOVE = 0.05;
    private static final double HALF = 0.5;

    private UnmakeWaves() {
    }

    /**
     * Draws the waves after the translucent blocks while Unmake is held.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility unmake = player == null ? null : heldUnmake(player);
        if (unmake == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 apex = GloveAim.handPosition(mc.gameRenderer.getMainCamera()).subtract(camera);
        Vec3 axis = player.getViewVector(partialTick);
        double tan = Math.tan(Math.toRadians(unmake.delivery().coneDegrees() * HALF));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.UNMAKE_WAVES_TYPE));
        ConeSections.emit(quads, new ConeSections.Volume(apex, axis, FROM_THE_GLOVE, unmake.delivery().range(),
                SECTIONS, distance -> distance * tan));
        buffers.endBatch(GooRenderTypes.UNMAKE_WAVES_TYPE);
    }

    /**
     * The Unmake the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Unmake is held
     */
    private static @Nullable ClientAbility heldUnmake(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean unmakes = ability != null && ability.behaviors().stream().anyMatch(UnmakeStep.class::isInstance);
        return unmakes && GloveUseTracker.showsArea() ? ability : null;
    }
}
