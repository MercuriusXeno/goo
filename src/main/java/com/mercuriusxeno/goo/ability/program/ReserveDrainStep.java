package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hearts.ReserveDrain;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Drains the host's target player into a reserve behind the bar for one held
 * tick and finishes; the held stream runs it again each tick right click
 * stays down, so releasing stops the drain. Health is set lower with no
 * damage event, so no flinch, sound, red flash or invulnerability frames
 * follow. Vital Reserve is
 * {@code reserve_drain drain=0.05 ratio=0.5 cap=10 floor=0.5}, every amount in hearts.
 * reserve-hearts-sit-behind-the-bar
 *
 * @param drain the hearts one held tick drains
 * @param ratio the reserve hearts one drained heart banks
 * @param cap   the most reserve hearts that may stand
 * @param floor the health the drain never takes the player under, in hearts
 */
public record ReserveDrainStep(Expr drain, Expr ratio, Expr cap, Expr floor) implements Step {

    private static final String NAME = "reserve_drain";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ReserveDrainStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("drain").forGetter(ReserveDrainStep::drain),
            Expr.CODEC.fieldOf("ratio").forGetter(ReserveDrainStep::ratio),
            Expr.CODEC.fieldOf("cap").forGetter(ReserveDrainStep::cap),
            Expr.CODEC.fieldOf("floor").forGetter(ReserveDrainStep::floor)
    ).apply(inst, ReserveDrainStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ReserveDrainStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ReserveDrainStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        if (host.target() instanceof Player player) {
            ReserveDrain reserve = new ReserveDrain(drain.evaluateFloat(context), ratio.evaluateFloat(context),
                    cap.evaluateFloat(context), floor.evaluateFloat(context));
            ReserveDrain.Drawn drawn = reserve.draw(player.getData(GooAttachments.HEART_OVERLAY), player.getHealth());
            player.setData(GooAttachments.HEART_OVERLAY, drawn.overlay());
            player.setHealth(drawn.health());
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(drain, ratio, cap, floor);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
