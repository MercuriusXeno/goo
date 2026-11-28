package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.TickPlantsStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Growth's breeze: while right click holds Growth, soft wisps of glowing
 * leaf green streak out from the glove along the look and drift outward
 * through its cone, brighter bands pulsing out about twice a second. The cone
 * is drawn from the glove as stacked cross-sections square to the look, each
 * filled by {@code growth_breeze.fsh}, as Petrify's fog is; the cone is the one
 * the breeze is poured through, the glove's or a nozzle's.
 * growth-breeze-ticks-plants
 * decision spray-abilities-render-procedurally-bound-to-their-cone
 */
public final class GrowthBreeze {

    /** Cross-sections stacked along the cone; as many as Petrify's, as the breeze starts at the glove too. */
    static final int SECTIONS = 16;
    /** The first section stands right at the glove, so the breeze pours from it. */
    static final double FROM_THE_GLOVE = 0.05;
    private static final double HALF = 0.5;

    /** The ability the breeze is registered under in {@link ConeVisuals}. */
    static final String GROWTH_ID = "goo:leaf_growth";

    /** The cone poured through this frame, the glove's or a nozzle's, taken by the frame's draw. */
    private static @Nullable SprayCone poured;

    private GrowthBreeze() {
    }

    /**
     * Pours the breeze through a cone this frame, the glove's or a nozzle's.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone poured through, in world space
     */
    static void pour(SprayCone cone) {
        poured = cone;
    }

    /**
     * The breeze filling a cone: its sections cut from the cone's shape out to its reach.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone, camera relative
     * @return the breeze's sections
     */
    static ConeSections.Shaped breezeOf(SprayCone cone) {
        return new ConeSections.Shaped(cone, FROM_THE_GLOVE, SECTIONS);
    }

    /**
     * Hands the shared path the breeze while it is poured, sorted by the
     * middle of its cone.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the breeze's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        pourTheGlovesBreeze(mc, player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        SprayCone cone = poured;
        poured = null;
        if (cone == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        SprayCone seen = new SprayCone(cone.apex().subtract(camera), cone.axis(), cone.shape(), cone.reach());
        Vec3 middle = seen.apex().add(seen.axis().normalize().scale(seen.reach() * HALF));
        ConeSections.Shaped breeze = breezeOf(seen);
        draws.accept(new SortedDraw(middle.lengthSqr(), () -> drawBreeze(frame, breeze)));
    }

    /**
     * Pours the glove's breeze while right click holds Growth: the round cone of its delivery, from the hand along
     * the look this frame.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param mc          the client
     * @param player      the local player
     * @param partialTick the frame's partial tick
     */
    private static void pourTheGlovesBreeze(Minecraft mc, LocalPlayer player, float partialTick) {
        ClientAbility growth = heldGrowth(player);
        if (growth != null) {
            ConeVisuals.pour(growth.id().toString(), SprayCone.ofGlove(growth.delivery(), CastOrigin.ofLocal(mc),
                    player.getViewVector(partialTick)));
        }
    }

    private static void drawBreeze(LevelFrame frame, ConeSections.Shaped volume) {
        ImmediateBuffers buffers = ImmediateBuffers.shared();
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
