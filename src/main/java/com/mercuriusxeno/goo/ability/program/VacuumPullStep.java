package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Void Vacuum's pull, run each held tick of the stream on what stands inside
 * its cone: each item entity flies to the player and lands in the inventory
 * as a pickup, as Grab pulls an item, the goo a vacuumed residual drops
 * among them; each mob is dragged level toward the player at a steady pace
 * its knockback resistance slows, takes no damage, and stops about a block in
 * front. Bosses and players are not dragged.
 * vacuum-pulls-items-to-pickup-and-drags-mobs
 *
 * @param itemSpeed    the speed an item flies to the player at, in blocks a tick
 * @param mobSpeed     the pace a mob resisting no knockback is dragged at, in blocks a tick
 * @param stopDistance how near the player, level, a dragged mob stops, in blocks
 */
public record VacuumPullStep(double itemSpeed, double mobSpeed, double stopDistance) implements TakesNoEntity {

    private static final String NAME = "vacuum_pull";
    private static final String FIELD_ITEM_SPEED = "item_speed";
    private static final String FIELD_MOB_SPEED = "mob_speed";
    private static final String FIELD_STOP_DISTANCE = "stop_distance";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VacuumPullStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_ITEM_SPEED).forGetter(VacuumPullStep::itemSpeed),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_MOB_SPEED).forGetter(VacuumPullStep::mobSpeed),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_STOP_DISTANCE)
                    .forGetter(VacuumPullStep::stopDistance)
    ).apply(inst, VacuumPullStep::new));

    /**
     * The registered type.
     */
    public static final StepType<VacuumPullStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<VacuumPullStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        if (!(host.breaker() instanceof ServerPlayer player)) {
            return true;
        }
        host.channelAim().ifPresent(aim -> {
            Vec3 eye = host.eye();
            Vec3 look = aim.aimPoint().subtract(eye);
            for (Entity entity : inCone(host, player, eye, look, aim.coneDegrees())) {
                if (entity instanceof ItemEntity item) {
                    GrabEvents.pull(player, item, itemSpeed, eye);
                } else if (drags(entity)) {
                    drag((Mob) entity, player);
                }
            }
        });
        return true;
    }

    private static List<Entity> inCone(ChannelHost host, ServerPlayer player, Vec3 eye, Vec3 look,
                                       double coneDegrees) {
        double range = look.length();
        return host.level().getEntities(player, new AABB(eye, eye).inflate(range),
                entity -> StreamCone.contains(eye, look, range, coneDegrees, entity.getBoundingBox().getCenter()));
    }

    private void drag(Mob mob, ServerPlayer player) {
        double resistance = mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        Vec3 step = dragStep(mob.position(), player.position(), pace(mobSpeed, resistance), stopDistance);
        // set even when zero, so a mob at the stop halts there rather than drifting on the last tick's drag
        mob.setDeltaMovement(step.x, mob.getDeltaMovement().y, step.z);
        mob.hurtMarked = true;
    }

    /**
     * The pace a mob is dragged at: the full pace slowed by the share of
     * knockback it resists.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param speed               the pace a mob resisting no knockback is dragged at
     * @param knockbackResistance the mob's knockback resistance, 0 to 1
     * @return the pace, in blocks a tick
     */
    static double pace(double speed, double knockbackResistance) {
        return speed * (1 - Math.clamp(knockbackResistance, 0, 1));
    }

    /**
     * One tick's level drag toward the player, cut short at the stop so the
     * mob halts about a block in front rather than on the player.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param from         the mob's position
     * @param toward       the player's position
     * @param pace         the drag's pace, in blocks a tick
     * @param stopDistance how near the player, level, the drag stops
     * @return the tick's motion, level, zero once within the stop
     */
    static Vec3 dragStep(Vec3 from, Vec3 toward, double pace, double stopDistance) {
        Vec3 level = new Vec3(toward.x - from.x, 0, toward.z - from.z);
        double left = level.length() - stopDistance;
        if (left <= 0) {
            return Vec3.ZERO;
        }
        return level.normalize().scale(Math.min(pace, left));
    }

    /**
     * Whether the pull drags an entity: a living mob that is no boss, and so
     * never a player.
     * vacuum-pulls-items-to-pickup-and-drags-mobs
     *
     * @param entity the entity in the cone
     * @return true for a mob the pull drags
     */
    static boolean drags(Entity entity) {
        return entity instanceof Mob mob && mob.isAlive() && !EntityScan.isBoss(mob);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
