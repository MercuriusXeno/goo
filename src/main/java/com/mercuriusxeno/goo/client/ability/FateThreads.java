package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.SharedFate;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The thread of light between two creatures sharing a fate: a thin glowing
 * line from one's middle to the other's, drawn each frame while the link
 * the server synced stands.
 * shared-fate-pools-health-and-status
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FateThreads {

    /** The thread's color: a pale violet at most of full alpha. */
    static final int THREAD_COLOR = ARGB.color(200, 0xC8B8FF);
    private static final float WIDTH_SCALE = 1.5f;
    /** Where on a creature's height the thread meets it. */
    private static final double MIDDLE = 0.5;

    private FateThreads() {
    }

    /**
     * Draws each standing thread after the translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Entity creature : level.entitiesForRendering()) {
            if (!creature.hasData(GooAttachments.SHARED_FATE)) {
                continue;
            }
            SharedFate fate = creature.getData(GooAttachments.SHARED_FATE);
            Entity partner = fate.standsAt(level.getGameTime()) ? level.getEntity(fate.partner()) : null;
            // each pair draws once, from the creature whose id sorts first
            if (partner != null && creature.getUUID().compareTo(partner.getUUID()) < 0) {
                lines.emitPolyline(camera, new Vec3[] {middleOf(creature, partialTick), middleOf(partner, partialTick)},
                        THREAD_COLOR, width);
            }
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    private static Vec3 middleOf(Entity creature, float partialTick) {
        return creature.getPosition(partialTick).add(0, creature.getBbHeight() * MIDDLE, 0);
    }
}
