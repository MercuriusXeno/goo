package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.ability.AfterimageRenderer;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;

/**
 * Draws the aimed mob's mark over its model after the level: the toned-down
 * silhouette ripple {@link AimedMobMark} times, in the goo's highlight color,
 * beside the outline AimTracker gives it. It draws for a mob the aim reaches
 * straight; a mob reached through a gate keeps its outline alone.
 * active-target-indicators-carry-depth-and-animation
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AimedMobRipple {

    private AimedMobRipple() {
    }

    /**
     * Draws the aimed mob's ripple for this frame.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        Entity mob = AimTracker.aimedMob();
        if (mc.player == null || mob == null || !seenStraight()) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = GloveAim.selectedGooType(mc.player);
        if (type != null) {
            drawMark(event, mc, mob, type);
        }
    }

    /**
     * Whether the main frame draws and the aim reaches its mob straight, not through a gate.
     *
     * @return true where the mark draws in this pass
     */
    private static boolean seenStraight() {
        return !PortalView.drawing() && AimTracker.currentTarget().passage() == null;
    }

    /**
     * Draws the mob's mark as it stands this frame.
     *
     * @param event the level render stage event
     * @param mc    the client
     * @param mob   the aimed mob
     * @param type  the selected goo type, whose highlight the ripple wears
     */
    private static void drawMark(RenderLevelStageEvent.AfterLevel event, Minecraft mc, Entity mob,
                                 ResourceKey<GooTypeDefinition> type) {
        if (mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        AimedMobMark mark = AimTracker.markFor(mob, mc.level.getGameTime() + partialTick);
        if (mark.strength() <= 0f) {
            return;
        }
        EntityRenderState pose = mc.getEntityRenderDispatcher().extractEntity(mob, partialTick);
        AfterimageRenderer.drawPulses(event, pose, mob.getPosition(partialTick), ClientGooTypes.highlight(type),
                List.of(mark.pulse()));
    }
}
