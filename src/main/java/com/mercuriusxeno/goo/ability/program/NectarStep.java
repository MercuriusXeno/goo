package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.nectar.Feeding;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Jelly's Nectar on a living thing one of its drips strikes: a struck animal
 * is fed, an adult set in love or a baby grown a stage, and a struck slime
 * grows a size step, the drip spent on it. Jelly's Nectar is {@code nectar}.
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 * slimes-grow-on-jelly-feed
 */
public record NectarStep() implements Step {

    private static final String NAME = "nectar";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<NectarStep> CODEC = MapCodec.unit(NectarStep::new);

    /**
     * The registered type.
     */
    public static final StepType<NectarStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<NectarStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.host() instanceof TargetHost struck) {
            feedStruck(struck);
        }
        return true;
    }

    private void feedStruck(TargetHost struck) {
        LivingEntity target = struck.target();
        if (!takesEntity(target)) {
            return;
        }
        if (target instanceof Slime slime) {
            Feeding.feed(slime);
            Feeding.crumbsAndHearts((ServerLevel) slime.level(), slime);
        } else if (Feeding.feed((Animal) target, struck.thrower() instanceof Player player ? player : null)) {
            Feeding.crumbsAndHearts((ServerLevel) target.level(), target);
        }
    }

    /**
     * Nectar takes a slime, which it grows, and an animal, which it feeds.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where a struck drip feeds it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof Slime || entity instanceof Animal;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }
}
