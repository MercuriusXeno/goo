package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.HeldSweepSections;
import com.mercuriusxeno.goo.client.BufferedPass;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shows the gem ore Glimmer revealed through walls: each ore block drawn as
 * itself, its own texture, a little translucent and fully lit, with a soft
 * crystal glow about it, fading in as the front reaches it and away at its
 * life's end, through the pipelines Sight shows fungus by.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreXray {

    /** The ore's own color, a little translucent at full show. */
    private static final int ORE_RGB = 0xFFFFFF;
    private static final float ORE_ALPHA = 0.8f;
    /** The glow's pale crystal blue. */
    private static final int GLOW_RGB = 0xA8E8FF;
    private static final float GLOW_ALPHA = 0.3f;
    /** How far the glow swells past the block. */
    private static final float GLOW_SWELL = 1.1f;
    private static final int OPAQUE = 255;

    private OreXray() {
    }

    /**
     * Hands the shared path the revealed ore, drawn through walls, sorted by
     * the nearest ore block showing.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision glitter-sphere-icons-gem-ore-groups
     *
     * @param frame the frame being drawn
     * @param draws where the ore's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<OreSightings.Sighting> showing = showingAt(now);
        if (!showing.isEmpty()) {
            Vec3 camera = mc.gameRenderer.getMainCamera().position();
            draws.accept(sortedByNearest(showing, camera, () -> drawOre(pos -> FungusXray.partsAt(mc, pos),
                    frame.poseStack(), camera, showing,
                    pass -> BufferedPass.of(mc.renderBuffers().bufferSource(), renderTypeOf(mc, pass)))));
        }
    }

    /**
     * The ore blocks the client's revealed veins show at a moment.
     *
     * @param now the game time with its partial tick
     * @return the blocks to draw, each as fully as its vein shows
     */
    public static List<OreSightings.Sighting> showingAt(double now) {
        return HeldSweepSections.profiled(HeldSweepSections.ORE_LIST, () -> OreSightings.CLIENT.showingAt(now));
    }

    /**
     * The ore's draw, sorted by the nearest ore block showing.
     *
     * @param showing the ore blocks showing
     * @param camera  the camera's place in the world
     * @param draw    draws the ore, ending its buffers
     * @return the draw
     */
    public static SortedDraw sortedByNearest(List<OreSightings.Sighting> showing, Vec3 camera, Runnable draw) {
        return HeldSweepSections.profiled(HeldSweepSections.SORT_KEY, () -> SortedDraw.nearestOf(
                showing.stream().map(sighting -> Vec3.atCenterOf(sighting.pos())), camera, draw));
    }

    /**
     * One of the two passes the revealed ore draws in.
     * decision glitter-sphere-icons-gem-ore-groups
     *
     * @param rgb   the pass's color
     * @param alpha the pass's alpha at full show
     * @param swell the scale about each block's center
     */
    public record OrePass(int rgb, float alpha, float swell) {
    }

    /** The ore block as itself, a little translucent. */
    public static final OrePass ORE_PASS = new OrePass(ORE_RGB, ORE_ALPHA, 1f);
    /** The soft crystal glow swollen past the block. */
    public static final OrePass GLOW_PASS = new OrePass(GLOW_RGB, GLOW_ALPHA, GLOW_SWELL);
    private static final List<OrePass> PASSES = List.of(ORE_PASS, GLOW_PASS);

    private static RenderType renderTypeOf(Minecraft mc, OrePass pass) {
        Identifier atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location();
        return pass == ORE_PASS ? GooRenderTypes.fungusXray(atlas) : GooRenderTypes.fungusGlow(atlas);
    }

    /**
     * Draws every showing ore block through each pass in turn, each as fully
     * as its vein shows, submitting each pass's batch before the next opens.
     *
     * @param partsAt   the parts of the model each block draws with
     * @param poseStack the pose stack
     * @param camera    the camera's position
     * @param showing   the ore blocks showing
     * @param open      opens a pass's batch
     */
    public static void drawOre(Function<BlockPos, List<BlockStateModelPart>> partsAt, PoseStack poseStack,
                               Vec3 camera, List<OreSightings.Sighting> showing,
                               Function<OrePass, BufferedPass> open) {
        HeldSweepSections.profiled(HeldSweepSections.ORE_DRAW, () -> {
            for (OrePass pass : PASSES) {
                BufferedPass batch = open.apply(pass);
                for (OreSightings.Sighting sighting : showing) {
                    FungusXray.drawParts(partsAt.apply(sighting.pos()), poseStack, batch.consumer(),
                            new FungusXray.Placed(camera, sighting.pos(), pass.swell()),
                            FungusXray.instance(color(pass.rgb(), pass.alpha() * sighting.shown())));
                }
                batch.end().run();
            }
        });
    }

    private static int color(int rgb, float alpha) {
        return ARGB.color(Math.round(Math.clamp(alpha, 0f, 1f) * OPAQUE), rgb);
    }
}
