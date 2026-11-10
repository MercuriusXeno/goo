package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.BladeFlights;
import com.mercuriusxeno.goo.ability.reap.BladeSizes;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reap's step, run on the player each tick of the held channel: on the
 * hold's first tick, and then a random few ticks after each swing, the
 * player swings, one sweep and one attack, throwing a random few leaf
 * crescent blades a tick apart along the look. Each blade stands centred on
 * the aim within a block to the side and just under it, heads within 22.5
 * degrees of the look, lies nearly flat, forms ahead of the eye from a small
 * skinny start at one side and grows its width and its depth at their own
 * rates to one size drawn at random, a narrow blade skinny and a wide one
 * deep; every ripe plant a blade covers is reaped and
 * replanted, and every living body the swing's blades reach but the slasher
 * is cut once for the swing. Leaf Reap is {@code slash fewest_ticks=2
 * most_ticks=3 fewest_blades=1 most_blades=2 start=0.75 drift=3 width=1.2
 * widest=3.2 depth=0.5 deepest=1.8 width_ticks=6 depth_ticks=8 fade_ticks=6
 * damage=0.5}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param fewestTicks  the fewest held ticks from one swing to the next
 * @param mostTicks    the most held ticks from one swing to the next
 * @param fewestBlades the fewest blades a swing throws
 * @param mostBlades   the most blades a swing throws
 * @param start        how far ahead of the eye a blade forms, in blocks
 * @param drift        how far it drifts on over its life, in blocks
 * @param width        the narrowest blade's full width, side to side, in blocks
 * @param widest       the widest blade's full width, in blocks
 * @param depth        the narrowest blade's full depth, how far its middle bows away from the slasher, in blocks
 * @param deepest      the widest blade's full depth, in blocks
 * @param widthTicks   the ticks its width takes to grow full
 * @param depthTicks   the ticks its depth takes to grow full
 * @param fadeTicks    the ticks it decays to nothing once full grown
 * @param damage       what each swing's cut deals
 */
public record SlashStep(Expr fewestTicks, Expr mostTicks, Expr fewestBlades, Expr mostBlades, Expr start,
                        Expr drift, Expr width, Expr widest, Expr depth, Expr deepest, Expr widthTicks,
                        Expr depthTicks, Expr fadeTicks, Expr damage) implements Step {

    private static final String NAME = "slash";
    private static final float SWING_VOLUME = 0.4f;
    private static final float SWING_PITCH = 1.7f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("fewest_ticks").forGetter(SlashStep::fewestTicks),
            Expr.CODEC.fieldOf("most_ticks").forGetter(SlashStep::mostTicks),
            Expr.CODEC.fieldOf("fewest_blades").forGetter(SlashStep::fewestBlades),
            Expr.CODEC.fieldOf("most_blades").forGetter(SlashStep::mostBlades),
            Expr.CODEC.fieldOf("start").forGetter(SlashStep::start),
            Expr.CODEC.fieldOf("drift").forGetter(SlashStep::drift),
            Expr.CODEC.fieldOf("width").forGetter(SlashStep::width),
            Expr.CODEC.fieldOf("widest").forGetter(SlashStep::widest),
            Expr.CODEC.fieldOf("depth").forGetter(SlashStep::depth),
            Expr.CODEC.fieldOf("deepest").forGetter(SlashStep::deepest),
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
            swing(level, slasher, bladesOf(context, slasher), damage.evaluateFloat(context));
        }
        return true;
    }

    /**
     * The blades one swing throws along the slasher's look, each at its own
     * random spread, tilt, start side and depth.
     *
     * @param context the step's context
     * @param slasher the entity swinging
     * @return the swing's blades, in the order they fly
     */
    private List<ReapBlade> bladesOf(StepContext context, LivingEntity slasher) {
        RandomSource random = slasher.getRandom();
        int count = SwingCadence.bladesThisSwing(fewestBlades.evaluateInt(context), mostBlades.evaluateInt(context),
                random);
        BladeSizes sizes = new BladeSizes(width.evaluate(context), widest.evaluate(context), depth.evaluate(context),
                deepest.evaluate(context));
        List<ReapBlade> blades = new ArrayList<>(count);
        for (int blade = 0; blade < count; blade++) {
            double size = random.nextDouble();
            ReapBlade.Shape shape = new ReapBlade.Shape(start.evaluate(context), drift.evaluate(context),
                    sizes.widthAt(size), sizes.depthAt(size), Math.max(1, widthTicks.evaluateInt(context)),
                    Math.max(1, depthTicks.evaluateInt(context)), Math.max(1, fadeTicks.evaluateInt(context)));
            blades.add(ReapBlade.launch(slasher.getEyePosition(), slasher.getLookAngle(), slasher.getKnownMovement(), shape,
                    random));
        }
        return blades;
    }

    /**
     * Swings once: the swing's blades drawn on the watchers' clients a tick
     * apart and set flying to reap and cut as one attack, with one quick
     * swish of a sweep.
     *
     * @param level   the server level
     * @param slasher the entity swinging
     * @param blades  the swing's blades
     * @param cut     what the swing's cut deals
     */
    private static void swing(ServerLevel level, LivingEntity slasher, List<ReapBlade> blades, float cut) {
        Vec3 eye = slasher.getEyePosition();
        for (int blade = 0; blade < blades.size(); blade++) {
            BlockVisuals.sendToWatchers(level, BlockPos.containing(eye), new ReapSlashPayload(blades.get(blade), blade));
        }
        BladeFlights.launchSwing(level, blades, level.getGameTime(), slasher.getId(), cut);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SWING_VOLUME, SWING_PITCH);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fewestTicks, mostTicks, fewestBlades, mostBlades, start, drift, width, widest, depth,
                deepest, widthTicks, depthTicks, fadeTicks, damage);
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
