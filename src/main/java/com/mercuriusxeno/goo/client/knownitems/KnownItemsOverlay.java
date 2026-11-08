package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.client.ClientKnownItems;
import com.mercuriusxeno.goo.network.GhostItemPickPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.Comparator;
import java.util.List;

/**
 * The known items overlay: a Screen only because a Screen is what takes the mouse. It draws a
 * darkening fill and the items the player knows, nothing else, keeps the game running beneath it,
 * and a click on an item asks the server for a ghost of it and closes. Escape closes.
 * decision menu-is-a-darkening-overlay-not-a-screen
 * decision menu-places-a-ghost-item-in-the-empty-hand
 */
public final class KnownItemsOverlay extends Screen {

    private final List<KnownItemsOverlayRenderer.KnownItem> items;
    private List<KnownItemsOverlayRenderer.ItemIcon> drawn = List.of();

    private KnownItemsOverlay(List<KnownItemsOverlayRenderer.KnownItem> items) {
        super(Component.empty());
        this.items = items;
    }

    /**
     * Opens the overlay over the items the client's player knows.
     */
    public static void open() {
        List<KnownItemsOverlayRenderer.KnownItem> known = ClientKnownItems.current().items().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .map(id -> new KnownItemsOverlayRenderer.KnownItem(id,
                        new ItemStack(BuiltInRegistries.ITEM.getValue(id))))
                .filter(item -> !item.stack().isEmpty())
                .toList();
        Minecraft.getInstance().setScreen(new KnownItemsOverlay(known));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Draws the overlay in place of any screen background, so the darkening fill and the items are
     * the whole frame.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDelta) {
        drawn = KnownItemsOverlayRenderer.render(graphics, new KnownItemsOverlayRenderer.Frame(width, height, items));
        KnownItemsOverlayRenderer.nameWhatTheCursorRestsOn(graphics, font, drawn, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        GhostItemPickPayload pick = KnownItemsOverlayRenderer.pickAt(drawn, event.x(), event.y());
        if (pick == null) {
            return super.mouseClicked(event, doubleClick);
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(pick));
        }
        onClose();
        return true;
    }
}
