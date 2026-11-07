package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pocket.PocketMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the mod's container menus.
 */
public final class GooMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Goo.MODID);

    /** Void's Pocket (decision pocket-is-a-void-reserving-container-with-scroll-and-search). */
    public static final DeferredHolder<MenuType<?>, MenuType<PocketMenu>> POCKET = MENUS.register("pocket",
            () -> IMenuTypeExtension.create((containerId, inventory, extra) -> new PocketMenu(containerId, inventory)));

    private GooMenus() {
    }
}
