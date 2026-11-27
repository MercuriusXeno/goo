package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.alienstructure.AlienGlyphBlock;
import com.mercuriusxeno.goo.block.alienstructure.AlienMaterial;
import com.mercuriusxeno.goo.block.alienstructure.AlienMaterialBlock;
import com.mercuriusxeno.goo.block.alienstructure.AlienPillarBlock;
import com.mercuriusxeno.goo.block.alienstructure.GlyphMark;
import com.mercuriusxeno.goo.block.alienstructure.PillarStyle;
import com.mercuriusxeno.goo.fluid.GooBucketItem;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Comparator;

/**
 * Creative mode tab registration. Shows machines, intermediates, and for
 * every type the goo type registry holds, a datapack's included, one goo,
 * two sample gooStacks, a bucket and a goo label (decision generic-goo-items).
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
                // Intermediates
                output.accept(GooItems.CHORAL_GASKET.get());
                output.accept(GooItems.CHORAL_TUNER.get());
                output.accept(GooItems.EXORITE.get());
                output.accept(GooItems.EXORITE_UPGRADE_SMITHING_TEMPLATE.get());
                output.accept(GooItems.EXORITE_BARS.get());
                output.accept(GooItems.ANORTHOSITE.get());
                output.accept(GooItems.REGOLITH.get());
                output.accept(GooItems.IRON_CORE.get());
                output.accept(GooItems.METEORIC_IRON.get());
                output.accept(GooItems.NATIVE_IRON.get());
                output.accept(GooItems.ASTRAL_CRYSTAL.get());
                output.accept(GooItems.MONOLITH_MARKER.get());
                acceptAlienStructureLooks(output);
                output.accept(GooItems.VOLATILE_CRYSTAL.get());
                output.accept(GooItems.RESONATOR.get());
                output.accept(GooItems.IMPACT_GLASS.get());
                output.accept(GooItems.BRECCIA.get());
                output.accept(GooItems.CRATER_ICE.get());
                output.accept(GooItems.METRONOME.get());
                output.accept(GooItems.REFLECTOR.get());
                output.accept(GooItems.LIGHT_BEACON.get());
                output.accept(GooItems.OCULUS.get());
                output.accept(GooItems.RELAY.get());
                output.accept(GooItems.MOLTEN_IRON_BUCKET.get());
                output.accept(GooItems.MOONROCK.get());
                // Equipment
                output.accept(GooItems.GOO_GLOVE.get());
                output.accept(GooItems.GOO_GAUNTLET.get());
                output.accept(GooItems.EXO_GAUNTLET.get());
                output.accept(GooItems.SPRAYER.get());
                GooItems.EXORITE_SET.forEach(piece -> output.accept(piece.get()));
                params.holders().lookupOrThrow(GooTypes.REGISTRY).listElements()
                        .map(Holder.Reference::key)
                        .sorted(Comparator.comparing(ResourceKey::identifier))
                        .forEach(key -> acceptType(output, key));
            })
            .build()
        );

    /**
     * Offers every look of the alien structure family as its own stack, each carrying the block state it places:
     * stone in each material, each pillar style, each glyph on each material, and tile in each material.
     * decision warrens-hold-xeno-rich-alien-blocks
     *
     * @param output the tab's item sink
     */
    private static void acceptAlienStructureLooks(CreativeModeTab.Output output) {
        for (AlienMaterial material : AlienMaterial.values()) {
            output.accept(withState(GooItems.ALIEN_STONE.get(),
                    BlockItemStateProperties.EMPTY.with(AlienMaterialBlock.MATERIAL, material)));
        }
        for (PillarStyle style : PillarStyle.values()) {
            output.accept(withState(GooItems.ALIEN_PILLAR.get(),
                    BlockItemStateProperties.EMPTY.with(AlienPillarBlock.STYLE, style)));
        }
        for (AlienMaterial material : AlienMaterial.values()) {
            for (GlyphMark mark : GlyphMark.values()) {
                output.accept(withState(GooItems.ALIEN_GLYPH.get(), BlockItemStateProperties.EMPTY
                        .with(AlienMaterialBlock.MATERIAL, material).with(AlienGlyphBlock.MARK, mark)));
            }
        }
        for (AlienMaterial material : AlienMaterial.values()) {
            output.accept(withState(GooItems.ALIEN_TILE.get(),
                    BlockItemStateProperties.EMPTY.with(AlienMaterialBlock.MATERIAL, material)));
        }
    }

    private static ItemStack withState(Item item, BlockItemStateProperties state) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.BLOCK_STATE, state);
        return stack;
    }

    /**
     * Offers one type's 1-goo, 1K-goo and 1M-gooStacks, a bucket and a goo label.
     *
     * @param output the tab's item sink
     * @param key    the goo type's registry key
     */
    private static void acceptType(CreativeModeTab.Output output, ResourceKey<GooTypeDefinition> key) {
        output.accept(GooStacks.createForOutput(key, GooStacks.THOUSAND));
        output.accept(GooItem.createWithVolume(key, SAMPLE_GOO_VOLUME));
        output.accept(GooItem.createWithVolume(key, LARGE_GOO_VOLUME));
        output.accept(GooBucketItem.of(key));
        output.accept(GooItems.GOO_LABEL.get().labelOf(key));
    }
}
