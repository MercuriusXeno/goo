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
 * Bore's drill: while right click holds a bore stream, a big drill emerges
 * from the glove, a solid cone bit widest at the glove and pointed at the
 * tip, out to the bore's reach on the eye line, sinking into the blocks it
 * drills, which hide the buried length. {@code bore_drill.fsh} spins spiral
 * grooves about it in rock's two tones, as the Bore icon draws them.
 * decision bore-shows-a-big-drill-from-the-glove
 * decision bore-vortex-with-a-worldspace-shake
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreDrill {

    /** Rings the bit's surface runs between, from the glove to the tip. */
    static final int RINGS = 24;
    /** The bit's radius at the glove, in blocks. */
    static final double BASE_RADIUS = 0.3;
    /** How many ticks the bit takes to emerge from the glove to its full length. */
    static final double EMERGE_TICKS = 5;

    private BoreDrill() {
    }

    /**
     * The bit at its full length: a cone from the glove to the bore's reach
     * on the eye line it drills.
     *
     * @param eye   the eye, camera relative
     * @param hand  the glove, camera relative
     * @param look  the look's unit vector
     * @param yaw   the look's yaw in degrees
     * @param reach the bore's reach from the eye
     * @return the bit, its apex at the glove
     */
    static ConeSections.Volume volume(Vec3 eye, Vec3 hand, Vec3 look, float yaw, double reach) {
        Vec3 toTip = eye.add(look.scale(reach)).subtract(hand);
        return cone(hand, toTip.normalize(), yaw, toTip.length());
    }

    /**
     * The bit part way out of the glove: as long as its share of its full
     * length, keeping its width at the glove and its point at the tip.
     *
     * @param full  the bit at its full length
     * @param share how far it has emerged, 0 to 1
     * @return the bit
     */
    static ConeSections.Volume emerged(ConeSections.Volume full, double share) {
        return cone(full.apex(), full.axis(), full.yaw(), full.range() * Math.clamp(share, 0, 1));
    }

    /**
     * How far the bit has emerged, by how long the bore has run.
     *
     * @param ticks the ticks the bore has run, with the partial tick
     * @return the share of its full length, 0 to 1
     */
    static double emergedShare(double ticks) {
        return Math.clamp(ticks / EMERGE_TICKS, 0, 1);
    }

    private static ConeSections.Volume cone(Vec3 apex, Vec3 axis, float yaw, double length) {
        return new ConeSections.Volume(apex, axis, yaw, 0, length, RINGS,
                distance -> length <= 0 ? 0 : BASE_RADIUS * Math.max(0, 1 - distance / length));
    }

    /**
     * Draws the bit after the translucent blocks while a bore runs.
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
        ConeSections.Volume full = volume(player.getEyePosition(partialTick).subtract(camera),
                GloveAim.handPosition(mc.gameRenderer.getMainCamera()).subtract(camera),
                player.getViewVector(partialTick), player.getViewYRot(partialTick), bore.delivery().range());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.BORE_DRILL_TYPE));
        ConeSections.emitWalls(quads, emerged(full, emergedShare(GloveUseTracker.channelTicks() + partialTick)));
        buffers.endBatch(GooRenderTypes.BORE_DRILL_TYPE);
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
