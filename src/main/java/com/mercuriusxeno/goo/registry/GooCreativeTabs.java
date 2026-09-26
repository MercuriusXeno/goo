package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.fluid.GooBucketItem;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
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
 * every type the goo type registry holds, a datapack's included, one blob,
 * two sample omniblobs and a bucket (decision generic-goo-items).
 */
public class GooCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Goo.MODID);

    private static final int SAMPLE_OMNIBLOB_VOLUME = 1_000_000;
    private static final int LARGE_OMNIBLOB_VOLUME = 1_000_000_000;

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GOO_TAB =
        TABS.register("goo_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.goo"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> BlobStacks.createForOutput(GooTypes.ENDER, BlobStacks.MB_PER_BLOB))
            .displayItems((params, output) -> {
                // Machines
                output.accept(GooItems.CRUCIBLE.get());
                output.accept(GooItems.CANISTER.get());
                output.accept(GooItems.HUB.get());
                output.accept(GooItems.PLEXER.get());
                output.accept(GooItems.REACTOR.get());
                output.accept(GooItems.VAT.get());
                output.accept(GooItems.TAP.get());
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
     * Offers one type's 1-blob, 1K-blob and 1M-blob omniblobs, and a bucket.
     *
     * @param output the tab's item sink
     * @param key    the goo type's registry key
     */
    private static void acceptType(CreativeModeTab.Output output, ResourceKey<GooTypeDefinition> key) {
        output.accept(BlobStacks.createForOutput(key, BlobStacks.MB_PER_BLOB));
        output.accept(GooOmniblobItem.createWithVolume(key, SAMPLE_OMNIBLOB_VOLUME));
        output.accept(GooOmniblobItem.createWithVolume(key, LARGE_OMNIBLOB_VOLUME));
        output.accept(GooBucketItem.of(key));
    }
}
