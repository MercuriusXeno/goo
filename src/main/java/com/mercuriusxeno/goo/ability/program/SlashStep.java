package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.ReapQueue;
import com.mercuriusxeno.goo.ability.reap.ReapSlashes;
import com.mercuriusxeno.goo.ability.reap.Reaping;
import com.mercuriusxeno.goo.ability.reap.SlashCuts;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reap's step, run on the player using it: a flurry of leaf crescent slashes
 * flies out from the player's chest, one after another, each in a random
 * heading inside the forward cone, pitched from level down to the dive that
 * skims the ground at its reach; every ripe plant a slash's path crosses is
 * reaped and replanted as the slash reaches it, and every living body it
 * crosses but the slasher is cut. Leaf Reap is {@code slash slashes=5
 * cone=100 reach=6 flight_ticks=5 stagger_ticks=2 damage=4 invulnerable_ticks=2}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param slashes           the slashes one use launches
 * @param cone              the forward cone the slashes fly within, edge to edge, in degrees
 * @param reach             how far each slash flies, in blocks
 * @param flightTicks       the ticks a slash takes to fly its reach
 * @param staggerTicks      the ticks between one slash's launch and the next
 * @param damage            what each cut deals
 * @param invulnerableTicks the ticks a body cut keeps every slash off, so no one slash cuts it twice
 */
public record SlashStep(Expr slashes, Expr cone, Expr reach, Expr flightTicks, Expr staggerTicks, Expr damage,
                        Expr invulnerableTicks) implements Step {

    private static final String NAME = "slash";
    private static final String FIELD_SLASHES = "slashes";
    private static final String FIELD_CONE = "cone";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_FLIGHT_TICKS = "flight_ticks";
    private static final String FIELD_STAGGER_TICKS = "stagger_ticks";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_INVULNERABLE_TICKS = "invulnerable_ticks";
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
            Expr.CODEC.fieldOf(FIELD_STAGGER_TICKS).forGetter(SlashStep::staggerTicks),
            Expr.CODEC.fieldOf(FIELD_DAMAGE).forGetter(SlashStep::damage),
            Expr.CODEC.fieldOf(FIELD_INVULNERABLE_TICKS).forGetter(SlashStep::invulnerableTicks)
    ).apply(inst, SlashStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SlashStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SlashStep> type() {
        return TYPE;
    }

    /**
     * The step's params evaluated for one use.
     *
     * @param reach       how far each slash flies
     * @param flightTicks the ticks a slash takes to fly it
     * @param cone        the forward cone's width, in degrees
     * @param damage      what each cut deals
     * @param window      the ticks a body cut keeps every slash off
     */
    private record Flurry(double reach, int flightTicks, double cone, float damage, int window) {
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity slasher = context.hostAs(TargetHost.class).target();
        if (!(slasher.level() instanceof ServerLevel level)) {
            return true;
        }
        Flurry flurry = new Flurry(reach.evaluate(context), Math.max(1, flightTicks.evaluateInt(context)),
                cone.evaluate(context), damage.evaluateFloat(context), invulnerableTicks.evaluateInt(context));
        int stagger = staggerTicks.evaluateInt(context);
        long now = level.getGameTime();
        int count = slashes.evaluateInt(context);
        for (int slash = 0; slash < count; slash++) {
            launch(level, slasher, flurry, now + (long) slash * stagger);
        }
        return true;
    }

    /**
     * Launches one slash from the slasher's chest: its blade drawn on the
     * watchers' clients, the ripe plants its path crosses queued to be
     * reaped, and the living it crosses set to be cut.
     *
     * @param level      the server level
     * @param slasher    the entity slashing
     * @param flurry     the use's params
     * @param launchedAt the game tick the slash launches
     */
    private static void launch(ServerLevel level, LivingEntity slasher, Flurry flurry, long launchedAt) {
        double launchHeight = slasher.getBbHeight() * CHEST_SHARE;
        Vec3 origin = slasher.position().add(0, launchHeight, 0);
        RandomSource random = slasher.getRandom();
        Vec3 path = ReapSlashes.sample(slasher.getYRot(), flurry.cone(),
                ReapSlashes.dropLimit(launchHeight, flurry.reach()), random).direction().scale(flurry.reach());
        float roll = Mth.nextFloat(random, -ROLL_SPREAD, ROLL_SPREAD);
        BlockVisuals.sendToWatchers(level, BlockPos.containing(origin), new ReapSlashPayload(origin,
                path.normalize(), (float) flurry.reach(), flurry.flightTicks(),
                (int) (launchedAt - level.getGameTime()), roll));
        reapAlong(level, origin, path, launchedAt, flurry.flightTicks());
        SlashCuts.launch(level, new SlashCuts.Cut(slasher.getId(), origin, path, launchedAt, flurry.flightTicks(),
                flurry.damage(), flurry.window()));
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
        return Stream.of(slashes, cone, reach, flightTicks, staggerTicks, damage, invulnerableTicks);
    }

    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof LivingEntity;
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
