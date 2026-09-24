package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spawns an item stack at the host's target and finishes. The item is
 * named by id and the host resolves it; rock petrify drops
 * {@code drop_item item=minecraft:cobblestone count="1 + random(3)"}.
 * The id {@code spawn_egg} names the target's own spawn egg, which aeon's
 * ritual drops (decision aeon-mob-ritual-drops-spawn-egg).
 *
 * @param item  the item id
 * @param count the stack size, evaluated when the step runs
 */
public record DropItemStep(Identifier item, Expr count) implements Step {

    /**
     * The item id that names the target's own spawn egg.
     */
    public static final Identifier SPAWN_EGG = Identifier.withDefaultNamespace("spawn_egg");

    private static final String NAME = "drop_item";
    private static final String FIELD_ITEM = "item";
    private static final String FIELD_COUNT = "count";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DropItemStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ITEM).forGetter(DropItemStep::item),
            Expr.CODEC.optionalFieldOf(FIELD_COUNT, Expr.literal(1)).forGetter(DropItemStep::count)
    ).apply(inst, DropItemStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DropItemStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DropItemStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().dropItemAtTarget(item, count.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(count);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
