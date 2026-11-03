package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.workbench.GooAmountGrid;
import com.mercuriusxeno.goo.client.workbench.RunsTooltipComponent;
import com.mercuriusxeno.goo.client.workbench.TooltipLine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

/**
 * Registers the mapping from GooValueTooltipComponent data to its client renderer.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTooltipComponentFactory {

    private GooTooltipComponentFactory() {}

    /**
     * Maps the goo tooltip data model to its client-side renderer.
     *
     * @param event the tooltip component factory registration event
     */
    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(GooValueTooltipComponent.class, GooValueClientTooltipComponent::new);
        event.register(VanillaFluidTooltipComponent.class, VanillaFluidClientTooltipComponent::new);
        event.register(ContainerValueTooltipComponent.class, ContainerValueClientTooltipComponent::new);
        // Decision value-tooltips-draw-each-types-icon-before-its-amount: each amount as its icon then its amount,
        // a run of them laid as one grid (decision tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
        event.register(TooltipLine.GooAmounts.class, GooAmountGrid::new);
        // decision conversion-controls-hover-their-formula
        event.register(TooltipLine.Runs.class, RunsTooltipComponent::new);
    }
}
