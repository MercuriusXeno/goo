package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that each goo type's icon draws from its own texture and its hover names the type
 * (decision goo-types-draw-as-icons-named-on-hover).
 */
class GooTypeIconsTest {

    /**
     * Each of the bundled types resolves to textures/goo/type/<type>.png in the mod's namespace.
     */
    @Test
    void eachTypeDrawsFromItsOwnTexture() {
        assertEquals(26, GooTypes.BUNDLED.size());
        for (String type : GooTypes.BUNDLED.stream().map(GooTypes::id).toList()) {
            assertEquals(Identifier.fromNamespaceAndPath("goo", "textures/goo/type/" + type + ".png"),
                    GooTypeIcons.texture(type));
        }
    }

    /**
     * The hover area an icon leaves carries a tooltip naming its type through the type's translation key.
     */
    @Test
    void eachIconsHoverNamesItsType() {
        for (String type : GooTypes.BUNDLED.stream().map(GooTypes::id).toList()) {
            ClickTargets targets = new ClickTargets();
            GooTypeIcons.addHover(targets, type, 40, 60);
            Optional<Component> tooltip = targets.hovered(48, 68).flatMap(GooTypeIcons::tooltipFor);
            assertTrue(tooltip.isPresent(), type);
            assertEquals("goo.type." + type, ((TranslatableContents) tooltip.get().getContents()).getKey());
        }
    }

    /**
     * An icon's hover area takes no click, so a click on an icon inside a read view still reaches the view.
     */
    @Test
    void anIconsHoverTakesNoClick() {
        ClickTargets targets = new ClickTargets();
        boolean[] opened = {false};
        targets.add(0, 0, 100, 20, () -> opened[0] = true);
        GooTypeIcons.addHover(targets, "metal", 10, 2);
        assertTrue(targets.click(12, 4));
        assertTrue(opened[0]);
    }
}
