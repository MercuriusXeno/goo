package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.ReapQueue;
import com.mercuriusxeno.goo.ability.reap.ReapSlashes;
import com.mercuriusxeno.goo.ability.reap.Reaping;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reap's step, run on the player using it: a flurry of leaf crescent slashes
 * flies out from the player's chest, one after another, each in a random
 * heading inside the forward cone, pitched from level down to the dive that
 * skims the ground at its reach; every ripe plant a slash's path crosses is
 * reaped and replanted as the slash reaches it. Leaf Reap is
 * {@code slash slashes=5 cone=100 reach=6 flight_ticks=5 stagger_ticks=2}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param slashes      the slashes one use launches
 * @param cone         the forward cone the slashes fly within, edge to edge, in degrees
 * @param reach        how far each slash flies, in blocks
 * @param flightTicks  the ticks a slash takes to fly its reach
 * @param staggerTicks the ticks between one slash's launch and the next
 */
public record SlashStep(Expr slashes, Expr cone, Expr reach, Expr flightTicks, Expr staggerTicks)
        implements TakesNoEntity {

    private static final String NAME = "slash";
    private static final String FIELD_SLASHES = "slashes";
    private static final String FIELD_CONE = "cone";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_FLIGHT_TICKS = "flight_ticks";
    private static final String FIELD_STAGGER_TICKS = "stagger_ticks";
    /** The share of the slasher's height the slashes launch from: the chest, below the eye. */
    private static final double CHEST_SHARE = 0.6;
    /** How far a blade tilts either way about its heading, in degrees, so the flurry slashes every way. */
    private static final float ROLL_SPREAD = 60f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_SLASHES).forGetter(SlashStep::slashes),
            Expr.CODEC.fieldOf(FIELD_CONE).forGetter(SlashStep::cone),
            Expr.CODEC.fieldOf(FIELD_REACH).forGetter(SlashStep::reach),
            Expr.CODEC.fieldOf(FIELD_FLIGHT_TICKS).forGetter(SlashStep::flightTicks),
            Expr.CODEC.fieldOf(FIELD_STAGGER_TICKS).forGetter(SlashStep::staggerTicks)
    ).apply(inst, SlashStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SlashStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SlashStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity slasher = context.hostAs(TargetHost.class).target();
        if (!(slasher.level() instanceof ServerLevel level)) {
            return true;
        }
        double length = reach.evaluate(context);
        int flight = Math.max(1, flightTicks.evaluateInt(context));
        int stagger = staggerTicks.evaluateInt(context);
        double launchHeight = slasher.getBbHeight() * CHEST_SHARE;
        Vec3 origin = slasher.position().add(0, launchHeight, 0);
        double drop = ReapSlashes.dropLimit(launchHeight, length);
        double width = cone.evaluate(context);
        RandomSource random = slasher.getRandom();
        long now = level.getGameTime();
        int count = slashes.evaluateInt(context);
        for (int slash = 0; slash < count; slash++) {
            Vec3 direction = ReapSlashes.sample(slasher.getYRot(), width, drop, random).direction();
            int delay = slash * stagger;
            float roll = Mth.nextFloat(random, -ROLL_SPREAD, ROLL_SPREAD);
            BlockVisuals.sendToWatchers(level, BlockPos.containing(origin),
                    new ReapSlashPayload(origin, direction, (float) length, flight, delay, roll));
            reapAlong(level, origin, direction.scale(length), now + delay, flight);
        }
        return true;
    }

    /**
     * Queues every ripe plant a slash's path crosses for the tick the slash
     * reaches it.
     *
     * @param level       the server level
     * @param origin      where the slash launches
     * @param path        the slash's flight, its heading scaled to its reach
     * @param launchedAt  the game tick the slash launches
     * @param flightTicks the ticks it takes to fly its reach
     */
    public static void reapAlong(ServerLevel level, Vec3 origin, Vec3 path, long launchedAt, int flightTicks) {
        double length = path.length();
        for (BlockPos cell : ReapSlashes.cellsAlong(origin, origin.add(path))) {
            if (Reaping.ripe(level.getBlockState(cell))) {
                double share = Math.min(1, Vec3.atCenterOf(cell).distanceTo(origin) / length);
                ReapQueue.schedule(level, cell, launchedAt + Math.round(share * flightTicks));
            }
        }
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(slashes, cone, reach, flightTicks, staggerTicks);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
