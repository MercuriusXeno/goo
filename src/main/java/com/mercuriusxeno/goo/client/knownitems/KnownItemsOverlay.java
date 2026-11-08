package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.client.ClientKnownItems;
import com.mercuriusxeno.goo.network.GhostItemPickPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The known items overlay: a Screen only because a Screen is what takes the mouse and the keys. It
 * draws a darkening fill, a search bar, the items the player knows in their language's alphabetical
 * order and a scroll bar when they overflow, nothing else, and keeps the game running beneath it.
 * Typing narrows the items, the wheel or a drag on the thumb scrolls them, a click on an item asks
 * the server for a ghost of it and closes, and Escape closes.
 * decision menu-is-a-darkening-overlay-not-a-screen
 * decision menu-places-a-ghost-item-in-the-empty-hand
 * decision menu-lists-known-items-alphabetically-with-search-and-scroll
 */
public final class KnownItemsOverlay extends Screen {

    private static final double HALF = 0.5;
    private static final int ROW_UP = -1;
    private static final int ROW_DOWN = 1;

    private final List<KnownItemsOverlayRenderer.KnownItem> ordered;
    private final Locale locale;
    private final SearchInput search = new SearchInput();
    private int firstRow;
    private KnownItemsOverlayRenderer.@Nullable Drawn drawn;
    private boolean draggingThumb;

    private KnownItemsOverlay(List<KnownItemsOverlayRenderer.KnownItem> ordered, Locale locale) {
        super(Component.empty());
        this.ordered = ordered;
        this.locale = locale;
    }

    /**
     * Opens the overlay over the items the client's player knows, in the selected language's order.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Locale locale = KnownItemsListing.localeOf(mc.getLanguageManager().getSelected());
        List<KnownItemsOverlayRenderer.KnownItem> known = ClientKnownItems.current().items().stream()
                .map(id -> new KnownItemsOverlayRenderer.KnownItem(id,
                        new ItemStack(BuiltInRegistries.ITEM.getValue(id))))
                .filter(item -> !item.stack().isEmpty())
                .toList();
        mc.setScreen(new KnownItemsOverlay(KnownItemsListing.order(known, locale), locale));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Draws the overlay in place of any screen background, so the overlay's own draws are the whole frame.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float frameDelta) {
        drawn = KnownItemsOverlayRenderer.render(graphics, font, new KnownItemsOverlayRenderer.Frame(width, height,
                KnownItemsListing.filter(ordered, search.text(), locale), search.text(), firstRow));
        firstRow = drawn.scroll().firstRow();
        KnownItemsOverlayRenderer.nameWhatTheCursorRestsOn(graphics, font, drawn.icons(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (drawn == null) {
            return super.mouseClicked(event, doubleClick);
        }
        KnownItemsOverlayRenderer.Track track = drawn.track();
        if (track != null && KnownItemsOverlayRenderer.onTrack(track, event.x(), event.y())) {
            draggingThumb = true;
            dragThumbTo(track, event.y());
            return true;
        }
        GhostItemPickPayload pick = KnownItemsOverlayRenderer.pickAt(drawn.icons(), event.x(), event.y());
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

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        KnownItemsOverlayRenderer.Track track = drawn == null ? null : drawn.track();
        if (draggingThumb && track != null) {
            dragThumbTo(track, event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        return super.mouseReleased(event);
    }

    private void dragThumbTo(KnownItemsOverlayRenderer.Track track, double y) {
        if (drawn != null) {
            ScrollModel scroll = drawn.scroll();
            // The cursor holds the thumb by its middle.
            double thumbTop = y - track.top() - scroll.thumbSpan(track.length()) * HALF;
            firstRow = scroll.withThumbAt(thumbTop, track.length()).firstRow();
        }
    }

    /**
     * Moves the grid one row per wheel notch.
     */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (drawn != null && scrollY != 0) {
            firstRow = drawn.scroll().scrolledBy(scrollY > 0 ? ROW_UP : ROW_DOWN).firstRow();
        }
        return true;
    }

    /**
     * Types into the search bar, which shows the grid from its first row again; the G that opened
     * the overlay types nothing.
     */
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter() || !search.type(event.codepointAsString())) {
            return false;
        }
        firstRow = 0;
        return true;
    }

    /**
     * Backspace erases the search bar's last character, and the opening G's key repeats are swallowed;
     * every other key, Escape among them, acts as a screen's would.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search.openingKeyHeld() && KnownItemsKey.MAPPING.matches(event)) {
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE && search.eraseLast()) {
            firstRow = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * Releasing the G that opened the overlay lets typing reach the search bar.
     */
    @Override
    public boolean keyReleased(KeyEvent event) {
        if (KnownItemsKey.MAPPING.matches(event)) {
            search.releaseOpeningKey();
        }
        return super.keyReleased(event);
    }
}
