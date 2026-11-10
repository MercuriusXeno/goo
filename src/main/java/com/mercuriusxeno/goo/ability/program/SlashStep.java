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
 * and then a random few ticks after each slash, a fixed leaf crescent stands
 * upright across the view, facing the slasher, out along the look and
 * centred just under the aim, at its own tilt, sweep, sideways place and
 * size; a bright band sweeps its arc from one tip to the other and the arc
 * wipes away behind it, with one swish; and every ripe plant inside the box
 * it cuts is reaped and replanted and every living body there but the
 * slasher cut once. Leaf Reap is {@code slash fewest_ticks=2 most_ticks=3
 * distance=2.5 narrowest=2.5 widest=3.5 thinnest=0.6 thickest=1.0
 * sideways=0.6 drop=0.3 tilt=35 life_ticks=4 box_near=1 box_far=3.5
 * box_height=1.5 damage=0.5}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param fewestTicks the fewest held ticks from one slash to the next
 * @param mostTicks   the most held ticks from one slash to the next
 * @param distance    how far out along the look a slash stands, in blocks
 * @param narrowest   the smallest slash's width, in blocks
 * @param widest      the largest slash's width, in blocks
 * @param thinnest    the smallest slash's thickness, in blocks
 * @param thickest    the largest slash's thickness, in blocks
 * @param sideways    the farthest a slash stands to either side of the aim, in blocks
 * @param drop        how far under the aim a slash's middle stands, in blocks
 * @param tilt        the farthest a slash tilts from level either way, in degrees
 * @param lifeTicks   the ticks from a slash appearing to its wiping away
 * @param boxNear     how far ahead of the eye the box a slash cuts starts, in blocks
 * @param boxFar      how far ahead of the eye it reaches, in blocks
 * @param boxHeight   its height, in blocks
 * @param damage      what each slash's cut deals
 */
public record SlashStep(Expr fewestTicks, Expr mostTicks, Expr distance, Expr narrowest, Expr widest,
                        Expr thinnest, Expr thickest, Expr sideways, Expr drop, Expr tilt, Expr lifeTicks,
                        Expr boxNear, Expr boxFar, Expr boxHeight, Expr damage) implements Step {

    private static final String NAME = "slash";
    private static final float SWING_VOLUME = 0.4f;
    private static final float SWING_PITCH = 1.7f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("fewest_ticks").forGetter(SlashStep::fewestTicks),
            Expr.CODEC.fieldOf("most_ticks").forGetter(SlashStep::mostTicks),
            Expr.CODEC.fieldOf("distance").forGetter(SlashStep::distance),
            Expr.CODEC.fieldOf("narrowest").forGetter(SlashStep::narrowest),
            Expr.CODEC.fieldOf("widest").forGetter(SlashStep::widest),
            Expr.CODEC.fieldOf("thinnest").forGetter(SlashStep::thinnest),
            Expr.CODEC.fieldOf("thickest").forGetter(SlashStep::thickest),
            Expr.CODEC.fieldOf("sideways").forGetter(SlashStep::sideways),
            Expr.CODEC.fieldOf("drop").forGetter(SlashStep::drop),
            Expr.CODEC.fieldOf("tilt").forGetter(SlashStep::tilt),
            Expr.CODEC.fieldOf("life_ticks").forGetter(SlashStep::lifeTicks),
            Expr.CODEC.fieldOf("box_near").forGetter(SlashStep::boxNear),
            Expr.CODEC.fieldOf("box_far").forGetter(SlashStep::boxFar),
            Expr.CODEC.fieldOf("box_height").forGetter(SlashStep::boxHeight),
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
            slash(level, slasher, context);
        }
        return true;
    }

    /**
     * The sizes and timing the step's params draw slashes from.
     *
     * @param context the step's context
     * @return the shape
     */
    private ReapSlash.Shape shapeOf(StepContext context) {
        return new ReapSlash.Shape(distance.evaluate(context), narrowest.evaluate(context), widest.evaluate(context),
                thinnest.evaluate(context), thickest.evaluate(context), sideways.evaluate(context),
                drop.evaluate(context), Math.toRadians(tilt.evaluate(context)),
                Math.max(1, lifeTicks.evaluateInt(context)));
    }

    /**
     * Slashes once: the crescent drawn on the watchers' clients, its box set
     * cutting for the slash's life, and one quick swish.
     *
     * @param level   the server level
     * @param slasher the entity slashing
     * @param context the step's context
     */
    private void slash(ServerLevel level, LivingEntity slasher, StepContext context) {
        Vec3 eye = slasher.getEyePosition();
        ReapSlash slash = ReapSlash.launch(eye, slasher.getLookAngle(), shapeOf(context), slasher.getRandom());
        BlockVisuals.sendToWatchers(level, BlockPos.containing(eye), new ReapSlashPayload(slash));
        SlashCuts.launch(level, slash.box(boxNear.evaluate(context), boxFar.evaluate(context),
                boxHeight.evaluate(context)), level.getGameTime(), slash.lifeTicks(), slasher.getId(),
                damage.evaluateFloat(context));
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SWING_VOLUME, SWING_PITCH);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fewestTicks, mostTicks, distance, narrowest, widest, thinnest, thickest, sideways, drop,
                tilt, lifeTicks, boxNear, boxFar, boxHeight, damage);
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
