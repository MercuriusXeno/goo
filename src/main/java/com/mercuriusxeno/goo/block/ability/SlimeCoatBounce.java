package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The slime coat's bounce: a landing with jump held launches the lander
 * higher than it arrived and higher than its bounce before, so held jumps
 * ramp up bounce on bounce to a ceiling; without jump held the bounce
 * decays, and sneaking decays it far faster. Each bounce sounds a short boing.
 * bounce-ramps-jump-height-while-jump-is-held
 */
public final class SlimeCoatBounce {

    /** How much higher each held-jump bounce launches than the last or than the fall it caught. */
    static final double RAMP_PER_BOUNCE = 1.25;
    /** The share of its fall a lander keeps on the way back up without jump held. */
    static final double RELEASED_DECAY = 0.7;
    /** The share of its fall a sneaking lander keeps, a far faster decay. */
    static final double SNEAK_DECAY = 0.25;
    /** The fastest launch a ramp reaches, in blocks a tick: a climb of about fourteen blocks. */
    static final double HIGHEST_LAUNCH = 1.8;
    /** A launch below this settles the lander on the coat rather than hopping it. */
    static final double SETTLED_LAUNCH = 0.1;
    /** The sound each bounce makes. */
    static final Identifier BOUNCE_SOUND = GooSoundIds.BOING;
    private static final float BOING_VOLUME = 0.6f;
    private static final float BOING_PITCH = 1.0f;
    private static final float BOING_PITCH_PER_LAUNCH = 0.35f;

    /** The last launch each lander took off the coat, on its side of the game. */
    private static final Map<Entity, Double> LAST_LAUNCH = new WeakHashMap<>();

    private SlimeCoatBounce() {
    }

    /**
     * How fast a lander leaves the coat.
     *
     * @param arrivingFall   how fast it fell onto the coat, in blocks a tick
     * @param previousLaunch how fast it left the coat the bounce before, 0 for none
     * @param jumpHeld       whether the lander holds jump
     * @param sneaking       whether the lander sneaks, which decays the bounce even with jump held
     * @return the upward speed it leaves at, 0 where it settles
     */
    static double launch(double arrivingFall, double previousLaunch, boolean jumpHeld, boolean sneaking) {
        double launch;
        if (sneaking) {
            launch = arrivingFall * SNEAK_DECAY;
        } else if (jumpHeld) {
            launch = Math.min(HIGHEST_LAUNCH, Math.max(arrivingFall, previousLaunch) * RAMP_PER_BOUNCE);
        } else {
            launch = arrivingFall * RELEASED_DECAY;
        }
        return launch < SETTLED_LAUNCH ? 0 : launch;
    }

    /**
     * Bounces an entity that has just landed on the coat, and sounds the boing.
     *
     * @param entity the lander, still moving at the speed it landed with
     */
    static void bounce(Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        double previous = LAST_LAUNCH.getOrDefault(entity, 0.0);
        double launch = launch(-motion.y, previous, jumpHeld(entity), entity.isShiftKeyDown());
        entity.setDeltaMovement(motion.x, launch, motion.z);
        LAST_LAUNCH.put(entity, launch);
        if (launch > 0) {
            // the local player hears its own boing; the server plays it to everyone else
            entity.level().playSound(entity instanceof Player player ? player : null, entity.getX(), entity.getY(),
                    entity.getZ(), GooSounds.BOING.get(), SoundSource.BLOCKS, BOING_VOLUME,
                    BOING_PITCH + (float) launch * BOING_PITCH_PER_LAUNCH);
        }
    }

    private static boolean jumpHeld(Entity entity) {
        return entity instanceof LivingEntity living && living.jumping;
    }
}
