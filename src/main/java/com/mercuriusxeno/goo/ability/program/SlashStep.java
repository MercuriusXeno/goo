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
 * the Crissaegrim does in Symphony of the Night, its frames read: on the
 * hold's first tick, and then a random few ticks after each swing, the
 * player swings with one swish, throwing a random few thick leaf crescents
 * out ahead along the look, each lying nearly flat under the line of sight
 * so its face shows and it stays below the eye line, hollow toward the
 * slasher and bulging away, sized at random with its width, thickness and
 * bulge together, as often small or huge as middling; each appears thick and
 * bright, then thins to a sliver and dims as it travels outward; and every
 * ripe plant inside the box a slash cuts is reaped and replanted and every
 * living body there but the slasher cut once. Leaf Reap is {@code slash
 * fewest_ticks=2 most_ticks=3 fewest_slashes=1 most_slashes=2 near=1
 * narrowest=0.8 widest=6 thinnest=0.3 thickest=2.0 bulge=0.55
 * drop_least=0.3 drop_most=0.6 tilt=3 drift=2.5 life_ticks=6 damage=1}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param fewestTicks   the fewest held ticks from one swing to the next
 * @param mostTicks     the most held ticks from one swing to the next
 * @param fewestSlashes the fewest slashes a swing throws
 * @param mostSlashes   the most slashes a swing throws
 * @param near        how far out along the look a slash's tips stand, in blocks
 * @param narrowest   the smallest slash's width, tip to tip, in blocks
 * @param widest      the largest slash's width, in blocks
 * @param thinnest    the smallest slash's thickness, in blocks
 * @param thickest    the largest slash's thickness, in blocks
 * @param bulge       how far its middle bows out beyond its tips, as a share of its width
 * @param dropLeast   the least its plane stands under the line of sight, in blocks
 * @param dropMost    the most its plane stands under the line of sight, in blocks
 * @param tilt        the farthest it tilts from flat either way, in degrees
 * @param drift       how far it travels outward over its life, thinning, in blocks
 * @param lifeTicks   the ticks from a slash appearing to its dimming away
 * @param damage      what each slash's cut deals
 */
public record SlashStep(Expr fewestTicks, Expr mostTicks, Expr fewestSlashes, Expr mostSlashes, Expr near,
                        Expr narrowest, Expr widest, Expr thinnest, Expr thickest, Expr bulge, Expr dropLeast,
                        Expr dropMost, Expr tilt, Expr drift, Expr lifeTicks, Expr damage) implements Step {

    private static final String NAME = "slash";
    private static final float SWING_VOLUME = 0.4f;
    private static final float SWING_PITCH = 1.7f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("fewest_ticks").forGetter(SlashStep::fewestTicks),
            Expr.CODEC.fieldOf("most_ticks").forGetter(SlashStep::mostTicks),
            Expr.CODEC.fieldOf("fewest_slashes").forGetter(SlashStep::fewestSlashes),
            Expr.CODEC.fieldOf("most_slashes").forGetter(SlashStep::mostSlashes),
            Expr.CODEC.fieldOf("near").forGetter(SlashStep::near),
            Expr.CODEC.fieldOf("narrowest").forGetter(SlashStep::narrowest),
            Expr.CODEC.fieldOf("widest").forGetter(SlashStep::widest),
            Expr.CODEC.fieldOf("thinnest").forGetter(SlashStep::thinnest),
            Expr.CODEC.fieldOf("thickest").forGetter(SlashStep::thickest),
            Expr.CODEC.fieldOf("bulge").forGetter(SlashStep::bulge),
            Expr.CODEC.fieldOf("drop_least").forGetter(SlashStep::dropLeast),
            Expr.CODEC.fieldOf("drop_most").forGetter(SlashStep::dropMost),
            Expr.CODEC.fieldOf("tilt").forGetter(SlashStep::tilt),
            Expr.CODEC.fieldOf("drift").forGetter(SlashStep::drift),
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
     * The sizes and timing the step's params draw slashes from.
     *
     * @param context the step's context
     * @return the shape
     */
    private ReapSlash.Shape shapeOf(StepContext context) {
        return new ReapSlash.Shape(near.evaluate(context), narrowest.evaluate(context), widest.evaluate(context),
                thinnest.evaluate(context), thickest.evaluate(context), bulge.evaluate(context),
                dropLeast.evaluate(context), dropMost.evaluate(context), Math.toRadians(tilt.evaluate(context)),
                drift.evaluate(context), Math.max(1, lifeTicks.evaluateInt(context)));
    }

    /**
     * Swings once with one quick swish, throwing a random few slashes, each
     * drawn on the watchers' clients with its box set cutting for its life.
     *
     * @param level   the server level
     * @param slasher the entity slashing
     * @param context the step's context
     */
    private void swing(ServerLevel level, LivingEntity slasher, StepContext context) {
        Vec3 eye = slasher.getEyePosition();
        ReapSlash.Shape shape = shapeOf(context);
        float cut = damage.evaluateFloat(context);
        int slashes = SwingCadence.slashesThisSwing(fewestSlashes.evaluateInt(context),
                mostSlashes.evaluateInt(context), slasher.getRandom());
        for (int thrown = 0; thrown < slashes; thrown++) {
            ReapSlash slash = ReapSlash.launch(eye, slasher.getLookAngle(), shape, slasher.getRandom());
            BlockVisuals.sendToWatchers(level, BlockPos.containing(eye), new ReapSlashPayload(slash));
            SlashCuts.launch(level, slash.box(), level.getGameTime(), slash.lifeTicks(), slasher.getId(), cut);
        }
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SWING_VOLUME, SWING_PITCH);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fewestTicks, mostTicks, fewestSlashes, mostSlashes, near, narrowest, widest, thinnest,
                thickest, bulge, dropLeast, dropMost, tilt, drift, lifeTicks, damage);
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
