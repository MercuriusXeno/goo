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
 * A wisp a Wisps cell let out, beaming in one straight line from the center
 * of the cell to the center of the dark spot it claimed, arriving in about
 * half a second however far the spot lies, and lighting nothing on the
 * way, since a light update each tick it moved would cost more than the
 * light is worth. On arrival it becomes the glowy air block wisps are,
 * fading on its own, its mote staying on the spot a moment so the block's
 * mote takes over from it with no gap; where its spot was taken or lit
 * meanwhile it beams once, by the same rule, from where it stands to
 * another dark spot near it, and where none lies it is gone. A drifting
 * wisp is not saved: a chunk unloading under it drops it, and its claim
 * lapses.
 * wisps-goo-sheds-a-wisp-toward-nearby-dark
 * shed-wisps-arrive-in-about-half-a-second
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
public class DriftingWisp extends Entity {

    /** Ticks a wisp takes to beam to its spot, whatever the distance: half a second. */
    static final int ARRIVAL_TICKS = 10;
    /** How far a wisp heads for another spot, in blocks, when it finds its own taken. */
    private static final int RETARGET_REACH = 8;
    /**
     * Ticks a settled wisp's drifting mote stays on its spot after the wisp
     * block appears there, so a watching client holds the block before the
     * drifting mote leaves it.
     */
    static final int HANDOFF_TICKS = 5;
    /** The settled ticks of a wisp still drifting. */
    private static final int DRIFTING = -1;

    private @Nullable BlockPos destination;
    /** Where the wisp set off on its beam: the shedding cell's center, or where it stood on retargeting. */
    private Vec3 beamFrom = Vec3.ZERO;
    /** Ticks since the wisp set off on its beam. */
    private int beamTicks;
    private int life;
    private boolean retargeted;
    private int settledTicks = DRIFTING;

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
        wisp.beamFrom = Vec3.atCenterOf(cell);
        wisp.setPos(wisp.beamFrom);
        wisp.destination = destination.immutable();
        wisp.life = life;
        level.addFreshEntity(wisp);
    }

    /**
     * Where a beaming wisp stands a number of ticks after it set off: on the
     * straight line from where it set off to the point it heads for, as far
     * along it as those ticks are of the arrival count, and on the point
     * itself from the arrival count on.
     * shed-wisps-arrive-in-about-half-a-second
     * wisps-goo-sheds-a-wisp-toward-nearby-dark
     *
     * @param from  where the wisp set off
     * @param to    the point it heads for
     * @param ticks the ticks since it set off
     * @return where it stands
     */
    static Vec3 beamPosition(Vec3 from, Vec3 to, int ticks) {
        return ticks >= ARRIVAL_TICKS ? to : from.lerp(to, (double) ticks / ARRIVAL_TICKS);
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
        if (settledTicks != DRIFTING) {
            holdOnTheSpot();
            return;
        }
        if (destination == null || tickCount > WispClaims.LONGEST_DRIFT_TICKS) {
            giveUp(server);
            return;
        }
        beamOn(server, destination);
    }

    /**
     * Keeps a settled wisp's drifting mote on its spot for the handoff, then
     * lets it go, the wisp block drawing the mote from then on.
     * operator UAT 2026-11-16: the mote vanished for a few frames as the wisp settled
     */
    private void holdOnTheSpot() {
        settledTicks++;
        if (settledTicks >= HANDOFF_TICKS) {
            discard();
        }
    }

    /**
     * Beams the wisp a tick further along its line to the spot, arriving on
     * the arrival count.
     *
     * @param server the server level
     * @param spot   the spot the wisp heads for
     */
    private void beamOn(ServerLevel server, BlockPos spot) {
        beamTicks++;
        setPos(beamPosition(beamFrom, Vec3.atCenterOf(spot), beamTicks));
        if (beamTicks >= ARRIVAL_TICKS) {
            arrive(server, spot);
        }
    }

    private void arrive(ServerLevel server, BlockPos spot) {
        if (WispShed.settles(server, spot)) {
            // the claim stands on: the other wisps know this spot's light until the world shows it
            WispShed.settle(server, spot, life);
            settledTicks = 0;
            return;
        }
        WispClaims claims = GooServerState.of(server.getServer()).wispClaims();
        claims.release(server, spot);
        destination = retargeted ? null : WispShed.destinationFrom(server, spot, RETARGET_REACH, 0).orElse(null);
        retargeted = true;
        if (destination == null) {
            discard();
        } else {
            claims.claim(server, destination);
            beamFrom = position();
            beamTicks = 0;
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
