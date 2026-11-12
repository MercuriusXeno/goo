package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Floats a life mark over the head of every rallied or charmed mob in sight,
 * and every mob Heal or Drain touched lately or Leader senses: the charmed
 * heart or the rally fist leading the row on a marked mob, then its health
 * bar, for as long as the mark holds.
 * charm-glisten-and-icon-over-the-head
 * charm-holds-until-struck
 * rallied-mobs-wear-a-red-clenched-fist
 * rally-and-charm-icons-sit-left-of-the-heart
 * rallied-and-charmed-mobs-always-show-their-health-bar
 * leader-shows-life-bars-while-it-stands
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeadMarks {

    private HeadMarks() {
    }

    /**
     * Draws the life marks after the translucent world.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        LifeMarkBillboard.drawAll(event.getPoseStack(), lifeMarkedInSight(mc.level), partialTick);
    }

    private static List<LivingEntity> lifeMarkedInSight(ClientLevel level) {
        long tick = level.getGameTime();
        List<LivingEntity> marked = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !living.isInvisible()
                    && LifeMarkBillboard.showsLifeMark(living.getId(), tick, TouchedMobs.CLIENT, LeaderSense.CLIENT,
                    headMarkOf(living).isPresent())) {
                marked.add(living);
            }
        }
        return marked;
    }

    /**
     * The head mark a mob wears, leading its life mark's row.
     *
     * @param living the mob
     * @return the charm heart or the rally fist, empty for a mob neither charmed nor rallied
     */
    static Optional<HeadMark> headMarkOf(LivingEntity living) {
        return HeadMark.of(living.hasData(GooAttachments.CHARMED), living.hasData(GooAttachments.RALLIED));
    }
}
