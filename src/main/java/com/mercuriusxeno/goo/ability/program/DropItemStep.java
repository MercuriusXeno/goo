package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Spawns an item stack at the host's target and finishes. The item is
 * named by id and resolved when the step runs; rock petrify drops
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
    private static final String LOG_UNKNOWN_ITEM = "Drop step names item {}, which no registry holds";
    private static final String LOG_NO_SPAWN_EGG = "Drop step names the spawn egg of {}, which has none";
    private static final Set<EntityType<?>> TYPES_WARNED_EGGLESS = ConcurrentHashMap.newKeySet();

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
        LivingEntity target = context.host().target();
        int stackSize = count.evaluateInt(context);
        if (SPAWN_EGG.equals(item)) {
            dropOwnSpawnEgg(target, stackSize);
            return true;
        }
        Optional<Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.get(item);
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_ITEM, item);
            return true;
        }
        target.spawnAtLocation((ServerLevel) target.level(), new ItemStack(holder.get(), stackSize));
        return true;
    }

    /**
     * Drops the spawn egg of the target's type, warning once per type
     * that has none.
     *
     * @param target    the entity whose egg drops
     * @param stackSize the stack size
     */
    private static void dropOwnSpawnEgg(LivingEntity target, int stackSize) {
        EntityType<?> type = target.getType();
        Optional<Holder<Item>> egg = SpawnEggItem.byId(type);
        if (egg.isEmpty()) {
            if (TYPES_WARNED_EGGLESS.add(type)) {
                Goo.LOGGER.warn(LOG_NO_SPAWN_EGG, EntityType.getKey(type));
            }
            return;
        }
        target.spawnAtLocation((ServerLevel) target.level(), new ItemStack(egg.get(), stackSize));
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
