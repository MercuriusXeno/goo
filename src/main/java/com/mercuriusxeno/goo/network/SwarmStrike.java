package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * A mob-first stream's swarm on the server: each tick it tells the watching
 * clients where the gnats converge, the mob it chases or else the block
 * under the crosshair, and holds the bite back until the gnats have had time
 * to fly to the mob (decision decay-gnats-degrade-each-block-once).
 */
final class SwarmStrike {

    private SwarmStrike() {
    }

    /**
     * Tells the caster's watchers to launch this tick's gnats, each at its
     * own speed alone.
     * glove-stream-motes-carry-the-players-travel-velocity
     *
     * @param player    the streaming player
     * @param glove     the glove hand the gnats leave from
     * @param aim       the heading from the glove onto the crosshair
     * @param delivery  the stream's delivery, whose cone the gnats fan across
     * @param chased    the mob the swarm chases, alone, or none
     * @param crosshair the block the crosshair lands on, or a miss at the stream's reach
     */
    static void send(ServerPlayer player, Vec3 glove, Vec3 aim, Delivery delivery, List<LivingEntity> chased,
                     BlockHitResult crosshair) {
        int preyId = chased.isEmpty() ? GnatSeekPayload.NO_PREY : chased.getFirst().getId();
        // cone-style-abilities-are-reviewed-for-consistency: the glove's cast, its round cone out to the living reach
        EntityVisuals.sendToWatchers(player, payloadOf(new SwarmCast(player.getId(), glove, aim,
                GooStreamHandler.gloveShapeOf(delivery), delivery.livingReach()), preyId,
                SwarmMotes.moteTarget(crosshair)));
    }

    /**
     * A cast of the swarm: who casts it, from where, along what heading, through what cone and how far.
     *
     * @param casterId the streaming player
     * @param glove    the glove hand the gnats leave from
     * @param aim      the heading from the glove onto the crosshair
     * @param shape    the cone the gnats fan across
     * @param reach    how far from the glove the gnats seek
     */
    record SwarmCast(int casterId, Vec3 glove, Vec3 aim, ConeShape shape, double reach) {
    }

    /**
     * A tick of the swarm as its watchers are told it: the cast's shape and reach, and where the gnats converge.
     * decision cone-style-abilities-are-reviewed-for-consistency
     *
     * @param cast   the swarm's cast
     * @param preyId the mob the swarm chases, or {@link GnatSeekPayload#NO_PREY}
     * @param block  the box the gnats converge on with no prey
     * @return the payload
     */
    static GnatSeekPayload payloadOf(SwarmCast cast, int preyId, AABB block) {
        return new GnatSeekPayload(cast.casterId(), cast.glove(), cast.aim(), cast.shape(), cast.reach(), preyId,
                new Vec3(block.minX, block.minY, block.minZ), new Vec3(block.maxX, block.maxY, block.maxZ));
    }

    /**
     * The mob the swarm bites this tick: the one it chases once its gnats
     * have reached it, none before.
     *
     * @param player the streaming player
     * @param glove  the glove hand the gnats leave from
     * @param chased the mob the swarm chases, alone, or none
     * @param held   the hold's tick count
     * @return the mob bitten, alone, or none
     */
    static List<LivingEntity> reached(ServerPlayer player, Vec3 glove, List<LivingEntity> chased, int held) {
        if (chased.isEmpty()) {
            return chased;
        }
        LivingEntity prey = chased.getFirst();
        HoldMarks marks = GooServerState.of(player.level().getServer()).streamHolds().marks(player.getUUID());
        return marks.swarmReached(prey.getId(), held, glove.distanceTo(prey.getBoundingBox().getCenter()))
                ? chased : List.of();
    }
}
