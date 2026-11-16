package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ability.program.WispShed;
import com.mercuriusxeno.goo.block.ability.WispClaims;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A wisp a Wisps cell let out, drifting off the goo upward and over to the
 * dark spot it claimed, lighting nothing on the way, since a light update
 * each tick it moved would cost more than the light is worth. On arrival it
 * becomes the glowy air block wisps are, fading on its own; where its spot
 * was taken or lit meanwhile it heads once for another dark spot near it,
 * and where none lies it is gone. A drifting wisp is not saved: a chunk
 * unloading under it drops it, and its claim lapses.
 * wisps-goo-sheds-a-wisp-toward-nearby-dark
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
public class DriftingWisp extends Entity {

    /** Blocks a wisp drifts each tick. */
    static final double DRIFT_SPEED = 0.15;
    /** Blocks a wisp rises off the goo before it heads over to its spot. */
    static final double RISE = 1.0;
    /** How far a wisp heads for another spot, in blocks, when it finds its own taken. */
    private static final int RETARGET_REACH = 8;

    private @Nullable BlockPos destination;
    private @Nullable Vec3 risePoint;
    private int life;
    private boolean retargeted;

    /**
     * The constructor the entity type builds a synced wisp with.
     *
     * @param type  the entity type
     * @param level the level
     */
    public DriftingWisp(EntityType<? extends DriftingWisp> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * Lets a wisp out of a Wisps cell, headed for a claimed spot.
     *
     * @param level       the server level
     * @param cell        the Wisps cell shedding it
     * @param destination the spot it claimed
     * @param life        the ticks it lasts once settled
     */
    public static void shed(ServerLevel level, BlockPos cell, BlockPos destination, int life) {
        DriftingWisp wisp = new DriftingWisp(GooEntities.DRIFTING_WISP.get(), level);
        Vec3 start = Vec3.atCenterOf(cell);
        wisp.setPos(start);
        BlockPos over = cell.above();
        wisp.risePoint = level.getBlockState(over).isAir() ? start.add(0, RISE, 0) : null;
        wisp.destination = destination.immutable();
        wisp.life = life;
        level.addFreshEntity(wisp);
    }

    /**
     * The step a wisp drifts toward a point: straight at it, no further
     * than the drift speed and no further than the point.
     *
     * @param from  where the wisp stands
     * @param to    the point it heads for
     * @param speed the blocks it drifts a tick
     * @return the step to take
     */
    static Vec3 driftToward(Vec3 from, Vec3 to, double speed) {
        Vec3 way = to.subtract(from);
        double length = way.length();
        return length <= speed ? way : way.scale(speed / length);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        // the client draws the wisp where the server moves it and reads nothing else
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            tickServer(server);
        }
    }

    private void tickServer(ServerLevel server) {
        if (destination == null || tickCount > WispClaims.LONGEST_DRIFT_TICKS) {
            giveUp(server);
            return;
        }
        Vec3 aim = risePoint != null ? risePoint : Vec3.atCenterOf(destination);
        Vec3 step = driftToward(position(), aim, DRIFT_SPEED);
        setPos(position().add(step));
        if (position().distanceToSqr(aim) > 0) {
            return;
        }
        if (risePoint != null) {
            risePoint = null;
        } else {
            arrive(server, destination);
        }
    }

    private void arrive(ServerLevel server, BlockPos spot) {
        WispClaims claims = GooServerState.of(server.getServer()).wispClaims();
        claims.release(server, spot);
        if (WispShed.settles(server, spot)) {
            WispShed.settle(server, spot, life);
            discard();
            return;
        }
        destination = retargeted ? null : WispShed.destinationFrom(server, spot, RETARGET_REACH).orElse(null);
        retargeted = true;
        if (destination == null) {
            discard();
        } else {
            claims.claim(server, destination);
        }
    }

    private void giveUp(ServerLevel server) {
        if (destination != null) {
            GooServerState.of(server.getServer()).wispClaims().release(server, destination);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // a drifting wisp is never saved
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // a drifting wisp is never saved
    }
}
