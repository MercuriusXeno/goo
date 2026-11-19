package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.TickPlantsStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Growth's breeze: while right click holds Growth, soft wisps of glowing
 * leaf green streak out from the glove along the look and drift outward
 * through its cone, brighter bands pulsing out about twice a second. The cone
 * is drawn from the glove as stacked cross-sections square to the look, each
 * filled by {@code growth_breeze.fsh}, as Petrify's fog is.
 * growth-breeze-ticks-plants
 */
public final class GrowthBreeze {

    /** Cross-sections stacked along the cone; as many as Petrify's, as the breeze starts at the glove too. */
    static final int SECTIONS = 16;
    /** The first section stands right at the glove, so the breeze pours from it. */
    static final double FROM_THE_GLOVE = 0.05;
    private static final double HALF = 0.5;

    private GrowthBreeze() {
    }

    /**
     * Hands the shared path the breeze while Growth is held, sorted by the
     * middle of its cone.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the breeze's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility growth = player == null ? null : heldGrowth(player);
        if (growth == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 apex = CastOrigin.ofLocal(mc).subtract(camera);
        Vec3 axis = player.getViewVector(partialTick);
        double range = growth.delivery().range();
        double tan = Math.tan(Math.toRadians(growth.delivery().coneDegrees() * HALF));
        ConeSections.Volume volume = new ConeSections.Volume(apex, axis, player.getViewYRot(partialTick),
                FROM_THE_GLOVE, range, SECTIONS, distance -> distance * tan);
        draws.accept(new SortedDraw(apex.add(axis.normalize().scale(range * HALF)).lengthSqr(),
                () -> drawBreeze(mc, frame, volume)));
    }

    private static void drawBreeze(Minecraft mc, LevelFrame frame, ConeSections.Volume volume) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(frame.poseStack().last(),
                buffers.getBuffer(GooRenderTypes.GROWTH_BREEZE_TYPE));
        ConeSections.emit(quads, volume);
        buffers.endBatch(GooRenderTypes.GROWTH_BREEZE_TYPE);
    }

    /**
     * The Growth the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Growth is held
     */
    private static @Nullable ClientAbility heldGrowth(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean grows = ability != null && ability.behaviors().stream().anyMatch(TickPlantsStep.class::isInstance);
        return grows && GloveUseTracker.runsHeld(player) ? ability : null;
    }
}
