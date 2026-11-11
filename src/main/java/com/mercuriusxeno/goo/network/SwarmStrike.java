package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.registry.GooServerState;
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
     * Tells the caster's watchers to launch this tick's gnats, each carrying
     * the caster's travel velocity.
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
        AABB block = SwarmMotes.moteTarget(crosshair);
        EntityVisuals.sendToWatchers(player, new GnatSeekPayload(player.getId(), glove, aim, delivery.coneDegrees(),
                preyId, new Vec3(block.minX, block.minY, block.minZ), new Vec3(block.maxX, block.maxY, block.maxZ),
                GooStreamHandler.travelOf(player)));
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
