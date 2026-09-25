package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ber.style.NetherHoleStyles;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side event handler that drives the nether black-hole lens
 * post-process. Hooks {@link RenderLevelStageEvent.AfterLevel}, which
 * fires inside {@code GameRenderer.renderLevel} after
 * {@code LevelRenderer.renderLevel} returns (so all BERs have already
 * run their extract paths) and before the post-effect chain executes.
 * That timing is the only window where:
 * <ul>
 *   <li>The BER has had a chance to call
 *       {@link NetherLensEffect#markHoleActive} for the frame.</li>
 *   <li>The projection/model-view matrices are still the ones used
 *       for level rendering, so projecting the hole's world position
 *       to a UV matches what the main framebuffer sees.</li>
 *   <li>The post-effect pass has not yet consumed the uniform buffer,
 *       so a fresh {@code writeToBuffer} is picked up this same
 *       frame.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class NetherLensClientEvents {

    private NetherLensClientEvents() {}

    /** Drives the lens post-effect once per frame at the end of the
     * level pass.
     *
     * @param event the render-level stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        driveFrame(NetherHoleStyles.lensEnabled(),
                () -> NetherLensEffect.applyPerFrame(mc),
                () -> NetherLensEffect.release(mc.gameRenderer));
    }

    /** Runs the per-frame lens work only while the config turns the lens
     * on, and otherwise only releases it (decision one-disc-mesh-config-lens).
     *
     * @param lensEnabled   whether the client config turns the lens on
     * @param applyPerFrame the per-frame projection and uniform upload
     * @param release       the release of a lens left from an earlier frame
     */
    static void driveFrame(boolean lensEnabled, Runnable applyPerFrame, Runnable release) {
        if (lensEnabled) {
            applyPerFrame.run();
            return;
        }
        release.run();
    }
}
