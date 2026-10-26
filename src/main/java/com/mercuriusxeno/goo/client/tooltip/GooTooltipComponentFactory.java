package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.workbench.GooAmountTooltipComponent;
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
        // decision value-tooltips-draw-each-types-icon-before-its-amount
        event.register(TooltipLine.GooAmount.class, GooAmountTooltipComponent::new);
    }
}
