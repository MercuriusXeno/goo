package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlockEntity;
import com.mercuriusxeno.goo.block.gate.DragonGateBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.statue.StatueBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.fluid.GooFluidBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class GooBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Goo.MODID);

    /**
     * The block entity carrying an in-world goo fluid block's type.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GooFluidBlockEntity>> GOO_FLUID =
            BLOCK_ENTITIES.register(GooFluids.GOO_PATH,
                    () -> new BlockEntityType<>(GooFluidBlockEntity::new, GooBlocks.GOO_FLUID.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrucibleBlockEntity>> CRUCIBLE =
            BLOCK_ENTITIES.register("crucible",
                    () -> new BlockEntityType<>(CrucibleBlockEntity::new, GooBlocks.CRUCIBLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HubBlockEntity>> HUB =
            BLOCK_ENTITIES.register("hub",
                    () -> new BlockEntityType<>(HubBlockEntity::new, GooBlocks.HUB.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlexerBlockEntity>> PLEXER =
            BLOCK_ENTITIES.register("plexer",
                    () -> new BlockEntityType<>(PlexerBlockEntity::new, GooBlocks.PLEXER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorBlockEntity>> REACTOR =
            BLOCK_ENTITIES.register("reactor",
                    () -> new BlockEntityType<>(ReactorBlockEntity::new, GooBlocks.REACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VatBlockEntity>> VAT =
            BLOCK_ENTITIES.register("vat",
                    () -> new BlockEntityType<>(VatBlockEntity::new, GooBlocks.VAT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TapBlockEntity>> TAP =
            BLOCK_ENTITIES.register("tap",
                    () -> new BlockEntityType<>(TapBlockEntity::new, GooBlocks.TAP.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystallizerBlockEntity>> CRYSTALLIZER =
            BLOCK_ENTITIES.register("crystallizer",
                    () -> new BlockEntityType<>(CrystallizerBlockEntity::new, GooBlocks.CRYSTALLIZER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CanisterBlockEntity>> CANISTER =
            BLOCK_ENTITIES.register("canister",
                    () -> new BlockEntityType<>(CanisterBlockEntity::new, GooBlocks.CANISTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AbilityBlockEntity>> ABILITY_BLOCK =
            BLOCK_ENTITIES.register("ability_block",
                    () -> new BlockEntityType<>(AbilityBlockEntity::new, GooBlocks.ABILITY_BLOCK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StatueBlockEntity>> STATUE =
            BLOCK_ENTITIES.register("statue",
                    () -> new BlockEntityType<>(StatueBlockEntity::new, GooBlocks.STATUE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrismBlockEntity>> PRISM =
            BLOCK_ENTITIES.register("prism",
                    () -> new BlockEntityType<>(PrismBlockEntity::new, GooBlocks.PRISM.get()));

    /** A Dragon Gate cell (decision dragon-gate-banishes-blocks-and-opens-a-portal). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DragonGateBlockEntity>> DRAGON_GATE =
            BLOCK_ENTITIES.register("dragon_gate",
                    () -> new BlockEntityType<>(DragonGateBlockEntity::new, GooBlocks.DRAGON_GATE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChoralGasketBlockEntity>> CHORAL_GASKET =
            BLOCK_ENTITIES.register("choral_gasket",
                    () -> new BlockEntityType<>(ChoralGasketBlockEntity::new, GooBlocks.CHORAL_GASKET_BLOCK.get()));

}
