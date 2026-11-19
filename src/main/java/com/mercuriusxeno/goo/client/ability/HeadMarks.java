package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Floats a life mark over the head of every led, charmed or downed mob in sight,
 * and every mob Heal or Drain touched lately or Herd senses: the charmed
 * heart or the lead fist leading the row on a marked mob, then its health
 * bar, for as long as the mark holds.
 * charm-glisten-and-icon-over-the-head
 * charm-holds-until-struck
 * rallied-mobs-wear-a-red-clenched-fist
 * rally-and-charm-icons-sit-left-of-the-heart
 * rallied-and-charmed-mobs-always-show-their-health-bar
 * leader-shows-life-bars-while-it-stands
 * downed-mob-shows-a-skull-and-its-negative-health
 */
public final class HeadMarks {

    private HeadMarks() {
    }

    /**
     * Hands the shared path the life marks of every marked mob in sight,
     * sorted by the nearest marked mob. The marks draw through vanilla's
     * see-through text, through walls, so a mark never clips into the wall
     * it stands near (operator ruling 2026-11-18).
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the marks' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<LivingEntity> marked = lifeMarkedInSight(mc.level);
        if (marked.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        draws.accept(SortedDraw.nearestOf(marked.stream().map(Entity::position),
                mc.gameRenderer.getMainCamera().position(),
                () -> LifeMarkBillboard.drawAll(frame.poseStack(), marked, partialTick)));
    }

    private static List<LivingEntity> lifeMarkedInSight(ClientLevel level) {
        long tick = level.getGameTime();
        List<LivingEntity> marked = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !living.isInvisible()
                    && LifeMarkBillboard.showsLifeMark(living.getId(), tick, TouchedMobs.CLIENT, HerdSense.CLIENT,
                    headMarkOf(living).isPresent(), living.hasData(GooAttachments.DOWNED))) {
                marked.add(living);
            }
        }
        return marked;
    }

    /**
     * The head mark a mob wears, leading its life mark's row.
     *
     * @param living the mob
     * @return the charm heart or the lead fist, empty for a mob neither charmed nor led
     */
    static Optional<HeadMark> headMarkOf(LivingEntity living) {
        return HeadMark.of(living.hasData(GooAttachments.CHARMED), living.hasData(GooAttachments.LED));
    }
}
