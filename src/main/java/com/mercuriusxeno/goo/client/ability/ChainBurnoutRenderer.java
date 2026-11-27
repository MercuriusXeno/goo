package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws every live burnout explosion through the visual its goo type names,
 * once the level is drawn, after any translucent feature writing depth, so
 * the additive layers blend over the world and over what stands behind an
 * explosion rather than it drawing over an explosion standing in front of it (decision elemental-explosion-per-type).
 */
public final class ChainBurnoutRenderer {

    private ChainBurnoutRenderer() {
    }

    /**
     * Drops each burnout whose explosion has run its duration and hands the
     * shared path one draw per live burnout, each sorted by its center.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision elemental-explosion-per-type
     *
     * @param frame the frame being drawn
     * @param draws where each burnout's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<ChainBurnouts.Burnout> burnouts = ChainBurnouts.CLIENT.live(mc.level.getGameTime());
        if (burnouts.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        BurnoutFrame burnoutFrame = new BurnoutFrame(frame.poseStack(), buffers, camera,
                mc.level.getGameTime() + partialTick);
        for (ChainBurnouts.Burnout burnout : burnouts) {
            draws.accept(new SortedDraw(Vec3.atCenterOf(burnout.pos()).distanceToSqr(camera), () -> {
                burnout.visual().render(burnout, burnoutFrame);
                buffers.endBatch();
            }));
        }
    }
}
