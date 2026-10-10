package com.mercuriusxeno.goo.ability.spray;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.FloorReach;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.SurfaceHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Runs a sprayed ability where its spray reaches: its behaviors on each
 * living entity, its {@code on_blocks} steps on each floor. A stream's cone
 * and a spored corpse's burst share it, so a burst is the same spray the
 * stream sprays (decision mycosis-spore-stream-buds-and-poisons).
 */
public final class SprayPrograms {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the {}: {}";
    /** Motes in a corpse's burst, a big cloud filling its sphere. */
    private static final int BURST_PARTICLES = 192;
    /** How far up the cloud spreads, in blocks. */
    private static final double BURST_RISE = 1.5;
    private static final double BURST_LIFT = 0.5;
    private static final double BURST_SPEED = 0.03;

    private SprayPrograms() {
    }

    /**
     * Runs the ability's behaviors on one living entity the spray reached.
     *
     * @param level   the server level
     * @param living  the entity reached
     * @param thrower the entity spraying, or null for a burst
     * @param ability the sprayed ability
     */
    public static void runOnLiving(ServerLevel level, LivingEntity living, @Nullable Entity thrower,
                                   AbilityDefinition ability) {
        try {
            // mycosis-grows-and-reaps-nether-wart: the steps tending the cone's blocks run in the stream's block pass alone
            ProgramBehavior.forHost(ChannelHost.passSteps(ability.behaviors(), false), HostKind.ENTITY)
                    .tick(new EntityHost(level, living, thrower));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), HostKind.ENTITY.label(), e.getMessage());
        }
    }

    /**
     * Runs the ability's {@code on_blocks} steps on each floor the spray
     * reached; an ability naming none runs nothing.
     *
     * @param level   the server level
     * @param floors  the floors reached
     * @param source  where the spray came from, which each floor's distance is read from
     * @param ability the sprayed ability
     */
    public static void runOnFloors(ServerLevel level, List<BlockPos> floors, Vec3 source,
                                   AbilityDefinition ability) {
        if (ability.onBlocks().isEmpty()) {
            return;
        }
        try {
            for (BlockPos floor : floors) {
                ProgramBehavior.forHost(ability.onBlocks(), HostKind.SURFACE).tick(new SurfaceHost(level, floor,
                        Vec3.atCenterOf(floor.above()).distanceTo(source)));
            }
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), HostKind.SURFACE.label(), e.getMessage());
        }
    }

    /**
     * Bursts the ability's spray in a sphere: its particle clouds the sphere,
     * and it runs on every living entity and every floor inside.
     *
     * @param level   the server level
     * @param center  the burst's center
     * @param radius  the burst's reach in blocks
     * @param ability the sprayed ability
     */
    public static void burst(ServerLevel level, Vec3 center, double radius, AbilityDefinition ability) {
        ability.delivery().particle().flatMap(SimpleParticles::resolve).ifPresent(particle -> level.sendParticles(particle,
                center.x, center.y + BURST_LIFT, center.z, BURST_PARTICLES, radius, BURST_RISE, radius,
                BURST_SPEED));
        AABB reach = new AABB(center, center).inflate(radius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, reach,
                living -> living.isAlive() && living.position().distanceTo(center) <= radius)) {
            runOnLiving(level, living, null, ability);
        }
        runOnFloors(level, FloorReach.inSphere(center, radius, cell -> FloorReach.isOpenFloor(level, cell)), center,
                ability);
    }
}
