package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import com.mercuriusxeno.goo.ability.reap.SlashCuts;
import com.mercuriusxeno.goo.ability.reap.SwingCadence;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reap's step, run on the player each tick of the held channel, slashing as
 * the Crissaegrim does in Symphony of the Night: on the hold's first tick,
 * and then a random few ticks after each swing, the player swings with one
 * swish, throwing a random few slashes. Each slash sweeps a level, almost
 * circular area with the slasher at its near edge: a crescent band appears
 * near the slasher and ripples outward to the area's far edge, moving with
 * the slasher, each slash nudged, leant and turned a little its own way.
 * Every ripe plant the band's front gets to inside the area is reaped and
 * replanted, and every living body there but the slasher is cut once a
 * slash. Leaf Reap is {@code slash fewest_ticks=2 most_ticks=3
 * fewest_slashes=1 most_slashes=2 width=5.3 length=5.8 source=0.5
 * back_from=1.1 back_to=3.9 front_from=2.6 drop=0.45 nudge=0.2 tilt=10 fan=5
 * life_ticks=6 damage=1}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param fewestTicks   the fewest held ticks from one swing to the next
 * @param mostTicks     the most held ticks from one swing to the next
 * @param fewestSlashes the fewest slashes a swing throws
 * @param mostSlashes   the most slashes a swing throws
 * @param width         the area's width, in blocks
 * @param length        how far ahead of the slasher the area reaches, in blocks
 * @param source        how far ahead of the slasher the point the band's circles spread from stands, in blocks
 * @param backFrom      the band's back as a slash appears, from the source, in blocks
 * @param backTo        the band's back at the end of a slash's life, from the source, in blocks
 * @param frontFrom     the band's front as a slash appears, from the source, in blocks
 * @param drop          how far under the slasher's eye a slash's plane stands, in blocks
 * @param nudge         the farthest a slash is shifted each of sideways, up and ahead, in blocks
 * @param tilt          the farthest a slash leans either way, in degrees
 * @param fan           the farthest a slash's heading turns off the slasher's facing either way, in degrees
 * @param lifeTicks     the ticks from a slash appearing to its dimming away
 * @param damage        what each slash's cut deals
 */
public record SlashStep(Expr fewestTicks, Expr mostTicks, Expr fewestSlashes, Expr mostSlashes, Expr width,
                        Expr length, Expr source, Expr backFrom, Expr backTo, Expr frontFrom, Expr drop, Expr nudge,
                        Expr tilt, Expr fan, Expr lifeTicks, Expr damage) implements Step {

    private static final String NAME = "slash";
    private static final float SWING_VOLUME = 0.4f;
    private static final float SWING_PITCH = 1.7f;
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("fewest_ticks").forGetter(SlashStep::fewestTicks),
            Expr.CODEC.fieldOf("most_ticks").forGetter(SlashStep::mostTicks),
            Expr.CODEC.fieldOf("fewest_slashes").forGetter(SlashStep::fewestSlashes),
            Expr.CODEC.fieldOf("most_slashes").forGetter(SlashStep::mostSlashes),
            Expr.CODEC.fieldOf("width").forGetter(SlashStep::width),
            Expr.CODEC.fieldOf("length").forGetter(SlashStep::length),
            Expr.CODEC.fieldOf("source").forGetter(SlashStep::source),
            Expr.CODEC.fieldOf("back_from").forGetter(SlashStep::backFrom),
            Expr.CODEC.fieldOf("back_to").forGetter(SlashStep::backTo),
            Expr.CODEC.fieldOf("front_from").forGetter(SlashStep::frontFrom),
            Expr.CODEC.fieldOf("drop").forGetter(SlashStep::drop),
            Expr.CODEC.fieldOf("nudge").forGetter(SlashStep::nudge),
            Expr.CODEC.fieldOf("tilt").forGetter(SlashStep::tilt),
            Expr.CODEC.fieldOf("fan").forGetter(SlashStep::fan),
            Expr.CODEC.fieldOf("life_ticks").forGetter(SlashStep::lifeTicks),
            Expr.CODEC.fieldOf("damage").forGetter(SlashStep::damage)
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
        int held = context.hostAs(ChannelHost.class).channelAim().map(ChannelAim::held).orElse(1);
        if (slasher.level() instanceof ServerLevel level && SwingCadence.dueToSwing(slasher, held,
                level.getGameTime(), fewestTicks.evaluateInt(context), mostTicks.evaluateInt(context))) {
            swing(level, slasher, context);
        }
        return true;
    }

    /**
     * The area and band the step's params give every slash.
     *
     * @param context the step's context
     * @return the shape
     */
    private ReapSlash.Shape shapeOf(StepContext context) {
        return new ReapSlash.Shape(width.evaluate(context) * HALF, length.evaluate(context) * HALF,
                source.evaluate(context), backFrom.evaluate(context), backTo.evaluate(context),
                frontFrom.evaluate(context), Math.max(1, lifeTicks.evaluateInt(context)));
    }

    /**
     * What the step's params let each slash draw its own placing from.
     *
     * @param context the step's context
     * @return the placing's ranges
     */
    private ReapSlash.Thrown thrownOf(StepContext context) {
        return new ReapSlash.Thrown(drop.evaluate(context), nudge.evaluate(context),
                Math.toRadians(tilt.evaluate(context)), Math.toRadians(fan.evaluate(context)));
    }

    /**
     * Swings once with one quick swish, throwing a random few slashes level
     * along the way the slasher faces, each drawn on the watchers' clients
     * and set cutting for its life.
     *
     * @param level   the server level
     * @param slasher the entity slashing
     * @param context the step's context
     */
    private void swing(ServerLevel level, LivingEntity slasher, StepContext context) {
        Vec3 feet = slasher.position();
        Vec3 facing = Vec3.directionFromRotation(0, slasher.getYHeadRot());
        ReapSlash.Shape shape = shapeOf(context);
        ReapSlash.Thrown thrown = thrownOf(context);
        float cut = damage.evaluateFloat(context);
        int slashes = SwingCadence.slashesThisSwing(fewestSlashes.evaluateInt(context),
                mostSlashes.evaluateInt(context), slasher.getRandom());
        for (int count = 0; count < slashes; count++) {
            ReapSlash slash = ReapSlash.launch(facing, slasher.getEyeHeight(), thrown, shape, slasher.getRandom());
            BlockVisuals.sendToWatchers(level, BlockPos.containing(feet),
                    new ReapSlashPayload(slash, slasher.getId(), feet));
            SlashCuts.launch(level, new SlashCuts.Throw(slash, feet, slasher.getId()), level.getGameTime(), cut);
        }
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SWING_VOLUME, SWING_PITCH);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fewestTicks, mostTicks, fewestSlashes, mostSlashes, width, length, source, backFrom, backTo,
                frontFrom, drop, nudge, tilt, fan, lifeTicks, damage);
    }

    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof LivingEntity;
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET, HostCapability.CHANNEL);
    }
}
