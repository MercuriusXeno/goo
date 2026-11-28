package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.entity.LeftoverMortar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a block hunk as its blocks, each at its offset from the hunk's origin
 * and lit by the world where it stands now, as a falling block draws.
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 *
 * A settled hunk lingering over its stone draws its blocks a hair swollen,
 * so they cover the chunk's own stone without fighting it for depth.
 * decision spire-carries-its-riders-up-with-the-rising-stone
 *
 * A hunk the local player holds glides each frame toward where the
 * player's own look puts it, once it has risen clear of its hole, by the same
 * hold and the same ease the server moves it by, so it trails the look
 * smoothly. A struck hunk draws nothing: HunkCrumbles draws it coming apart.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class BlockHunkRenderer extends EntityRenderer<BlockHunk, BlockHunkRenderer.HunkRenderState> {

    private static final double HALF = 0.5;
    /** A hunk draws no outline. */
    private static final int NO_OUTLINE = 0;
    /** A whole color channel, or full alpha. */
    private static final int OPAQUE = 255;
    /** How far a lingering hunk's blocks swell past the stone beneath them. */
    private static final float LINGER_SWELL = 1.002f;
    private static final float WHOLE = 1f;
    /** The farthest a rising pillar trembles from its line at the start of its rise, in blocks. */
    private static final double TREMBLE = 0.05;
    /** The tremble's two sine rates on each axis, in radians a tick, unrelated so it never repeats. */
    private static final double TREMBLE_FAST = 2.9;
    private static final double TREMBLE_SLOW = 1.7;
    /** Offsets the tremble's z from its x, so the pillar shakes in a wobble rather than along a line. */
    private static final double TREMBLE_PHASE = 1.3;
    /** How far a top's Mortar shakes along z for each step along x, so it shivers on a slant rather than a line. */
    private static final double QUIVER_SLANT = 0.6;
    /** The hunk's blocks as this frame draws them, each with its offset from the hunk's drawn position. */
    public static final class HunkRenderState extends EntityRenderState {
        final List<MovingBlockRenderState> blocks = new ArrayList<>();
        final List<Vec3> offsets = new ArrayList<>();
        final List<Faded> faded = new ArrayList<>();
        final List<RidingDraw> riding = new ArrayList<>();
        float swell = WHOLE;
        float fade = WHOLE;
    }

    /**
     * Mortar riding a held piece, as this frame draws it on the face it coats.
     *
     * @param view   the level it meshes against
     * @param offset its cell's corner from the pose's position, shaken by the quiver
     */
    record RidingDraw(RidingMortarView view, Vec3 offset) {
    }

    /**
     * One block of a placed hunk fading out over the world's block in its cell.
     *
     * @param cell   the cell it was placed in
     * @param state  the block
     * @param seed   the variant seed it wore while carried
     * @param offset its corner from the pose's position
     */
    record Faded(BlockPos cell, BlockState state, long seed, Vec3 offset) {
    }

    /**
     * @param context the renderer context
     */
    public BlockHunkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public HunkRenderState createRenderState() {
        return new HunkRenderState();
    }

    @Override
    public void extractRenderState(BlockHunk hunk, HunkRenderState state, float partialTick) {
        super.extractRenderState(hunk, state, partialTick);
        state.blocks.clear();
        state.offsets.clear();
        state.riding.clear();
        if (!(hunk.level() instanceof ClientLevel level) || hunk.shattering()) {
            return;
        }
        Vec3 lerped = hunk.getPosition(partialTick);
        double placed = hunk.placedShare(level.getGameTime() + partialTick);
        state.faded.clear();
        if (placed >= 0) {
            fadeOverThePlacedStone(hunk, state, lerped, placed);
            return;
        }
        // Drawn where the server holds it, so the holder sees what the world does.
        Vec3 position = lerped.add(trembleOf(hunk, level.getGameTime() + partialTick));
        state.swell = hunk.lingers(level.getGameTime()) ? LINGER_SWELL : WHOLE;
        // One light for the whole hunk, from the open cell above its top, so it shades evenly as it moves.
        BlockPos lightAt = lightCellOf(hunk, position);
        hunk.blocks().stream().filter(block -> block.state().getRenderShape() == RenderShape.MODEL).forEach(block -> {
            Vec3 corner = BlockHunk.cornerOf(position, block);
            state.blocks.add(movingBlock(level, block, lightAt, BlockHunk.variantCellOf(hunk.home(), block)));
            // The pose stands at the lerped position, so a block drawn elsewhere is offset from it.
            state.offsets.add(corner.subtract(lerped));
        });
        extractRiding(hunk, state, level, position, lerped, level.getGameTime() + partialTick);
    }

    /**
     * Gathers the Mortar riding a held piece, each rider drawn in the cell
     * beside the face it coats, as Mortar on that block, shaken by the quiver
     * across the face for the first half second after the grab.
     * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
     *
     * @param hunk     the hunk
     * @param state    the render state
     * @param level    the client level
     * @param position where the hunk draws
     * @param lerped   where the pose stands
     * @param time     the game time with the partial tick
     */
    private static void extractRiding(BlockHunk hunk, HunkRenderState state, ClientLevel level, Vec3 position,
                                      Vec3 lerped, double time) {
        double quiver = LeftoverMortar.quiverOffset(hunk.liftedAge(time));
        for (LeftoverMortar.Riding riding : hunk.ridingNow()) {
            BlockHunk.HunkBlock block = blockAt(hunk, riding.offset());
            if (riding.layers() <= 0 || block == null) {
                continue;
            }
            Direction face = riding.face();
            Vec3 corner = BlockHunk.cornerOf(position, block).add(face.getUnitVec3());
            BlockPos cell = BlockPos.containing(corner.add(HALF, HALF, HALF));
            RidingMortarView view = new RidingMortarView(level, cell, mortarOn(face, riding.layers()),
                    cell.relative(face.getOpposite()), block.state());
            state.riding.add(new RidingDraw(view, corner.subtract(lerped).add(acrossTheFace(face).scale(quiver))));
        }
    }

    private static BlockHunk.@Nullable HunkBlock blockAt(BlockHunk hunk, BlockPos offset) {
        return hunk.blocks().stream().filter(block -> block.offset().equals(offset)).findFirst().orElse(null);
    }

    /**
     * Mortar of some layers coating a block on one of its faces: lying on its
     * top, clinging to a side, or hanging from its underside.
     *
     * @param face   the block's face the Mortar coats
     * @param layers the layers
     * @return the Mortar's state, in the cell beside that face
     */
    private static BlockState mortarOn(Direction face, int layers) {
        BlockState mortar = GooBlocks.ROCK_MORTAR.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS,
                Math.min(layers, VolumeCellBlock.MAX_LAYERS));
        return switch (face) {
            case UP -> mortar;
            case DOWN -> mortar.setValue(VolumeCellBlock.CEILING, true);
            default -> mortar.setValue(VolumeCellBlock.coatProperty(face.getOpposite()), SideCoat.ATTACHED);
        };
    }

    /**
     * The way riding Mortar shakes across the face it coats: along the face,
     * never off it.
     *
     * @param face the face
     * @return the shake's direction
     */
    private static Vec3 acrossTheFace(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, QUIVER_SLANT) : new Vec3(0, 1, 0);
    }

    /**
     * The cell a moving hunk takes its light from: just above its top's middle, open air.
     *
     * @param hunk     the hunk
     * @param position where it draws
     * @return the cell
     */
    private static BlockPos lightCellOf(BlockHunk hunk, Vec3 position) {
        AABB box = hunk.getBoundingBox().move(position.subtract(hunk.position()));
        return BlockPos.containing(box.getCenter().x, box.maxY + HALF, box.getCenter().z);
    }

    /**
     * A hunk lingering over the stone it set: each block tesselated as the
     * world tesselates the block in its cell, so it shades as the world's
     * block does rather than as a moving block over it; a placed chunk's
     * blocks wear the texture they wore while carried and fade out, so a
     * texture that differs at the new cell crossfades rather than popping.
     *
     * @param hunk   the hunk
     * @param state  the render state
     * @param lerped where the pose stands
     * @param placed how far the linger has run, 0 to 1
     */
    private static void fadeOverThePlacedStone(BlockHunk hunk, HunkRenderState state, Vec3 lerped, double placed) {
        // A settled pillar wears the variant its cells do, so it covers the world's stone whole, never see-through
        // while the client's chunk redraws; a placed chunk's variant may differ at its new cells, so it crossfades.
        state.fade = hunk.risesFromTheGround() ? WHOLE : (float) (1 - placed);
        for (BlockHunk.HunkBlock block : hunk.blocks()) {
            if (block.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            BlockPos cell = BlockPos.containing(BlockHunk.cornerOf(hunk.position(), block).add(HALF, HALF, HALF));
            BlockPos variantCell = BlockHunk.variantCellOf(hunk.home(), block);
            state.faded.add(new Faded(cell, block.state(), block.state().getSeed(variantCell),
                    Vec3.atLowerCornerOf(cell).subtract(lerped)));
        }
    }

    /**
     * How far a rising pillar trembles off its line this frame: a sum of fast
     * sines across the ground, strongest as it breaks out and easing to
     * still as it reaches its cells.
     * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
     *
     * @param hunk the hunk
     * @param time the game time with the partial tick
     * @return the tremble, zero for a hunk not climbing
     */
    private static Vec3 trembleOf(BlockHunk hunk, double time) {
        if (hunk.heldInItsSlot()) {
            // Grabbed and still seated, it shakes in its slot until the holder pulls it out.
            return new Vec3(sway(time, 0), 0, sway(time, TREMBLE_PHASE)).scale(TREMBLE);
        }
        if (!hunk.climbing(time)) {
            return Vec3.ZERO;
        }
        double strength = TREMBLE * (1 - hunk.risenShareAt(time));
        return new Vec3(sway(time, 0), 0, sway(time, TREMBLE_PHASE)).scale(strength);
    }

    private static double sway(double time, double phase) {
        return HALF * (Math.sin(time * TREMBLE_FAST + phase) + Math.sin(time * TREMBLE_SLOW + phase));
    }

    private static MovingBlockRenderState movingBlock(ClientLevel level, BlockHunk.HunkBlock block, BlockPos lightAt,
                                                      BlockPos variantCell) {
        MovingBlockRenderState moving = new MovingBlockRenderState();
        // Seeded by its home cell, so its texture's variant holds still as it moves.
        moving.randomSeedPos = variantCell;
        moving.blockPos = lightAt;
        moving.blockState = block.state();
        moving.biome = level.getBiome(lightAt);
        moving.cardinalLighting = level.cardinalLighting();
        moving.lightEngine = level.getLightEngine();
        return moving;
    }

    /**
     * Draws a placed hunk's fading blocks, each tesselated as the world
     * tesselates the block in its cell but in the variant it wore while carried,
     * its alpha the fade's, swollen a hair so it sits over the world's block.
     *
     * @param state     the render state
     * @param poseStack the pose stack, at the hunk
     * @param collector the submit collector
     */
    private static void submitFaded(HunkRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        ClientLevel level = Minecraft.getInstance().level;
        if (state.faded.isEmpty() || level == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ModelBlockRenderer tesselator = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true,
                mc.getBlockColors());
        int fade = ARGB.color(Math.round(state.fade * OPAQUE), OPAQUE, OPAQUE, OPAQUE);
        for (Faded faded : state.faded) {
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(faded.state());
            poseStack.pushPose();
            poseStack.translate(faded.offset().x + HALF, faded.offset().y + HALF, faded.offset().z + HALF);
            poseStack.scale(LINGER_SWELL, LINGER_SWELL, LINGER_SWELL);
            poseStack.translate(-HALF, -HALF, -HALF);
            collector.submitCustomGeometry(poseStack, GooSubmitter.movingBlockTranslucent(), (pose, buffer) ->
                    tesselator.tesselateBlock((x, y, z, quad, instance) -> {
                        instance.multiplyColor(fade);
                        buffer.putBakedQuad(pose, quad, instance);
                    }, 0f, 0f, 0f, level, faded.cell(), faded.state(), model, faded.seed()));
            poseStack.popPose();
        }
    }

    /**
     * Draws the Mortar riding a held piece, each rider tesselated as the
     * world tesselates Mortar on that block, in the cell beside its face.
     * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
     *
     * @param state     the render state
     * @param poseStack the pose stack, at the hunk
     * @param collector the submit collector
     */
    private static void submitRiding(HunkRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (state.riding.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ModelBlockRenderer tesselator = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true,
                mc.getBlockColors());
        for (RidingDraw riding : state.riding) {
            RidingMortarView view = riding.view();
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(view.mortar());
            poseStack.pushPose();
            poseStack.translate(riding.offset().x, riding.offset().y, riding.offset().z);
            collector.submitCustomGeometry(poseStack, GooSubmitter.movingBlockTranslucent(), (pose, buffer) ->
                    tesselator.tesselateBlock((x, y, z, quad, instance) -> buffer.putBakedQuad(pose, quad, instance),
                            0f, 0f, 0f, view, view.cell(), view.mortar(), model, view.mortar().getSeed(view.cell())));
            poseStack.popPose();
        }
    }

    @Override
    public void submit(HunkRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        submitFaded(state, poseStack, collector);
        submitRiding(state, poseStack, collector);
        for (int index = 0; index < state.blocks.size(); index++) {
            Vec3 offset = state.offsets.get(index);
            poseStack.pushPose();
            poseStack.translate(offset.x + HALF, offset.y + HALF, offset.z + HALF);
            poseStack.scale(state.swell, state.swell, state.swell);
            poseStack.translate(-HALF, -HALF, -HALF);
            collector.submitMovingBlock(poseStack, state.blocks.get(index), NO_OUTLINE);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
