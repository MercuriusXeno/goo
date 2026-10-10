package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs the channeling caster's heart regrow clock ahead each held tick, so
 * Barkskin's bark regrows faster while Growth streams; Growth is
 * {@code hasten_regrow ticks=2}, the bark regrowing three times as fast.
 * growth-breeze-ticks-plants
 * barkskin-bark-hearts-thorn-and-burn
 *
 * @param ticks the ticks the regrow clock runs ahead by each held tick
 */
public record HastenRegrowStep(Expr ticks) implements Step {

    private static final String NAME = "hasten_regrow";
    private static final String FIELD_TICKS = "ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HastenRegrowStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_TICKS).forGetter(HastenRegrowStep::ticks)
    ).apply(inst, HastenRegrowStep::new));

    /**
     * The registered type.
     */
    public static final StepType<HastenRegrowStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HastenRegrowStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        if (caster.hasData(GooAttachments.HEART_OVERLAY)) {
            HeartOverlay overlay = caster.getData(GooAttachments.HEART_OVERLAY);
            HeartOverlay hastened = overlay.hastened(ticks.evaluateInt(context));
            if (hastened != overlay) {
                caster.setData(GooAttachments.HEART_OVERLAY, hastened);
            }
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(ticks);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.TARGET);
    }
}
