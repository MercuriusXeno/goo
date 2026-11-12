package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Where Bore's drawn vortex makes contact with a breakable block, a particle
 * of the block spits out of the face it meets each tick the bore runs,
 * scattered across the vortex's width there, so the vortex reads as tearing
 * into the block while the block clips it. The contact is traced along the
 * drawn vortex's own axis, from where it is held below the line of sight.
 * decision bore-shows-a-big-drill-from-the-glove
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreContactSpray {

    /** Particles spat a tick where the vortex meets a breakable block. */
    static final int SPITS_PER_TICK = 1;
    /** How fast a particle leaves the face, in blocks a tick. */
    static final double SPIT_SPEED = 0.12;
    /** How fast a particle scatters along the face, at most, in blocks a tick. */
    static final double SPIT_SCATTER = 0.08;
    /** How far out from the face a particle starts, so it never starts inside the block. */
    private static final double OFF_THE_FACE = 0.05;
    private static final double HALF = 0.5;
    private static final double BOTH_WAYS = 2;

    private BoreContactSpray() {
    }

    /**
     * A spat particle's velocity: out of the face it meets, scattered along
     * the face, never back into the block.
     *
     * @param face the face the vortex meets
     * @param x    the scatter along the world's x, -1 to 1
     * @param y    the scatter along the world's y, -1 to 1
     * @param z    the scatter along the world's z, -1 to 1
     * @return the velocity, in blocks a tick
     */
    static Vec3 spitVelocity(Direction face, double x, double y, double z) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 scatter = new Vec3(x, y, z).scale(SPIT_SCATTER);
        Vec3 along = scatter.subtract(normal.scale(scatter.dot(normal)));
        return normal.scale(SPIT_SPEED).add(along);
    }

    /**
     * The line the contact is traced along: the drawn vortex's axis, from
     * where it is held to its far end.
     *
     * @param eye   the eye the vortex is drawn from, in world space
     * @param look  the look's unit vector
     * @param yaw   the look's yaw in degrees
     * @param reach the bore's reach
     * @param near  the vortex's radius at the eye
     * @param far   the vortex's radius at the reach
     * @return the line's start and end, in world space
     */
    static Vec3[] contactLine(Vec3 eye, Vec3 look, float yaw, double reach, double near, double far) {
        ConeSections.Volume drawn = BoreDrill.volume(eye, look, yaw, reach, near, far);
        return new Vec3[] {drawn.apex(), drawn.apex().add(drawn.axis().scale(drawn.range()))};
    }

    /**
     * The vortex's radius at a depth along the line of sight, from the bore
     * step's near and far radii, which the spit scatters across.
     *
     * @param depth the depth along the line of sight, in blocks
     * @param reach the bore's reach
     * @param near  the vortex's radius at the eye
     * @param far   the vortex's radius at the reach
     * @return the radius, in blocks
     */
    static double widthAt(double depth, double reach, double near, double far) {
        return near + (far - near) * Math.clamp(depth / reach, 0, 1);
    }

    /**
     * Spits the tick's particles while the local player's bore runs and its
     * line of sight meets a breakable block within reach.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            return;
        }
        ClientAbility bore = BoreDrill.runningBore(player);
        BoreStep step = bore == null ? null : BoreDrill.stepOf(bore).orElse(null);
        if (step != null) {
            spitAtContact(level, player, step, bore.delivery().range());
        }
    }

    /**
     * Spits particles where the line of sight meets a block within reach,
     * where that block is one the bore breaks.
     *
     * @param level  the client level
     * @param player the local player
     * @param step   the running bore's step
     * @param reach  the bore's reach
     */
    private static void spitAtContact(ClientLevel level, LocalPlayer player, BoreStep step, double reach) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.options.getCameraType().isFirstPerson() ? mc.gameRenderer.getMainCamera().position()
                : player.getEyePosition();
        Vec3[] line = contactLine(eye, player.getViewVector(1f), player.getYRot(), reach, step.vortexNearRadius(),
                step.vortexFarRadius());
        BlockHitResult hit = level.clip(new ClipContext(line[0], line[1], ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (hit.getType() == HitResult.Type.MISS || !state.is(step.breaks())) {
            return;
        }
        spit(level, state, hit, widthAt(hit.getLocation().distanceTo(eye), reach, step.vortexNearRadius(),
                step.vortexFarRadius()));
    }

    private static void spit(ClientLevel level, BlockState state, BlockHitResult hit, double width) {
        RandomSource random = level.getRandom();
        Direction face = hit.getDirection();
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        for (int spit = 0; spit < SPITS_PER_TICK; spit++) {
            Vec3 across = new Vec3(signed(random), signed(random), signed(random)).scale(width);
            Vec3 onFace = across.subtract(normal.scale(across.dot(normal)));
            Vec3 at = hit.getLocation().add(onFace).add(normal.scale(OFF_THE_FACE));
            Vec3 velocity = spitVelocity(face, signed(random), signed(random), signed(random));
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    private static double signed(RandomSource random) {
        return (random.nextDouble() - HALF) * BOTH_WAYS;
    }
}
