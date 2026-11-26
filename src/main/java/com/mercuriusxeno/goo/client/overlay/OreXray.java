package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.HeldSweepSections;
import com.mercuriusxeno.goo.client.BufferedPass;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.HeldSweepVisual;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Shows the ore a held sweep, Glimmer's or Magnet's, revealed through walls:
 * each ore block drawn as itself, its own texture, a little translucent and
 * fully lit, with a soft glow about it in the color of the goo type that cast
 * the sweep, fading in as the front reaches it and away at its life's end,
 * through the pipelines Sculker's shroom vision shows fungus by.
 * decision glitter-sphere-icons-gem-ore-groups
 * decision ore-glow-takes-the-casting-goos-color
 */
public final class OreXray {

    /** The ore's own color, a little translucent at full show. */
    private static final int ORE_RGB = 0xFFFFFF;
    private static final float ORE_ALPHA = 0.8f;
    private static final float GLOW_ALPHA = 0.3f;
    /** How far the glow swells past the block. */
    private static final float GLOW_SWELL = 1.1f;
    private static final int OPAQUE = 255;
    /** The ore block as itself, a little translucent. */
    public static final OrePass ORE_PASS = new OrePass(false, ORE_ALPHA, 1f);
    /** The soft glow swollen past the block, in the casting goo type's color. */
    public static final OrePass GLOW_PASS = new OrePass(true, GLOW_ALPHA, GLOW_SWELL);
    private static final List<OrePass> PASSES = List.of(ORE_PASS, GLOW_PASS);

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
            Vec3 camera = mc.gameRenderer.mainCamera().position();
            draws.accept(sortedByNearest(showing, camera, () -> drawOre(pos -> FungusXray.partsAt(mc, pos),
                    frame.poseStack(), camera, showing, ClientGooTypes::color,
                    pass -> BufferedPass.of(ImmediateBuffers.shared(), renderTypeOf(mc, pass)))));
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
     * decision ore-glow-takes-the-casting-goos-color
     *
     * @param castingColored whether the pass draws in the casting goo type's color rather than white
     * @param alpha          the pass's alpha at full show
     * @param swell          the scale about each block's center
     */
    public record OrePass(boolean castingColored, float alpha, float swell) {

        /**
         * The pass's color for a block: the held sweep's tint for the goo
         * type that revealed it, so the glow and the sweep agree, or white
         * for the ore block itself.
         *
         * @param gooType   the goo type whose sweep revealed the block
         * @param typeColor answers a goo type's color
         * @return 0xRRGGBB
         */
        int rgbOf(ResourceKey<GooTypeDefinition> gooType, ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
            return castingColored ? HeldSweepVisual.tint(gooType, typeColor) : ORE_RGB;
        }
    }

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
     * @param typeColor answers a goo type's color
     * @param open      opens a pass's batch
     */
    public static void drawOre(Function<BlockPos, List<BlockStateModelPart>> partsAt, PoseStack poseStack,
                               Vec3 camera, List<OreSightings.Sighting> showing,
                               ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor,
                               Function<OrePass, BufferedPass> open) {
        HeldSweepSections.profiled(HeldSweepSections.ORE_DRAW, () -> {
            // both passes draw each block's one model, so it is read once a frame
            List<List<BlockStateModelPart>> parts = showing.stream().map(sighting -> partsAt.apply(sighting.pos()))
                    .toList();
            for (OrePass pass : PASSES) {
                BufferedPass batch = open.apply(pass);
                // a vein's blocks show alike and stand together, so one look serves each run of them
                float lookShown = Float.NaN;
                ResourceKey<GooTypeDefinition> lookType = null;
                QuadInstance look = null;
                for (int index = 0; index < showing.size(); index++) {
                    OreSightings.Sighting sighting = showing.get(index);
                    if (look == null || Float.compare(sighting.shown(), lookShown) != 0
                            || !sighting.gooType().equals(lookType)) {
                        lookShown = sighting.shown();
                        lookType = sighting.gooType();
                        look = FungusXray.instance(color(pass.rgbOf(lookType, typeColor), pass.alpha() * lookShown));
                    }
                    FungusXray.drawParts(parts.get(index), poseStack, batch.consumer(),
                            new FungusXray.Placed(camera, sighting.pos(), pass.swell()), look);
                }
                batch.end().run();
            }
        });
    }

    private static int color(int rgb, float alpha) {
        return ARGB.color(Math.round(Math.clamp(alpha, 0f, 1f) * OPAQUE), rgb);
    }
}
