package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.GooExplosion;
import com.mercuriusxeno.goo.ability.world.MeteorFall;
import com.mercuriusxeno.goo.ability.world.MeteorMarks;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.MeteorScorchPayload;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Meteo's meteor: it appears high above its target and hangs there
 * smouldering through its wind-up while a ring marks the target, then drops
 * straight down over its fall time trailing fire and smoke, and explodes
 * there through Goo's explosion with a flash, breaking blocks into a crater
 * every watching client scorches.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 * decision meteo-needs-a-clear-sky
 */
public class Meteor extends Entity {

    private static final String TAG_TARGET = "Target";
    private static final String TAG_START = "Start";
    private static final String TAG_POWER = "Power";
    private static final String TAG_WIND_UP_TICKS = "WindUpTicks";
    private static final String TAG_FALL_TICKS = "FallTicks";
    private static final String TAG_AGE = "Age";
    /** How far past the explosion's power the scorch reaches, in blocks. */
    private static final float SCORCH_PAST_POWER = 1.5f;

    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private float power;
    private int windUpTicks;
    private int fallTicks = 1;
    private int age;

    /**
     * The constructor the entity type builds a loaded or synced meteor with.
     *
     * @param type  the entity type
     * @param level the level
     */
    public Meteor(EntityType<? extends Meteor> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Calls a meteor down on a point: it appears above the point, as high as
     * the fall height or the world's top allows, hangs there through the
     * wind-up and lands after the fall time.
     *
     * @param level       the server level
     * @param at          the point it strikes
     * @param power       the explosion's power
     * @param windUpTicks the ticks it hangs before it drops
     * @param fallTicks   the ticks it falls for
     */
    public static void call(ServerLevel level, Vec3 at, float power, int windUpTicks, int fallTicks) {
        Meteor meteor = new Meteor(GooEntities.METEOR.get(), level);
        meteor.target = at;
        meteor.start = MeteorFall.startAbove(at, level.getMaxY());
        meteor.power = power;
        meteor.windUpTicks = Math.max(0, windUpTicks);
        meteor.fallTicks = Math.max(1, fallTicks);
        meteor.setPos(meteor.start);
        level.addFreshEntity(meteor);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        // the client draws the trail the server sends and reads nothing the server holds
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        age++;
        int fallen = MeteorFall.fallenAt(age, windUpTicks);
        if (fallen == 0) {
            MeteorMarks.windUp(server, start, target, age);
            return;
        }
        if (fallen == 1) {
            MeteorMarks.drop(server, start);
        }
        setPos(MeteorFall.positionAt(start, target, fallen, fallTicks));
        MeteorMarks.trail(server, position());
        if (fallen >= fallTicks) {
            strike(server);
        }
    }

    private void strike(ServerLevel server) {
        discard();
        GooExplosion.detonate(server, target, power, ExplosionMode.TNT, GooExplosion.Look.vanilla());
        MeteorMarks.impact(server, target);
        EntityVisuals.sendToWatchersOf(server, target, new MeteorScorchPayload(target, power + SCORCH_PAST_POWER));
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
        start = input.read(TAG_START, Vec3.CODEC).orElse(position());
        target = input.read(TAG_TARGET, Vec3.CODEC).orElse(position());
        power = input.read(TAG_POWER, Codec.FLOAT).orElse(0f);
        windUpTicks = Math.max(0, input.getIntOr(TAG_WIND_UP_TICKS, 0));
        fallTicks = Math.max(1, input.getIntOr(TAG_FALL_TICKS, 1));
        age = input.getIntOr(TAG_AGE, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_START, Vec3.CODEC, start);
        output.store(TAG_TARGET, Vec3.CODEC, target);
        output.store(TAG_POWER, Codec.FLOAT, power);
        output.putInt(TAG_WIND_UP_TICKS, windUpTicks);
        output.putInt(TAG_FALL_TICKS, fallTicks);
        output.putInt(TAG_AGE, age);
    }
}
