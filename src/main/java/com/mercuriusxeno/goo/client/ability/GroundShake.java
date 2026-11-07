package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.FlattenStep;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground shake bore and flatten play while they run: each solid block
 * within a radius of the working point is drawn again over itself, swollen a
 * hair and jittered in world space by a sum of fast sines, so the ground
 * itself trembles and sheds chips of itself while the camera stays still.
 * decision bore-shakes-the-blocks-around-the-tunnel-and-never-the-camera
 * decision flatten-plays-bores-ground-shake
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GroundShake {

    /** The farthest a block at the working point moves on any one axis, in blocks. */
    static final double AMPLITUDE = 0.04;
    /** How far from the tunnel's line or the swath's middle the ground shakes, in blocks. */
    static final double RADIUS = 2.25;
    /** The most blocks one frame redraws, so a wide open shake stays cheap. */
    private static final int MOST_SHAKEN = 384;
    /** Crumbs a tick shakes loose from the shaking blocks. */
    private static final int CRUMBS_PER_TICK = 3;
    /** How fast a crumb leaves its block, in blocks a tick. */
    private static final double CRUMB_SPEED = 0.06;
    /** How far past a face a crumb starts, so it never spawns inside the block. */
    private static final double CRUMB_LIFT = 0.05;
    /** Each axis's two sine rates, in radians per tick; unrelated rates keep the shake from repeating. */
    private static final double[] FAST = {2.9, 3.3, 2.5};
    private static final double[] SLOW = {1.7, 2.1, 1.3};
    /** Each axis's phase step by a block's hash, so neighbors tremble out of step. */
    private static final double[] PHASE_STEP = {0.37, 0.61, 0.83};
    private static final double HALF = 0.5;
    /** A swell grows a block on both sides of its center. */
    private static final double BOTH_SIDES = 2;
    private static final int X_AXIS = 0;
    private static final int Y_AXIS = 1;
    private static final int Z_AXIS = 2;
    /** Flatten's swath reaches this many layers out past the layer touching its face. */
    private static final int SWATH_LAYERS_PAST_THE_FIRST = 2;

    /** The blocks the last frame shook, which the tick sheds dust from. */
    private static final List<BlockPos> shaken = new ArrayList<>();

    private GroundShake() {
    }

    /**
     * Where the ground shakes: every block within a radius of a line, the bore's
     * tunnel or the flatten swath's spine.
     *
     * @param from   the line's start
     * @param to     the line's end
     * @param radius how far from the line the ground shakes, in blocks
     */
    record Zone(Vec3 from, Vec3 to, double radius) {

        /**
         * How far a point lies from the line.
         *
         * @param point the point
         * @return the distance in blocks
         */
        double distanceTo(Vec3 point) {
            Vec3 line = to.subtract(from);
            double lengthSqr = line.lengthSqr();
            double along = lengthSqr == 0 ? 0 : Math.clamp(point.subtract(from).dot(line) / lengthSqr, 0, 1);
            return point.distanceTo(from.add(line.scale(along)));
        }

        /**
         * How strongly a block shakes: full on the line, fading to still at the radius.
         *
         * @param pos the block
         * @return the strength, 0 to 1
         */
        double strengthAt(BlockPos pos) {
            return Math.max(0, 1 - distanceTo(Vec3.atCenterOf(pos)) / radius);
        }

        /**
         * The lowest corner of the box holding every block the zone can shake.
         *
         * @return the block
         */
        BlockPos low() {
            return BlockPos.containing(Math.min(from.x, to.x) - radius, Math.min(from.y, to.y) - radius,
                    Math.min(from.z, to.z) - radius);
        }

        /**
         * The highest corner of the box holding every block the zone can shake.
         *
         * @return the block
         */
        BlockPos high() {
            return BlockPos.containing(Math.max(from.x, to.x) + radius, Math.max(from.y, to.y) + radius,
                    Math.max(from.z, to.z) + radius);
        }
    }

    /**
     * Where a bore shakes the ground: around its tunnel, from the eye out to its reach.
     *
     * @param eye   the boring player's eye
     * @param look  the player's view vector
     * @param range the bore's reach in blocks
     * @return the zone
     */
    static Zone boreZone(Vec3 eye, Vec3 look, double range) {
        return new Zone(eye, eye.add(look.normalize().scale(range)), RADIUS);
    }

    /**
     * Where a flatten shakes the ground: around the swath it breaks, from the
     * layer touching the face out to its last layer, the face's own ground with it.
     *
     * @param plane the face the hold began on
     * @param aimed the block the cursor rests on
     * @return the zone
     */
    static Zone flattenZone(ChannelAim.FacePlane plane, BlockPos aimed) {
        BlockPos first = plane.cellOutFrom(aimed);
        BlockPos last = first.relative(plane.face(), SWATH_LAYERS_PAST_THE_FIRST);
        return new Zone(Vec3.atCenterOf(first), Vec3.atCenterOf(last), RADIUS);
    }

    /**
     * A block's offset from its cell at a moment: a sum of two sines on each
     * axis, out of step with its neighbors, scaled by how near the working
     * point it stands; zero past the radius.
     *
     * @param pos  the block
     * @param zone where the ground shakes
     * @param time the game time including the partial tick
     * @return the offset in blocks, each axis within the amplitude
     */
    static Vec3 offsetAt(BlockPos pos, Zone zone, float time) {
        double strength = zone.strengthAt(pos);
        if (strength == 0) {
            return Vec3.ZERO;
        }
        int hash = Long.hashCode(pos.asLong());
        return new Vec3(axis(time, X_AXIS, hash), axis(time, Y_AXIS, hash), axis(time, Z_AXIS, hash)).scale(strength);
    }

    private static double axis(float time, int axis, int hash) {
        double phase = hash * PHASE_STEP[axis];
        return AMPLITUDE * HALF * (Math.sin(time * FAST[axis] + phase) + Math.sin(time * SLOW[axis] + phase));
    }

    /**
     * The farthest a shaken block's face stands out past its cell: its swell
     * and its jitter, both at full strength on the working point.
     *
     * @return the distance in blocks
     */
    static double highestFaceRise() {
        return AMPLITUDE + AMPLITUDE;
    }

    /**
     * Whether an ability shakes the ground: it bores or flattens and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the ground shakes
     */
    static boolean shakes(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && ability.behaviors().stream()
                .anyMatch(step -> step instanceof BoreStep || step instanceof FlattenStep);
    }

    /**
     * Where the local player's glove shakes the ground now.
     *
     * @param player      the local player
     * @param partialTick the frame's partial tick
     * @return the zone, or null while no bore or flatten runs
     */
    private static @Nullable Zone runningZone(LocalPlayer player, float partialTick) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        if (!shakes(ability, GloveUseTracker.runsHeld(player))) {
            return null;
        }
        Vec3 eye = player.getEyePosition(partialTick);
        if (ability.behaviors().stream().anyMatch(BoreStep.class::isInstance)) {
            return boreZone(eye, player.getViewVector(partialTick), ability.delivery().range());
        }
        ChannelAim.FacePlane plane = GloveUseTracker.pressPlane();
        return plane == null ? null
                : flattenZone(plane, new ChannelAim(GloveThrowSender.cursorPoint(player), plane).aimedBlock(eye));
    }

    /**
     * Redraws the shaking blocks after the translucent blocks while a bore or
     * a flatten runs.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        shaken.clear();
        if (player == null || level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Zone zone = runningZone(player, partialTick);
        if (zone == null) {
            return;
        }
        collectShaken(level, zone);
        if (!shaken.isEmpty()) {
            drawShaken(mc, event.getPoseStack(), zone, level.getGameTime() + partialTick);
        }
    }

    private static void collectShaken(ClientLevel level, Zone zone) {
        for (BlockPos pos : BlockPos.betweenClosed(zone.low(), zone.high())) {
            if (shaken.size() >= MOST_SHAKEN) {
                return;
            }
            if (zone.strengthAt(pos) > 0 && level.getBlockState(pos).getRenderShape() == RenderShape.MODEL) {
                shaken.add(pos.immutable());
            }
        }
    }

    /**
     * Draws each shaking block's own model over itself, tesselated as the
     * world tesselates it so its light and culled faces match, swollen by as
     * much as it can move so the copy always stands in front of the still
     * block, and offset by the shake.
     *
     * @param mc        the client
     * @param poseStack the level's pose stack, camera relative
     * @param zone      where the ground shakes
     * @param time      the game time including the partial tick
     */
    private static void drawShaken(Minecraft mc, PoseStack poseStack, Zone zone, float time) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType blocks = renderType();
        VertexConsumer consumer = buffers.getBuffer(blocks);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        ModelBlockRenderer tesselator = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true,
                mc.getBlockColors());
        for (BlockPos pos : shaken) {
            BlockState state = mc.level.getBlockState(pos);
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            Vec3 offset = offsetAt(pos, zone, time);
            float swell = (float) (1 + BOTH_SIDES * AMPLITUDE * zone.strengthAt(pos));
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x + offset.x + HALF, pos.getY() - camera.y + offset.y + HALF,
                    pos.getZ() - camera.z + offset.z + HALF);
            poseStack.scale(swell, swell, swell);
            poseStack.translate(-HALF, -HALF, -HALF);
            PoseStack.Pose pose = poseStack.last();
            tesselator.tesselateBlock((x, y, z, quad, instance) -> consumer.putBakedQuad(pose, quad, instance),
                    0f, 0f, 0f, mc.level, pos, state, model, state.getSeed(pos));
            poseStack.popPose();
        }
        buffers.endBatch(blocks);
    }

    /**
     * The render type the shaking blocks draw through.
     *
     * @return the render type
     */
    static RenderType renderType() {
        return GooSubmitter.movingBlockCutout();
    }

    /**
     * Sheds crumbs of the shaking blocks each tick the ground shakes.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || shaken.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int index = 0; index < CRUMBS_PER_TICK; index++) {
            shedCrumbs(level, random, shaken.get(random.nextInt(shaken.size())));
        }
    }

    /**
     * Sheds a shaking block's crumbs, chips of its own texture: one hopping
     * up off an open top, and one dropping from an open underside.
     *
     * @param level  the client level
     * @param random the level's random
     * @param pos    the shaking block
     */
    private static void shedCrumbs(ClientLevel level, RandomSource random, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        if (level.getBlockState(pos.above()).isAir()) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + random.nextDouble(), pos.getY() + 1 + CRUMB_LIFT, pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - HALF) * CRUMB_SPEED, CRUMB_SPEED, (random.nextDouble() - HALF) * CRUMB_SPEED);
        }
        if (level.getBlockState(pos.below()).isAir()) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + random.nextDouble(), pos.getY() - CRUMB_LIFT, pos.getZ() + random.nextDouble(),
                    0, 0, 0);
        }
    }
}
