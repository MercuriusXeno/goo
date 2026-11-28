package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BoneBladeEvents;
import com.mercuriusxeno.goo.network.BoneBlades;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Osseous's Blade step: a hold of right click carried through its whole charge lays a bone shotel in the casting
 * hand, which strikes for the damage named here and drains the drain named here from the caster's Osseous with each
 * strike; a hold let go short of its charge lays nothing.
 * blade-forms-a-bone-shotel-in-hand
 *
 * @param damage the damage each strike landed with the shotel deals
 * @param drain  the whole mB of Osseous each strike drains
 */
public record BladeStep(float damage, int drain) implements Step {

    private static final String NAME = "blade";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_DRAIN = "drain";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BladeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.floatRange(0f, Float.MAX_VALUE).fieldOf(FIELD_DAMAGE).forGetter(BladeStep::damage),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_DRAIN).forGetter(BladeStep::drain)
    ).apply(inst, BladeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BladeStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The blade step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the blade step, empty where the program lays no shotel
     */
    public static Optional<BladeStep> in(List<Step> program) {
        return program.stream().filter(BladeStep.class::isInstance).map(BladeStep.class::cast).findFirst();
    }

    @Override
    public StepType<BladeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        float charge = (float) context.read(HostVariables.CHARGE).orElse(0);
        if (host.target() instanceof ServerPlayer player && BoneBlades.formsAt(charge)) {
            BoneBladeEvents.latchOn(player, this);
        }
        return true;
    }

    /**
     * A shotel forms in a player's hand.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof ServerPlayer;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
