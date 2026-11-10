package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.BladeFlights;
import com.mercuriusxeno.goo.ability.reap.ReapBlade;
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
 * Reap's step, run on the player each tick of the held channel: on the
 * hold's first tick, and then a random few ticks after each blade, a leaf
 * crescent blade swings out along the player's look, centred on the aim
 * within half a block to the side and up or down and lying nearly flat; it
 * forms ahead of the eye from a small skinny start at one side, grows its
 * width and its depth at their own rates, reaps and replants every ripe plant
 * it covers and cuts every living body it reaches but the slasher. Leaf Reap
 * is {@code slash fewest_ticks=2 most_ticks=3 start=1.5 drift=1 width=2.5
 * depth=0.8 width_ticks=6 depth_ticks=8 fade_ticks=3 damage=0.5}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param fewestTicks the fewest held ticks from one blade to the next
 * @param mostTicks   the most held ticks from one blade to the next
 * @param start       how far ahead of the eye a blade forms, in blocks
 * @param drift       how far it drifts on over its life, in blocks
 * @param width       its full width, side to side, in blocks
 * @param depth       its full depth, how far its middle bows away from the slasher, in blocks
 * @param widthTicks  the ticks its width takes to grow full
 * @param depthTicks  the ticks its depth takes to grow full
 * @param fadeTicks   the ticks it fades once full grown
 * @param damage      what each blade's cut deals
 */
public record SlashStep(Expr fewestTicks, Expr mostTicks, Expr start, Expr drift, Expr width, Expr depth,
                        Expr widthTicks, Expr depthTicks, Expr fadeTicks, Expr damage) implements Step {

    private static final String NAME = "slash";
    private static final float SWING_VOLUME = 0.4f;
    private static final float SWING_PITCH = 1.7f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("fewest_ticks").forGetter(SlashStep::fewestTicks),
            Expr.CODEC.fieldOf("most_ticks").forGetter(SlashStep::mostTicks),
            Expr.CODEC.fieldOf("start").forGetter(SlashStep::start),
            Expr.CODEC.fieldOf("drift").forGetter(SlashStep::drift),
            Expr.CODEC.fieldOf("width").forGetter(SlashStep::width),
            Expr.CODEC.fieldOf("depth").forGetter(SlashStep::depth),
            Expr.CODEC.fieldOf("width_ticks").forGetter(SlashStep::widthTicks),
            Expr.CODEC.fieldOf("depth_ticks").forGetter(SlashStep::depthTicks),
            Expr.CODEC.fieldOf("fade_ticks").forGetter(SlashStep::fadeTicks),
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
            swing(level, slasher, shapeOf(context), damage.evaluateFloat(context));
        }
        return true;
    }

    private ReapBlade.Shape shapeOf(StepContext context) {
        return new ReapBlade.Shape(start.evaluate(context), drift.evaluate(context), width.evaluate(context),
                depth.evaluate(context), Math.max(1, widthTicks.evaluateInt(context)),
                Math.max(1, depthTicks.evaluateInt(context)), Math.max(1, fadeTicks.evaluateInt(context)));
    }

    /**
     * Swings one blade along the slasher's look: drawn on the watchers'
     * clients, set swinging to reap and cut, with a quick swish of a sweep.
     *
     * @param level   the server level
     * @param slasher the entity swinging
     * @param shape   the blade's shape
     * @param cut     what its cut deals
     */
    private static void swing(ServerLevel level, LivingEntity slasher, ReapBlade.Shape shape, float cut) {
        Vec3 eye = slasher.getEyePosition();
        ReapBlade blade = ReapBlade.launch(eye, slasher.getLookAngle(), shape, slasher.getRandom());
        BlockVisuals.sendToWatchers(level, BlockPos.containing(eye), new ReapSlashPayload(blade));
        BladeFlights.launch(level, blade, level.getGameTime(), slasher.getId(), cut);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SWING_VOLUME, SWING_PITCH);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fewestTicks, mostTicks, start, drift, width, depth, widthTicks, depthTicks, fadeTicks,
                damage);
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
