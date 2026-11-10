package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.BladeFlights;
import com.mercuriusxeno.goo.ability.reap.ReapBlade;
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
 * hold's first tick and every cadence tick after, a leaf crescent blade
 * swings out from the player's chest, centred ahead within half a block,
 * lying nearly flat and pitched from level down to the dive that skims the
 * ground at its reach; it grows its width and its depth from a small skinny
 * start at one side, reaps and replants every ripe plant it covers and cuts
 * every living body it reaches but the slasher. Leaf Reap is
 * {@code slash every_ticks=3 travel=2 drift=1 width=2.5 depth=0.8
 * width_ticks=3 depth_ticks=5 drift_ticks=3 damage=1}.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param everyTicks the held ticks between one blade and the next
 * @param travel     how far a blade flies out while it grows, in blocks
 * @param drift      how far it drifts on once full grown, in blocks
 * @param width      its full width, side to side, in blocks
 * @param depth      its full depth, how far its middle bows away from the slasher, in blocks
 * @param widthTicks the ticks its width takes to grow full
 * @param depthTicks the ticks its depth takes to grow full
 * @param driftTicks the ticks it drifts and fades once full grown
 * @param damage     what each blade's cut deals
 */
public record SlashStep(Expr everyTicks, Expr travel, Expr drift, Expr width, Expr depth, Expr widthTicks,
                        Expr depthTicks, Expr driftTicks, Expr damage) implements Step {

    private static final String NAME = "slash";
    /** The share of the slasher's height the blades swing from: the chest, below the eye. */
    private static final double CHEST_SHARE = 0.6;
    private static final float SWING_VOLUME = 0.6f;
    private static final float SWING_PITCH = 1.3f;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlashStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("every_ticks").forGetter(SlashStep::everyTicks),
            Expr.CODEC.fieldOf("travel").forGetter(SlashStep::travel),
            Expr.CODEC.fieldOf("drift").forGetter(SlashStep::drift),
            Expr.CODEC.fieldOf("width").forGetter(SlashStep::width),
            Expr.CODEC.fieldOf("depth").forGetter(SlashStep::depth),
            Expr.CODEC.fieldOf("width_ticks").forGetter(SlashStep::widthTicks),
            Expr.CODEC.fieldOf("depth_ticks").forGetter(SlashStep::depthTicks),
            Expr.CODEC.fieldOf("drift_ticks").forGetter(SlashStep::driftTicks),
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
        if (slasher.level() instanceof ServerLevel level && swingsOn(held, everyTicks.evaluateInt(context))) {
            swing(level, slasher, shapeOf(context), damage.evaluateFloat(context));
        }
        return true;
    }

    /**
     * Whether a held tick swings a blade: the hold's first, and every cadence tick after it.
     *
     * @param held  the hold's tick count, 1 on its first tick
     * @param every the held ticks between one blade and the next
     * @return true on a swinging tick
     */
    static boolean swingsOn(int held, int every) {
        return every <= 1 || (held - 1) % every == 0;
    }

    private ReapBlade.Shape shapeOf(StepContext context) {
        return new ReapBlade.Shape(travel.evaluate(context), drift.evaluate(context), width.evaluate(context),
                depth.evaluate(context), Math.max(1, widthTicks.evaluateInt(context)),
                Math.max(1, depthTicks.evaluateInt(context)), Math.max(1, driftTicks.evaluateInt(context)));
    }

    /**
     * Swings one blade from the slasher's chest: drawn on the watchers'
     * clients, set flying to reap and cut, with the swish of a sweep.
     *
     * @param level   the server level
     * @param slasher the entity swinging
     * @param shape   the blade's shape
     * @param cut     what its cut deals
     */
    private static void swing(ServerLevel level, LivingEntity slasher, ReapBlade.Shape shape, float cut) {
        double chestHeight = slasher.getBbHeight() * CHEST_SHARE;
        Vec3 chest = slasher.position().add(0, chestHeight, 0);
        ReapBlade blade = ReapBlade.launch(chest, chestHeight, slasher.getYRot(), shape, slasher.getRandom());
        BlockVisuals.sendToWatchers(level, BlockPos.containing(chest), new ReapSlashPayload(blade));
        BladeFlights.launch(level, blade, level.getGameTime(), slasher.getId(), cut);
        level.playSound(null, chest.x, chest.y, chest.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                SWING_VOLUME, SWING_PITCH);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(everyTicks, travel, drift, width, depth, widthTicks, depthTicks, driftTicks, damage);
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
