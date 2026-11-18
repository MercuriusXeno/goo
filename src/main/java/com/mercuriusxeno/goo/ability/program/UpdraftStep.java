package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.typhoon.AirbornMotion;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Typhoon's Updraft block step: Typhoon's goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Rime its frost goo. The step names
 * what an Updraft cell's column reads: its half width and the rise it
 * carries at in blocks per tick, the column standing a block over the
 * cell's top for each of its layers; and the glide zone over it, its height,
 * the fastest a fall through it runs, and how a player's keys steer it
 * there, Float's air speed and steer share; and the air it refills each
 * living entity in either zone a tick, faster than lunar drains it:
 * {@code updraft half_width=0.5 rise=0.4 glide_height=16 fall_cap=0.15 air_speed=0.35 air_steer=0.15
 * air_refill=10}.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 * updraft-blob-stands-a-column-of-wind
 * updraft-refills-the-air-of-entities-it-propels
 * operator ruling 2026-11-17: a tall zone above the push smooths transitions with slow fall and air control
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param halfWidth   the column's half width in blocks, around the middle of the cell
 * @param rise        the rise the column carries at, in blocks per tick
 * @param glideHeight the glide zone's height over the column's top, in blocks
 * @param fallCap     the fastest a fall over the cell runs, in blocks per tick
 * @param airSpeed    the speed a player's keys steer it toward over the cell, in blocks per tick
 * @param airSteer    the share of the way a tick turns a player's motion toward its keys, 0 to 1
 * @param airRefill   the air a living entity in the column or the glide zone regains each tick
 */
