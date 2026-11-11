package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.FlattenStep;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TerrakinesisStep;
import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The ground shake bore and flatten play while they run: each block the
 * ability is progressing toward broken is drawn again over itself, for as
 * long as it gains progress, swollen a hair and jittered gently in world
 * space by a sum of fast sines, so the cracking blocks tremble and shed chips
 * of themselves while the ground around them and the camera stay still.
 * decision bore-shakes-the-blocks-around-the-tunnel-and-never-the-camera
 * decision flatten-plays-bores-ground-shake
 * decision only-the-blocks-about-to-break-shake-briefly-and-gently
 *
 * A winding-up Terrakinesis shakes exactly the cells it is about to tear
 * free, read on the client from the same rule the server lifts by.
 * decision terrakinesis-shakes-the-blocks-before-the-tear
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GroundShake {

    /**
     * The farthest a shaking block moves on any one axis, in blocks.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     */
    static final double AMPLITUDE = 0.02;
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

    /** The blocks the last frame shook, which the tick sheds dust from. */
    private static final List<BlockPos> shaken = new ArrayList<>();

    private GroundShake() {
    }

    /** Where the ground shakes, and how strongly each block in it does. */
    interface Zone {

        /**
         * How strongly a block shakes.
         *
         * @param pos the block
         * @return the strength, 0 for still to 1 for full
         */
        double strengthAt(BlockPos pos);

        /**
         * The lowest corner of the box holding every block the zone can shake.
         *
         * @return the block
         */
        BlockPos low();

        /**
         * The highest corner of the box holding every block the zone can shake.
         *
         * @return the block
         */
        BlockPos high();
    }

    /**
     * Exactly a set of cells, each shaking at full strength, and nothing beside them.
     *
     * @param cells the shaking cells
     */
    record Cells(Set<BlockPos> cells) implements Zone {

        @Override
        public double strengthAt(BlockPos pos) {
            return cells.contains(pos) ? 1 : 0;
        }

        @Override
        public BlockPos low() {
            return new BlockPos(cells.stream().mapToInt(BlockPos::getX).min().orElse(0),
                    cells.stream().mapToInt(BlockPos::getY).min().orElse(0),
                    cells.stream().mapToInt(BlockPos::getZ).min().orElse(0));
        }

        @Override
        public BlockPos high() {
            return new BlockPos(cells.stream().mapToInt(BlockPos::getX).max().orElse(0),
                    cells.stream().mapToInt(BlockPos::getY).max().orElse(0),
                    cells.stream().mapToInt(BlockPos::getZ).max().orElse(0));
        }
    }

    /**
     * Where a winding-up Terrakinesis shakes the ground: the cells it would lift.
     *
     * @param cells the cells the hunk would lift
     * @return the zone
     */
    static Zone cellsZone(Collection<BlockPos> cells) {
        return new Cells(Set.copyOf(cells));
    }

    /**
     * Where a bore shakes the ground: every block on the eye line it is
     * progressing, the pick its tick hands the gradual break, so the shaking
     * blocks are the cracking blocks.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     *
     * @param eye       the boring player's eye
     * @param aim       the stream's aim: its reach along the look
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block the bore breaks
     * @return the zone
     */
    static Zone boreZone(Vec3 eye, ChannelAim aim, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        return cellsZone(BoreStep.progressing(eye, aim, air, breakable));
    }

    /**
     * Where a flatten shakes the ground: every swath block it is
     * progressing, the pick its tick hands the gradual break, so the shaking
     * blocks are the cracking blocks.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     *
     * @param aim       the hold's aim, holding the face the hold began on
     * @param aimed     the block the cursor rests on
     * @param breakable whether a cell holds a block the flatten breaks, in reach
     * @return the zone
     */
    static Zone flattenZone(ChannelAim aim, BlockPos aimed, Predicate<BlockPos> breakable) {
        return cellsZone(FlattenStep.progressing(aim, aimed, breakable));
    }

    /**
     * A block's offset from its cell at a moment: a sum of two sines on each
     * axis, out of step with its neighbors, scaled by the zone's strength
     * there; zero outside the zone.
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
     * Whether an ability shakes the ground while right click holds it: a bore
     * or a flatten the whole hold, a Terrakinesis only while the wind-up the
     * server runs is under way, until the hunk tears free.
     *
     * @param ability   the selected ability's synced copy, or null when none
     * @param useHeld   whether right click holds a live press
     * @param heldTicks the ticks the press has held, or for a Terrakinesis the
     *                  server's wind-up tick, 0 while none runs
     * @return true while the ground shakes
     */
    static boolean shakes(@Nullable ClientAbility ability, boolean useHeld, int heldTicks) {
        return useHeld && ability != null && ability.behaviors().stream().anyMatch(step -> stepShakes(step, heldTicks));
    }

    private static boolean stepShakes(Step step, int heldTicks) {
        if (step instanceof TerrakinesisStep) {
            return heldTicks > 0 && TerrakinesisStep.windsUp(heldTicks);
        }
        return step instanceof BoreStep || step instanceof FlattenStep;
    }

    private static @Nullable TerrakinesisStep terrakinesisOf(@Nullable ClientAbility ability) {
        return ability == null ? null : stepOf(ability, TerrakinesisStep.class);
    }

    /**
     * The ticks the shake runs by: a Terrakinesis by the wind-up the server
     * runs, which restarts within a hold after a miss or a throw, every other
     * ability by the press.
     *
     * @param player       the local player
     * @param terrakinesis the selected Terrakinesis, or null
     * @return the tick count
     */
    private static int shakeTicks(LocalPlayer player, @Nullable TerrakinesisStep terrakinesis) {
        return terrakinesis == null ? GloveUseTracker.channelTicks()
                : player.getData(GooAttachments.TERRAKINESIS_WIND_UP).tickAt(player.level().getGameTime());
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
        TerrakinesisStep terrakinesis = terrakinesisOf(ability);
        if (!shakes(ability, GloveUseTracker.runsHeld(player), shakeTicks(player, terrakinesis))) {
            return null;
        }
        Vec3 eye = player.getEyePosition(partialTick);
        if (terrakinesis != null) {
            return terrakinesisZone(player, terrakinesis, eye, partialTick);
        }
        BoreStep bore = stepOf(ability, BoreStep.class);
        if (bore != null) {
            return liveBoreZone(player, bore, ability, eye, partialTick);
        }
        FlattenStep flatten = stepOf(ability, FlattenStep.class);
        return flatten == null ? null : liveFlattenZone(player, flatten, eye);
    }

    private static <T extends Step> @Nullable T stepOf(ClientAbility ability, Class<T> kind) {
        return ability.behaviors().stream().filter(kind::isInstance).map(kind::cast).findFirst().orElse(null);
    }

    /**
     * The line blocks the local player's bore is progressing, read off the
     * client's level along the aim the server bores by.
     *
     * @param player      the local player
     * @param bore        the selected bore
     * @param ability     the selected ability, whose delivery holds the reach
     * @param eye         the player's eye this frame
     * @param partialTick the frame's partial tick
     * @return the zone
     */
    private static Zone liveBoreZone(LocalPlayer player, BoreStep bore, ClientAbility ability, Vec3 eye,
                                     float partialTick) {
        Level level = player.level();
        ChannelAim aim = new ChannelAim(eye.add(player.getViewVector(partialTick).scale(ability.delivery().range())),
                null, ability.delivery().coneDegrees());
        return boreZone(eye, aim, cell -> level.getBlockState(cell).isAir(),
                cell -> level.getBlockState(cell).is(bore.breaks()));
    }

    /**
     * The swath blocks the local player's flatten is progressing, read off
     * the client's level under the cursor the disc rests on.
     *
     * @param player  the local player
     * @param flatten the selected flatten
     * @param eye     the player's eye this frame
     * @return the zone, or null where the hold began on no face
     */
    private static @Nullable Zone liveFlattenZone(LocalPlayer player, FlattenStep flatten, Vec3 eye) {
        ChannelAim.FacePlane plane = GloveUseTracker.pressPlane();
        if (plane == null) {
            return null;
        }
        Level level = player.level();
        ChannelAim aim = new ChannelAim(GloveThrowSender.cursorPoint(player), plane);
        return flattenZone(aim, aim.aimedBlock(eye), cell -> level.getBlockState(cell).is(flatten.breaks())
                && player.isWithinBlockInteractionRange(cell, PlayerHost.REACH_SLACK));
    }

    /**
     * The cells a winding-up Terrakinesis would tear free: the block the look
     * strikes and its joined neighbors, by the rule the server lifts by.
     *
     * @param player       the local player
     * @param terrakinesis the selected Terrakinesis
     * @param eye          the player's eye this frame
     * @param partialTick  the frame's partial tick
     * @return the zone, or null where the look strikes nothing the hunk lifts
     */
    private static @Nullable Zone terrakinesisZone(LocalPlayer player, TerrakinesisStep terrakinesis, Vec3 eye,
                                                   float partialTick) {
        ClientLevel level = (ClientLevel) player.level();
        Vec3 reach = eye.add(player.getViewVector(partialTick).scale(terrakinesis.range()));
        BlockHitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        Predicate<BlockPos> liftable = cell -> liftable(level, cell);
        if (hit.getType() == HitResult.Type.MISS || !liftable.test(hit.getBlockPos())) {
            return null;
        }
        return cellsZone(new HashSet<>(TerrakinesisStep.hunkCells(hit.getBlockPos(), terrakinesis.radius(),
                liftable)));
    }

    private static boolean liftable(ClientLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        Item item = state.getBlock().asItem();
        GooValue value = item == Items.AIR ? null
                : ClientGooValues.current().lookup(BuiltInRegistries.ITEM.getKey(item));
        return TerrakinesisStep.liftableState(state, state.getDestroySpeed(level, cell), value);
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
            drawShaken(mc, event.getPoseStack(), new Shake(zone, level.getGameTime() + partialTick,
                    event.getLevelRenderState().blockBreakingRenderStates));
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
     * One frame's shake: where the ground shakes, when, and the cracks
     * vanilla draws this frame.
     *
     * @param zone     where the ground shakes
     * @param time     the game time including the partial tick
     * @param breaking the blocks vanilla draws a crack on, each with its stage
     */
    private record Shake(Zone zone, float time, Collection<BlockBreakingRenderState> breaking) {
    }

    /**
     * Draws each shaking block's own model over itself, tesselated as the
     * world tesselates it so its light and culled faces match, swollen by as
     * much as it can move so the copy always stands in front of the still
     * block, and offset by the shake; a block vanilla cracks draws its crack
     * on the copy, since the copy hides the crack on the block's own faces.
     * decision diagnose-and-remove-flattens-particle-artifacting
     *
     * @param mc        the client
     * @param poseStack the level's pose stack, camera relative
     * @param shake     the frame's shake
     */
    private static void drawShaken(Minecraft mc, PoseStack poseStack, Shake shake) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        MultiBufferSource.BufferSource crumbling = mc.renderBuffers().crumblingBufferSource();
        RenderType blocks = renderType();
        VertexConsumer consumer = buffers.getBuffer(blocks);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        ModelBlockRenderer tesselator = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true,
                mc.getBlockColors());
        for (BlockPos pos : shaken) {
            poseStack.pushPose();
            placeCopy(poseStack, pos, camera, shake);
            PoseStack.Pose pose = poseStack.last();
            tesselateCopy(mc, tesselator, pos, consumer, pose);
            crackStageOn(pos, shake.breaking()).ifPresent(stage -> tesselateCopy(mc, tesselator, pos,
                    new SheetedDecalTextureGenerator(crumbling.getBuffer(ModelBakery.DESTROY_TYPES.get(stage)),
                            pose, 1f), pose));
            poseStack.popPose();
        }
        buffers.endBatch(blocks);
        crumbling.endBatch();
    }

    private static void placeCopy(PoseStack poseStack, BlockPos pos, Vec3 camera, Shake shake) {
        Vec3 offset = offsetAt(pos, shake.zone(), shake.time());
        float swell = (float) (1 + BOTH_SIDES * AMPLITUDE * shake.zone().strengthAt(pos));
        poseStack.translate(pos.getX() - camera.x + offset.x + HALF, pos.getY() - camera.y + offset.y + HALF,
                pos.getZ() - camera.z + offset.z + HALF);
        poseStack.scale(swell, swell, swell);
        poseStack.translate(-HALF, -HALF, -HALF);
    }

    private static void tesselateCopy(Minecraft mc, ModelBlockRenderer tesselator, BlockPos pos,
                                      VertexConsumer consumer, PoseStack.Pose pose) {
        BlockState state = mc.level.getBlockState(pos);
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        tesselator.tesselateBlock((x, y, z, quad, instance) -> consumer.putBakedQuad(pose, quad, instance),
                0f, 0f, 0f, mc.level, pos, state, model, state.getSeed(pos));
    }

    /**
     * The crack stage a shaking block's copy draws over itself: the stage
     * vanilla shows on the block, since the copy stands in front of the
     * block and hides the crack vanilla draws on the block's own faces.
     * decision diagnose-and-remove-flattens-particle-artifacting
     *
     * @param pos      the shaking block
     * @param breaking the blocks vanilla draws a crack on this frame, each with its stage
     * @return the stage, or empty where the block shows no crack
     */
    static OptionalInt crackStageOn(BlockPos pos, Collection<BlockBreakingRenderState> breaking) {
        return breaking.stream().filter(state -> state.blockPos().equals(pos))
                .mapToInt(BlockBreakingRenderState::progress).findFirst();
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
