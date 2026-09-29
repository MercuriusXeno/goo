package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;

/**
 * Draws every live burnout explosion through the visual its goo type names,
 * after translucent blocks so the additive layers blend over the world
 * (decision elemental-explosion-per-type).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ChainBurnoutRenderer {

    private ChainBurnoutRenderer() {
    }

    /**
     * Draws the live burnouts, dropping each whose explosion has run its duration.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<ChainBurnouts.Burnout> burnouts = ChainBurnouts.CLIENT.live(mc.level.getGameTime());
        if (burnouts.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BurnoutFrame frame = new BurnoutFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera().position(), mc.level.getGameTime() + partialTick);
        for (ChainBurnouts.Burnout burnout : burnouts) {
            burnout.visual().render(burnout, frame);
        }
    }
}
