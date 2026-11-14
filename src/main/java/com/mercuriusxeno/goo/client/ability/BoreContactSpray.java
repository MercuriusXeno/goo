package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
 * Where Bore's drawn vortex makes contact with a breakable block, particles of
 * the block spew from round the outside of its cone there each tick the bore
 * runs, each flying back toward the holder's face and out from its place on
 * the ring,
 * so the vortex reads as tearing into the block while the block clips it.
 * The contact is traced along the drawn vortex's own axis, from where it is
 * held below the line of sight.
 * decision bore-shows-a-big-drill-from-the-glove
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreContactSpray {

    /** Particles spewed a tick where the vortex meets a breakable block. */
    static final int SPITS_PER_TICK = 8;
    /** How fast a particle flies back toward the holder, in blocks a tick. */
    static final double BACK_SPEED = 0.28;
    /** How fast a particle fans out round the drill's axis, at most, in blocks a tick. */
    static final double RING_SPEED = 0.18;
    /** How much each particle's speed back varies either way, as a share. */
    private static final double SPEED_VARY = 0.3;
    /** How far back toward the holder a particle starts, so it never starts inside the block. */
    private static final double BACK_OFF = 0.1;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final double BOTH_WAYS = 2;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_EAST = new Vec3(1, 0, 0);
    private static final double NEARLY_UPRIGHT = 0.99;

    private BoreContactSpray() {
    }

    /**
     * A spewed particle's velocity: back toward the holder from the drill's
     * point, fanned out round the drill's axis at a place on a ring, so the
     * spew flies back in a circle rather than a line.
     *
     * @param towardHolder the unit direction from the drill's point back toward the holder
     * @param around       where round the ring the particle fans out, 0 to 1
     * @param flare        how far it fans out, 0 to 1
     * @return the velocity, in blocks a tick
     */
    static Vec3 spewVelocity(Vec3 towardHolder, double around, double flare) {
        Vec3 ring = ringAt(towardHolder, around);
        return towardHolder.scale(BACK_SPEED).add(ring.scale(RING_SPEED * (HALF + HALF * flare)));
    }

    /**
     * Where a spewed particle starts: on the outside of the drill's cone where
     * it meets the block, at a place round its axis, a little back toward the
     * holder so it never starts inside the block.
     *
     * @param contact      the point the drill's axis meets the block
     * @param towardHolder the unit direction from the drill's point back toward the holder
     * @param around       where round the cone the particle starts, 0 to 1
     * @param radius       the cone's radius where it meets the block
     * @return the start, in world space
     */
    static Vec3 spewOrigin(Vec3 contact, Vec3 towardHolder, double around, double radius) {
        return contact.add(ringAt(towardHolder, around).scale(radius)).add(towardHolder.scale(BACK_OFF));
    }

    /**
     * The unit direction out from the drill's axis at a place round it.
     *
     * @param towardHolder the unit direction along the axis back toward the holder
     * @param around       the place round the axis, 0 to 1
     * @return the direction
     */
    private static Vec3 ringAt(Vec3 towardHolder, double around) {
        Vec3 reference = Math.abs(towardHolder.dot(WORLD_UP)) > NEARLY_UPRIGHT ? WORLD_EAST : WORLD_UP;
        Vec3 side = towardHolder.cross(reference).normalize();
        Vec3 up = side.cross(towardHolder);
        double angle = around * TWO_PI;
        return side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
    }

    /**
     * The line the contact is traced along: the drawn vortex's axis, from
     * where it is held to its far end.
     *
     * @param eye   the eye the vortex is drawn from, in world space
     * @param look  the look's unit vector
     * @param yaw   the look's yaw in degrees
     * @param reach       the bore's reach
     * @param pointRadius the radius of the point the bore breaks at
     * @return the line's start and end, in world space
     */
    static Vec3[] contactLine(Vec3 eye, Vec3 look, float yaw, double reach, double pointRadius) {
        ConeSections.Volume drawn = BoreDrill.volume(eye, look, yaw, reach, pointRadius);
        return new Vec3[] {drawn.apex(), drawn.apex().add(drawn.axis().scale(drawn.range()))};
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
            // bore-reaches-as-far-as-your-block-reach
            spitAtContact(level, player, step, bore.delivery().reachingTo(player.blockInteractionRange()).range());
        }
    }

    /**
     * Spews particles where the drawn vortex meets a block within reach,
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
        ConeSections.Volume drawn = BoreDrill.volume(eye, player.getViewVector(1f), player.getYRot(), reach,
                step.pointRadius());
        Vec3[] line = contactLine(eye, player.getViewVector(1f), player.getYRot(), reach, step.pointRadius());
        BlockHitResult hit = level.clip(new ClipContext(line[0], line[1], ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (hit.getType() == HitResult.Type.MISS || !state.is(step.breaks())) {
            return;
        }
        double radius = drawn.radiusAt().applyAsDouble(hit.getLocation().distanceTo(drawn.apex()));
        spew(level, state, hit.getLocation(), drawn.axis().reverse(), radius);
    }

    private static void spew(ClientLevel level, BlockState state, Vec3 contact, Vec3 towardHolder, double radius) {
        RandomSource random = level.getRandom();
        for (int spit = 0; spit < SPITS_PER_TICK; spit++) {
            double around = random.nextDouble();
            Vec3 at = spewOrigin(contact, towardHolder, around, radius);
            double speed = 1 + SPEED_VARY * signed(random);
            Vec3 velocity = spewVelocity(towardHolder, around, random.nextDouble()).scale(speed);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    private static double signed(RandomSource random) {
        return (random.nextDouble() - HALF) * BOTH_WAYS;
    }
}
