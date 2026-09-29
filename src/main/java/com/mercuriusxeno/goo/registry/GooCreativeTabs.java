package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.fluid.GooBucketItem;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Comparator;

/**
 * Creative mode tab registration. Shows machines, intermediates, and for
 * every type the goo type registry holds, a datapack's included, one goo,
 * two sample gooStacks, a bucket and the three chrysm tiers (decision generic-goo-items).
 */
public class GooCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Goo.MODID);

    private static final int SAMPLE_GOO_VOLUME = 1_000_000;
    private static final int LARGE_GOO_VOLUME = 1_000_000_000;

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GOO_TAB =
        TABS.register("goo_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.goo"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> GooStacks.createForOutput(GooTypes.ENDER, GooStacks.THOUSAND))
            .displayItems((params, output) -> {
                // Machines
                output.accept(GooItems.CRUCIBLE.get());
                output.accept(GooItems.CANISTER.get());
                output.accept(GooItems.HUB.get());
                output.accept(GooItems.PLEXER.get());
                output.accept(GooItems.REACTOR.get());
                output.accept(GooItems.VAT.get());
                output.accept(GooItems.TAP.get());
                output.accept(GooItems.CRYSTALLIZER.get());
                // Intermediates
                output.accept(GooItems.CHORAL_GASKET.get());
                output.accept(GooItems.CHORAL_TUNER.get());
                output.accept(GooItems.EXORITE.get());
                output.accept(GooItems.EXORITE_UPGRADE_SMITHING_TEMPLATE.get());
                output.accept(GooItems.EXORITE_BARS.get());
                // Equipment
                output.accept(GooItems.GOO_GLOVE.get());
                output.accept(GooItems.GOO_GAUNTLET.get());
                output.accept(GooItems.EXO_GAUNTLET.get());
                GooItems.EXORITE_SET.forEach(piece -> output.accept(piece.get()));
                params.holders().lookupOrThrow(GooTypes.REGISTRY).listElements()
                        .map(Holder.Reference::key)
                        .sorted(Comparator.comparing(ResourceKey::identifier))
                        .forEach(key -> acceptType(output, key));
            })
            .build()
        );

    /**
     * Offers one type's 1-goo, 1K-goo and 1M-gooStacks, a bucket and one of each chrysm tier.
     *
     * @param output the tab's item sink
     * @param key    the goo type's registry key
     */
    private static void acceptType(CreativeModeTab.Output output, ResourceKey<GooTypeDefinition> key) {
        output.accept(GooStacks.createForOutput(key, GooStacks.THOUSAND));
        output.accept(GooItem.createWithVolume(key, SAMPLE_GOO_VOLUME));
        output.accept(GooItem.createWithVolume(key, LARGE_GOO_VOLUME));
        output.accept(GooBucketItem.of(key));
        GooItems.CHRYSM_TIERS.forEach(tier -> output.accept(tier.get().createOf(key)));
    }
}
