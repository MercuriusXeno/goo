package com.mercuriusxeno.goo.ability.xeno;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import java.util.Optional;

/**
 * Keeps a mob's mutations working: the size, speed and health its
 * mutations sum to stand as permanent attribute modifiers, which save with
 * the mob, and its drops turn to the item its latest drop mutation names.
 * xeno-blob-mutates-the-struck
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class MutationEvents {

    private static final Identifier SIZE_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "mutation_size");
    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "mutation_speed");
    private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "mutation_health");

    private MutationEvents() {
    }

    /**
     * Works a mutation on a mob: records it, lays the attributes its
     * mutations sum to, and heals a health mutation's gain at once.
     *
     * @param mob      the struck mob
     * @param mutation the mutation the strike drew
     */
    public static void mutate(LivingEntity mob, Mutation mutation) {
        mob.setData(GooAttachments.MUTATIONS, mob.getData(GooAttachments.MUTATIONS).with(mutation));
        layAttributes(mob);
        if (mutation.kind() == Mutation.Kind.HEALTH) {
            mob.heal((float) mutation.amount());
        }
    }

    /**
     * Turns every drop of a mob carrying a drop mutation to that mutation's item.
     *
     * @param event the drops event
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (!mob.hasData(GooAttachments.MUTATIONS)) {
            return;
        }
        dropItemOf(mob.getData(GooAttachments.MUTATIONS)).ifPresent(item -> {
            for (ItemEntity drop : event.getDrops()) {
                drop.setItem(new ItemStack(item, drop.getItem().getCount()));
            }
        });
    }

    /**
     * @param mutations a mob's mutations
     * @return the item its latest drop mutation names, or empty for none
     */
    static Optional<Item> dropItemOf(Mutations mutations) {
        return mutations.worn().reversed().stream()
                .filter(mutation -> mutation.kind() == Mutation.Kind.DROP)
                .flatMap(mutation -> mutation.item().stream())
                .flatMap(id -> BuiltInRegistries.ITEM.get(id).stream())
                .map(Holder::value)
                .findFirst();
    }

    private static void layAttributes(LivingEntity mob) {
        Mutations mutations = mob.getData(GooAttachments.MUTATIONS);
        lay(mob, Attributes.SCALE, SIZE_ID, mutations.total(Mutation.Kind.SIZE),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        lay(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, mutations.total(Mutation.Kind.SPEED),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        lay(mob, Attributes.MAX_HEALTH, HEALTH_ID, mutations.total(Mutation.Kind.HEALTH),
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void lay(LivingEntity mob, Holder<Attribute> attribute, Identifier id, double amount,
                            AttributeModifier.Operation operation) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0) {
            instance.removeModifier(id);
        } else {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }
}
