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
 * and then a random few ticks after each slash, a fixed leaf crescent appears
 * ahead of the eye below the eye line, facing the slasher, in the next of its
 * three forms; a bright band sweeps its arc from one tip to the other and the
 * arc wipes away behind it, with one swish; and every ripe plant inside the
 * box it cuts is reaped and replanted and every living body there but the
 * slasher cut once. Leaf Reap is {@code slash fewest_ticks=2 most_ticks=3
 * radius=1.5 span=100 thickness=0.45 drop=0.75 tilt=25 life_ticks=4
 * box_width=1.4 box_height=1.2 damage=0.5}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param fewestTicks the fewest held ticks from one slash to the next
 * @param mostTicks   the most held ticks from one slash to the next
 * @param radius      how far the crescent's outer edge stands from the slasher, in blocks
 * @param span        the angle its arc spans around the slasher, in degrees
 * @param thickness   how thick its middle is, tapering to its tips, in blocks
 * @param drop        how far below the eye its arc's hub stands, in blocks
 * @param tilt        how far a diagonal form slopes from flat, in degrees
 * @param lifeTicks   the ticks from a slash appearing to its wiping away
 * @param boxWidth    the width and depth of the box a slash cuts, in blocks
 * @param boxHeight   the height of the box, in blocks
 * @param damage      what each slash's cut deals
 */
public record SlashStep(Expr fewestTicks, Expr mostTicks, Expr radius, Expr span, Expr thickness, Expr drop,
                        Expr tilt, Expr lifeTicks, Expr boxWidth, Expr boxHeight, Expr damage) implements Step {

    private static final String NAME = "slash";
    private static final float SWING_VOLUME = 0.4f;
    private static final float SWING_PITCH = 1.7f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("fewest_ticks").forGetter(SlashStep::fewestTicks),
            Expr.CODEC.fieldOf("most_ticks").forGetter(SlashStep::mostTicks),
            Expr.CODEC.fieldOf("radius").forGetter(SlashStep::radius),
            Expr.CODEC.fieldOf("span").forGetter(SlashStep::span),
            Expr.CODEC.fieldOf("thickness").forGetter(SlashStep::thickness),
            Expr.CODEC.fieldOf("drop").forGetter(SlashStep::drop),
            Expr.CODEC.fieldOf("tilt").forGetter(SlashStep::tilt),
            Expr.CODEC.fieldOf("life_ticks").forGetter(SlashStep::lifeTicks),
            Expr.CODEC.fieldOf("box_width").forGetter(SlashStep::boxWidth),
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
     * The shape the step's params give a slash.
     *
     * @param context the step's context
     * @return the shape
     */
    private ReapSlash.Shape shapeOf(StepContext context) {
        return new ReapSlash.Shape(radius.evaluate(context), Math.toRadians(span.evaluate(context)),
                thickness.evaluate(context), drop.evaluate(context), Math.toRadians(tilt.evaluate(context)),
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
        ReapSlash slash = ReapSlash.launch(eye, slasher.getLookAngle(), SwingCadence.takeForm(slasher),
                shapeOf(context));
        BlockVisuals.sendToWatchers(level, BlockPos.containing(eye), new ReapSlashPayload(slash));
        SlashCuts.launch(level, slash.box(boxWidth.evaluate(context), boxHeight.evaluate(context)),
                level.getGameTime(), slash.lifeTicks(), slasher.getId(), damage.evaluateFloat(context));
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SWING_VOLUME, SWING_PITCH);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fewestTicks, mostTicks, radius, span, thickness, drop, tilt, lifeTicks, boxWidth,
                boxHeight, damage);
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