public record UpdraftStep(double halfWidth, double rise, double glideHeight, double fallCap, double airSpeed,
                          double airSteer, int airRefill) implements TakesNoEntity, DripLanding {

    private static final String NAME = "updraft";
    private static final String FIELD_HALF_WIDTH = "half_width";
    private static final String FIELD_RISE = "rise";
    private static final String FIELD_GLIDE_HEIGHT = "glide_height";
    private static final String FIELD_FALL_CAP = "fall_cap";
    private static final String FIELD_AIR_SPEED = "air_speed";
    private static final String FIELD_AIR_STEER = "air_steer";
    private static final String FIELD_AIR_REFILL = "air_refill";
    /** The most air a tick refills: vanilla's full breath. */
    private static final int MOST_AIR_REFILL = 300;
    /** The tallest a glide zone stands over its column. */
    private static final double MOST_GLIDE_HEIGHT = 32;
    /** The fastest a player's keys steer it over the goo: a block a tick. */
    private static final double MOST_AIR_SPEED = 1.0;
    private static final double NO_INPUT_SQUARED = 1e-6;
    /** The widest a column stands: a block and a half each way from the cell's middle. */
    private static final double MOST_HALF_WIDTH = 1.5;
    /** The fastest a column carries: a block a tick. */
    private static final double MOST_RISE = 1.0;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<UpdraftStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, MOST_HALF_WIDTH).fieldOf(FIELD_HALF_WIDTH).forGetter(UpdraftStep::halfWidth),
            Codec.doubleRange(0, MOST_RISE).fieldOf(FIELD_RISE).forGetter(UpdraftStep::rise),
            Codec.doubleRange(0, MOST_GLIDE_HEIGHT).fieldOf(FIELD_GLIDE_HEIGHT).forGetter(UpdraftStep::glideHeight),
            Codec.doubleRange(0, MOST_RISE).fieldOf(FIELD_FALL_CAP).forGetter(UpdraftStep::fallCap),
            Codec.doubleRange(0, MOST_AIR_SPEED).fieldOf(FIELD_AIR_SPEED).forGetter(UpdraftStep::airSpeed),
            Codec.doubleRange(0, 1).fieldOf(FIELD_AIR_STEER).forGetter(UpdraftStep::airSteer),
            Codec.intRange(0, MOST_AIR_REFILL).fieldOf(FIELD_AIR_REFILL).forGetter(UpdraftStep::airRefill)
    ).apply(inst, UpdraftStep::new));

    /**
     * The registered type.
     */
    public static final StepType<UpdraftStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The updraft step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the updraft step, empty where the program lays no Updraft goo
     */
    public static Optional<UpdraftStep> in(List<Step> program) {
        return program.stream().filter(UpdraftStep.class::isInstance).map(UpdraftStep.class::cast).findFirst();
    }

    /**
     * How far an Updraft cell's column stands over the cell's top: a block
     * for each of its layers, so fifteen layers lift fifteen blocks.
     * updraft-goo-lifts-one-block-per-layer
     *
     * @param layers the cell's layers
     * @return the column's height over the cell's top, in blocks
     */
    public static double heightOver(int layers) {
        return layers;
    }

    /**
     * How tall an Updraft cell's push column stands over its floor: through
     * its goo, then {@link #heightOver} blocks over its top.
     *
     * @param layers the cell's layers
     * @return the push column's height over the cell's floor, in blocks
     */
    public static double pushHeight(int layers) {
        return (double) layers / VolumeCellBlock.MAX_LAYERS + heightOver(layers);
    }

    /**
     * Carries every entity whose feet stand in an Updraft cell's column or
     * the glide zone over it, as wide as the step names: a mob rises at the
     * step's rise and falls no faster than its cap, every entity's fall
     * clears and every living one regains the step's air refill; a player's
     * own client moves it. Lifting spends none of the cell's layers.
     * updraft-goo-is-semi-permanent-and-does-not-deplete
     * updraft-refills-the-air-of-entities-it-propels
     *
     * @param level  the server level
     * @param cell   the Updraft cell
     * @param layers the cell's layers
     */
    public void liftAbove(ServerLevel level, BlockPos cell, int layers) {
        EntityLift.liftInColumn(level, Vec3.atBottomCenterOf(cell), halfWidth, pushHeight(layers), glideHeight, rise,
                fallCap, airRefill);
    }

    /**
     * A velocity over an Updraft cell, at a height over the cell's floor: in
     * the push column it rises at the step's rise, in the glide zone over it
     * its fall runs no faster than the cap; over the glide zone, or below the
     * cell's floor, the cell carries nothing.
     *
     * @param velocity      the velocity now
     * @param feetOverFloor the entity's feet over the cell's floor, in blocks
     * @param layers        the cell's layers
     * @return the carried velocity, empty outside the column and its glide zone
     */
    public Optional<Vec3> carried(Vec3 velocity, double feetOverFloor, int layers) {
        double push = pushHeight(layers);
        if (feetOverFloor < 0 || feetOverFloor >= push + glideHeight) {
            return Optional.empty();
        }
        return Optional.of(EntityLift.carried(velocity, push - feetOverFloor, rise, fallCap));
    }

    /**
     * A player's velocity over an Updraft cell turned by its keys: its
     * sideways motion moves the steer share of the way toward the air speed
     * along where the keys point at its yaw; no keys leave it, and the
     * height stays the column's to carry.
     *
     * @param velocity the velocity now
     * @param input    the movement keys: strafe left as x, forward as z, each -1 to 1
     * @param yaw      the player's yaw in degrees
     * @return the steered velocity
     */
    public Vec3 steered(Vec3 velocity, Vec3 input, float yaw) {
        Vec3 heading = AirbornMotion.heading(new Vec3(input.x, 0, input.z), yaw);
        if (heading.lengthSqr() < NO_INPUT_SQUARED) {
            return velocity;
        }
        Vec3 aim = heading.normalize().scale(airSpeed);
        double share = Math.clamp(airSteer, 0, 1);
        return new Vec3(velocity.x + (aim.x - velocity.x) * share, velocity.y,
                velocity.z + (aim.z - velocity.z) * share);
    }

    @Override
    public StepType<UpdraftStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Updraft goo as they strike.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Optional<CellFed> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return CellLanding.cellFed(host, strike, GooBlocks.TYPHOON_UPDRAFT.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.TYPHOON_UPDRAFT.get());
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
