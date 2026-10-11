package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.feed.Feeding;
import com.mercuriusxeno.goo.entity.FeedPile;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Jelly's Feed where its blob lands: on a struck animal it counts as
 * feeding, setting an adult in love or growing a baby a stage; on the ground
 * it lays a feed that draws the animals and mobs within its radius, hostiles
 * that reach it together fighting for the fight time. Jelly Feed is
 * {@code feed radius=12 fight_ticks=100}.
 * feed-blob-feeds-and-draws-mobs
 *
 * @param radius     the blocks out to which a laid feed draws mobs
 * @param fightTicks the ticks hostiles that reach a laid feed together fight for
 */
public record FeedStep(Expr radius, Expr fightTicks) implements Step {

    private static final String NAME = "feed";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FeedStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("radius").forGetter(FeedStep::radius),
            Expr.CODEC.fieldOf("fight_ticks").forGetter(FeedStep::fightTicks)
    ).apply(inst, FeedStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FeedStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FeedStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        StepHost host = context.host();
        if (host instanceof TargetHost struck) {
            if (struck.target() instanceof Animal animal
                    && Feeding.feed(animal, struck.thrower() instanceof Player player ? player : null)) {
                Feeding.crumbsAndHearts((ServerLevel) animal.level(), animal);
            }
        } else if (host instanceof AnchoredWorldHost landing) {
            FeedPile.lay(landing.level(), landing.anchor(), radius.evaluate(context), fightTicks.evaluateInt(context));
        }
        return true;
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