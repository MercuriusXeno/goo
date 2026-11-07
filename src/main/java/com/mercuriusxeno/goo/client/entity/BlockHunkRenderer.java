package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.ability.program.TerrakinesisStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.entity.HoldEase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    /** A hair under a block's top face, inside the cell its top stands in. */
    private static final double JUST_UNDER_THE_TOP = 0.99;
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
    /** More gliding hunks than one player could hold means stale ones are left; they are dropped. */
    private static final int MOST_GLIDES = 16;
    /** Where each held hunk last drew and when, by entity id, so it glides on from there. */
    private static final Map<Integer, Glide> GLIDES = new HashMap<>();

    /**
     * Where a held hunk last drew.
     *
     * @param at   the position it drew at
     * @param time the game time, with the partial tick, it drew at
     */
    private record Glide(Vec3 at, double time) {
    }

    /** The hunk's blocks as this frame draws them, each with its offset from the hunk's drawn position. */
    public static final class HunkRenderState extends EntityRenderState {
        final List<MovingBlockRenderState> blocks = new ArrayList<>();
        final List<Vec3> offsets = new ArrayList<>();
        float swell = WHOLE;
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
        if (!(hunk.level() instanceof ClientLevel level) || hunk.shattering()) {
            return;
        }
        Vec3 lerped = hunk.getPosition(partialTick);
        Vec3 drawnAt = heldOnTheLocalLook(hunk, partialTick);
        Vec3 position = (drawnAt == null ? lerped : drawnAt).add(trembleOf(hunk, level.getGameTime() + partialTick));
        state.swell = hunk.lingers(level.getGameTime()) ? LINGER_SWELL : WHOLE;
        hunk.blocks().stream().filter(block -> block.state().getRenderShape() == RenderShape.MODEL).forEach(block -> {
            Vec3 corner = BlockHunk.cornerOf(position, block);
            state.blocks.add(movingBlock(level, block, corner, BlockHunk.variantCellOf(hunk.home(), block)));
            // The pose stands at the lerped position, so a block drawn elsewhere is offset from it.
            state.offsets.add(corner.subtract(lerped));
        });
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
        if (!hunk.climbing(time)) {
            return Vec3.ZERO;
        }
        double strength = TREMBLE * (1 - hunk.risenShareAt(time));
        return new Vec3(sway(time, 0), 0, sway(time, TREMBLE_PHASE)).scale(strength);
    }

    private static double sway(double time, double phase) {
        return HALF * (Math.sin(time * TREMBLE_FAST + phase) + Math.sin(time * TREMBLE_SLOW + phase));
    }

    private static MovingBlockRenderState movingBlock(ClientLevel level, BlockHunk.HunkBlock block, Vec3 corner,
                                                      BlockPos variantCell) {
        // Lit from the cell its top stands in, so a block breaking out of the ground draws in daylight.
        BlockPos lightAt = BlockPos.containing(corner.add(HALF, JUST_UNDER_THE_TOP, HALF));
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
     * Where a hunk the local player holds draws this frame: gliding (HoldEase)
     * from where it last drew, along the server's rise until it clears its
     * hole and then toward where the player's own look puts it, so it trails
     * the look smoothly and never jumps.
     *
     * @param hunk        the hunk
     * @param partialTick the frame's partial tick
     * @return the position, or null for a hunk the local player does not hold
     */
    private static @Nullable Vec3 heldOnTheLocalLook(BlockHunk hunk, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        TerrakinesisStep terrakinesis = player == null || hunk.holderId() != player.getId() ? null
                : selectedTerrakinesis(player);
        double now = hunk.level().getGameTime() + partialTick;
        Vec3 lerped = hunk.getPosition(partialTick);
        if (terrakinesis == null) {
            GLIDES.remove(hunk.getId());
            return null;
        }
        Vec3 target = !hunk.clearOfHole() ? lerped : onTheLook(hunk, player, terrakinesis, partialTick);
        return glide(hunk.getId(), lerped, target, now);
    }

    /**
     * Glides a held hunk on from where it last drew toward its target.
     *
     * @param id     the hunk's entity id
     * @param lerped where the server last put it, where a new glide starts
     * @param target where it glides toward
     * @param now    the game time, with the partial tick
     * @return where it draws this frame
     */
    private static Vec3 glide(int id, Vec3 lerped, Vec3 target, double now) {
        Glide last = GLIDES.get(id);
        Vec3 drawn = last == null ? lerped
                : HoldEase.toward(last.at(), target, (now - last.time()) * HoldEase.TICK_SECONDS);
        if (GLIDES.size() > MOST_GLIDES) {
            GLIDES.clear();
        }
        GLIDES.put(id, new Glide(drawn, now));
        return drawn;
    }

    private static Vec3 onTheLook(BlockHunk hunk, LocalPlayer player, TerrakinesisStep terrakinesis,
                                  float partialTick) {
        return terrakinesis.holdPosition(player.getEyePosition(partialTick), player.getViewVector(partialTick),
                player.getViewXRot(partialTick), hunk.getBoundingBox().move(hunk.position().reverse()),
                player.getBoundingBox());
    }

    private static @Nullable TerrakinesisStep selectedTerrakinesis(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? null : ability.behaviors().stream().filter(TerrakinesisStep.class::isInstance)
                .map(TerrakinesisStep.class::cast).findFirst().orElse(null);
    }

    @Override
    public void submit(HunkRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        for (int index = 0; index < state.blocks.size(); index++) {
            Vec3 offset = state.offsets.get(index);
            poseStack.pushPose();
            poseStack.translate(offset.x + HALF, offset.y + HALF, offset.z + HALF);
            poseStack.scale(state.swell, state.swell, state.swell);
            poseStack.translate(-HALF, -HALF, -HALF);
            collector.submitMovingBlock(poseStack, state.blocks.get(index));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
