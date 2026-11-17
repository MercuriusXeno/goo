package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.crystal.KnifeHoming;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Knives' thrown knives in flight on the server, each flown a tick at a time
 * since its mob moves: each tick it turns toward where its mob stands now, a
 * knife meeting a block sticks there, a knife reaching its mob runs the
 * ability's program on it, and a knife whose mob is gone flies on to where the
 * mob last stood and shatters there, its price spent, as the operator ruled.
 * decision knives-home-on-their-painted-mob
 * decision knives-leave-the-blob-at-a-snowballs-speed
 */
public final class HomingKnives {

    private final List<Flight> flying = new ArrayList<>();

    /** One knife in flight, flown a tick at a time. */
    private static final class Flight {
        private final ServerPlayer thrower;
        private final ResourceKey<GooTypeDefinition> gooType;
        private final String abilityId;
        private final int mobId;
        private Vec3 position;
        private Vec3 lastKnown;
        private int ticks;

        Flight(ServerPlayer thrower, ResourceKey<GooTypeDefinition> gooType, String abilityId, LivingEntity mob,
               Vec3 start) {
            this.thrower = thrower;
            this.gooType = gooType;
            this.abilityId = abilityId;
            this.mobId = mob.getId();
            this.position = start;
            this.lastKnown = mob.getBoundingBox().getCenter();
        }

        Vec3 lastKnown() {
            return lastKnown;
        }

        /**
         * Flies the knife one tick toward where its mob stands now, or where
         * the mob last stood once it is gone.
         *
         * @param now the server tick
         * @return true once its flight has ended
         */
        boolean fly(int now) {
            if (thrower.isRemoved()) {
                return true;
            }
            LivingEntity mob = standingMob(thrower.level(), mobId);
            if (mob != null) {
                lastKnown = mob.getBoundingBox().getCenter();
            }
            Vec3 next = position.add(KnifeHoming.velocityToward(position, lastKnown));
            if (meetsABlock(next)) {
                return true;
            }
            if (mob != null && KnifeHoming.arrives(position, next, mob.getBoundingBox())) {
                strike(mob, next, now);
                return true;
            }
            position = next;
            ticks++;
            return spent(mob == null);
        }

        private boolean meetsABlock(Vec3 next) {
            BlockHitResult wall = thrower.level().clip(new ClipContext(position, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, thrower));
            return wall.getType() == HitResult.Type.BLOCK;
        }

        // A knife whose mob is gone is spent where the mob last stood; any knife is spent once its flight runs out.
        private boolean spent(boolean mobGone) {
            return mobGone && position.equals(lastKnown) || ticks >= KnifeHoming.MOST_FLIGHT_TICKS;
        }

        /**
         * Lands the ability on the mob the knife reached, aimed along its last step.
         *
         * @param mob the mob it reached
         * @param at  where its last step stopped
         * @param now the server tick
         */
        private void strike(LivingEntity mob, Vec3 at, int now) {
            ServerLevel level = thrower.level();
            Vec3 heading = at.subtract(position);
            GooServerState.of(level.getServer()).gooEffects().enqueue(new GooEffectScheduler.PendingEffect(now,
                    level, thrower, gooType, mob.getId(), mob.blockPosition(), null, abilityId,
                    new GooEffectScheduler.Aim(position, heading.lengthSqr() > 0 ? heading.normalize() : heading)));
        }
    }

    /**
     * Sends a knife from its fan slot at its mob, and tells the players watching.
     *
     * @param thrower   the throwing player
     * @param gooType   the ability's goo type
     * @param abilityId the ability the knife lands
     * @param mob       the knife's mob
     * @param start     the knife's fan slot
     */
    public void launch(ServerPlayer thrower, ResourceKey<GooTypeDefinition> gooType, String abilityId,
                       LivingEntity mob, Vec3 start) {
        Flight flight = new Flight(thrower, gooType, abilityId, mob, start);
        flying.add(flight);
        EntityVisuals.sendToWatchers(thrower, new GlassKnifePayload(start, mob.getId(), flight.lastKnown()));
    }

    /**
     * @return whether a knife is in flight
     */
    public boolean hasPending() {
        return !flying.isEmpty();
    }

    /**
     * Flies every knife one tick.
     *
     * @param now the server tick
     */
    public void tick(int now) {
        flying.removeIf(flight -> flight.fly(now));
    }

    private static @Nullable LivingEntity standingMob(ServerLevel level, int mobId) {
        return level.getEntity(mobId) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** Drops every knife in flight, as a server stop does. */
    public void clear() {
        flying.clear();
    }
}
