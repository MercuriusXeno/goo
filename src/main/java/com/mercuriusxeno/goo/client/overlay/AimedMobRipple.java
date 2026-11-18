package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.AfterimageRenderer;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws the aimed mob's mark over its model after the level: the toned-down
 * silhouette ripple {@link AimedMobMark} times, in the goo's highlight color,
 * beside the outline AimTracker gives it. It draws for a mob the aim reaches
 * straight; a mob reached through a gate keeps its outline alone.
 * active-target-indicators-carry-depth-and-animation
 */
public final class AimedMobRipple {

    private AimedMobRipple() {
    }

    /**
     * Hands the shared path the aimed mob's ripple, sorted by where the mob
     * stands this frame, in the main frame alone and for a mob the aim
     * reaches straight, not through a gate.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision active-target-indicators-carry-depth-and-animation
     *
     * @param frame the frame being drawn
     * @param draws where the ripple's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        Entity mob = AimTracker.aimedMob();
        if (PortalView.drawing() || mc.player == null || mob == null || AimTracker.currentTarget().passage() != null) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = GloveAim.selectedGooType(mc.player);
        if (type != null) {
            Vec3 at = mob.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            draws.accept(new SortedDraw(at.distanceToSqr(mc.gameRenderer.getMainCamera().position()),
                    () -> drawMark(frame, mc, mob, type)));
        }
    }

    /**
     * Draws the mob's mark as it stands this frame.
     *
     * @param frame the frame being drawn
     * @param mc    the client
     * @param mob   the aimed mob
     * @param type  the selected goo type, whose highlight the ripple wears
     */
    private static void drawMark(LevelFrame frame, Minecraft mc, Entity mob, ResourceKey<GooTypeDefinition> type) {
        if (mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        AimedMobMark mark = AimTracker.markFor(mob, mc.level.getGameTime() + partialTick);
        if (mark.strength() <= 0f) {
            return;
        }
        EntityRenderState pose = mc.getEntityRenderDispatcher().extractEntity(mob, partialTick);
        AfterimageRenderer.drawPulses(frame, pose, mob.getPosition(partialTick), ClientGooTypes.highlight(type),
                List.of(mark.pulse()));
    }
}
