package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.nectar.Feeding;
import com.mercuriusxeno.goo.entity.NectarPile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Jelly's Nectar where its blob lands: on a struck animal it counts as
 * feeding, setting an adult in love or growing a baby a stage; on a struck
 * slime it grows the slime a size step; on the ground it lays a nectar that
 * draws the animals and mobs within its radius, hostiles that reach it
 * together fighting for the fight time. Jelly's Nectar is
 * {@code nectar radius=12 fight_ticks=100}.
 * feed-blob-feeds-and-draws-mobs
 * slimes-grow-on-jelly-feed
 *
 * @param radius     the blocks out to which a laid nectar draws mobs
 * @param fightTicks the ticks hostiles that reach a laid nectar together fight for
 */
public record NectarStep(Expr radius, Expr fightTicks) implements Step {

    private static final String NAME = "nectar";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<NectarStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("radius").forGetter(NectarStep::radius),
            Expr.CODEC.fieldOf("fight_ticks").forGetter(NectarStep::fightTicks)
    ).apply(inst, NectarStep::new));

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
        StepHost host = context.host();
        if (host instanceof TargetHost struck) {
            feedStruck(struck);
        } else if (host instanceof AnchoredWorldHost landing) {
            NectarPile.lay(landing.level(), landing.anchor(), radius.evaluate(context), fightTicks.evaluateInt(context));
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
     * @return true where a struck blob feeds it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof Slime || entity instanceof Animal;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, fightTicks);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }
}