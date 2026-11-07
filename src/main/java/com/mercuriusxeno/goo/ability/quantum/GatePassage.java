package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Going through a linked gate, a portal's or a hole's mouth: the blocks a
 * gate lies on stand open to a body in front of its oval, so the body walks
 * into the opening, and once its middle crosses the gate's face it carries
 * on out of the partner, at the matching point, with its momentum and its
 * view turned through the pair, as the portal gun does. Read on both sides:
 * the server's gates from the saved links and open holes, the client's from
 * what the server told it.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GatePassage {

    /** How far before a gate's face a body may stand and still find its blocks open. */
    private static final double APPROACH = 1.0;
    /** How deep behind a gate's face a body's middle may be and still cross it this tick. */
    private static final double DEEPEST = 1.5;
    private static final double HALF = 0.5;
    private static final double UNIT = 1.0;
    private static final float DEGREES = (float) (180.0 / Math.PI);

    private static volatile List<Pair> serverPairs = List.of();
    private static volatile List<Pair> clientPairs = List.of();
    /** Each body near a gate's middle at the last tick, which a crossing is read against. */
    private static Map<UUID, Vec3> lastMiddles = Map.of();

    private GatePassage() {
    }

    /**
     * Two gates linked, each opening onto the other.
     *
     * @param one   a gate
     * @param other its partner
     */
    public record Pair(Gate one, Gate other) {
    }

    /**
     * Sets the gates the client knows linked.
     *
     * @param pairs the pairs
     */
    public static void setClientPairs(List<Pair> pairs) {
        clientPairs = List.copyOf(pairs);
    }

    /**
     * Whether a block stands open to a body: it is one a linked gate lies on,
     * and the body stands at the gate, before its face and within its oval.
     *
     * @param entity the body
     * @param pos    the block
     * @return true where the body passes into the block
     */
    public static boolean passable(Entity entity, BlockPos pos) {
        List<Pair> pairs = entity.level().isClientSide() ? clientPairs : serverPairs;
        if (pairs.isEmpty()) {
            return false;
        }
        Vec3 middle = entity.getBoundingBox().getCenter();
        for (Pair pair : pairs) {
            for (Gate gate : List.of(pair.one(), pair.other())) {
                if (opensTo(gate, entity, pos, middle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether one gate opens a block to a body: the gate lies on the block
     * in the body's level, and the body's middle stands at its face, within
     * its oval.
     *
     * @param gate   the gate
     * @param entity the body
     * @param pos    the block
     * @param middle the body's middle
     * @return true where the gate opens the block to the body
     */
    private static boolean opensTo(Gate gate, Entity entity, BlockPos pos, Vec3 middle) {
        return gate.dimension().equals(entity.level().dimension()) && gate.covers(pos) && atTheFace(gate, entity,
                middle);
    }

    private static boolean atTheFace(Gate gate, Entity entity, Vec3 middle) {
        double out = gate.out(middle);
        return out > -DEEPEST && out < APPROACH + entity.getBbWidth() && gate.withinOval(middle);
    }

    /**
     * Gathers the server's linked gates and carries every body whose middle
     * crossed one's face this tick out of its partner.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        serverPairs = gather(server);
        Map<UUID, Vec3> seen = new HashMap<>();
        for (Pair pair : serverPairs) {
            crossThrough(server, pair.one(), pair.other(), seen);
            crossThrough(server, pair.other(), pair.one(), seen);
        }
        lastMiddles = seen;
    }

    private static List<Pair> gather(MinecraftServer server) {
        List<Pair> pairs = new ArrayList<>();
        QuantumPortals.get(server.overworld()).links().values()
                .forEach(link -> pairs.add(new Pair(link.entry().gate(), link.exit().gate())));
        OpenHoles.open().forEach(hole -> pairs.add(new Pair(hole.entry(), hole.exit())));
        return pairs;
    }

    private static void crossThrough(MinecraftServer server, Gate near, Gate far, Map<UUID, Vec3> seen) {
        ServerLevel level = server.getLevel(near.dimension());
        ServerLevel target = server.getLevel(far.dimension());
        if (level == null || target == null) {
            return;
        }
        for (Entity entity : level.getEntities((Entity) null, near.blocks().inflate(APPROACH + 1), Entity::isAlive)) {
            Vec3 before = lastMiddles.get(entity.getUUID());
            if (before != null && crossed(near, before, entity.getBoundingBox().getCenter())) {
                carry(target, entity, PortalTransform.between(near, far));
            }
            seen.put(entity.getUUID(), entity.getBoundingBox().getCenter());
        }
    }

    /**
     * Whether a body's middle crossed a gate's face since the last tick,
     * within its oval.
     *
     * @param gate   the gate
     * @param before the body's middle at the last tick
     * @param now    its middle now
     * @return true where it went through
     */
    static boolean crossed(Gate gate, Vec3 before, Vec3 now) {
        return gate.out(before) >= 0 && gate.out(now) < 0 && gate.out(now) > -DEEPEST && gate.withinOval(now);
    }

    /**
     * Carries a body out of the partner gate at the point matching where it
     * crossed, its momentum and its view turned through the pair.
     *
     * @param target  the partner's level
     * @param entity  the body
     * @param through the transform through the pair
     */
    static void carry(ServerLevel target, Entity entity, PortalTransform through) {
        AABB box = entity.getBoundingBox();
        Vec3 middle = through.point(box.getCenter());
        Vec3 feet = middle.subtract(0, box.getYsize() * HALF, 0);
        Vec3 motion = through.direction(entity.getDeltaMovement());
        Vec3 look = through.direction(entity.getLookAngle());
        float yaw = (float) Math.atan2(-look.x(), look.z()) * DEGREES;
        float pitch = (float) Math.asin(Math.clamp(-look.y(), -UNIT, UNIT)) * DEGREES;
        if (entity instanceof ServerPlayer player) {
            player.teleport(new TeleportTransition(target, feet, motion, yaw, pitch, Set.of(),
                    TeleportTransition.DO_NOTHING));
        } else {
            entity.teleport(new TeleportTransition(target, feet, motion, yaw, pitch, Set.of(),
                    TeleportTransition.DO_NOTHING));
            entity.setDeltaMovement(motion);
            entity.hurtMarked = true;
        }
        entity.resetFallDistance();
    }
}
