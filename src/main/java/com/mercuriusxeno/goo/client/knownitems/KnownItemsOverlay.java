package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.ClientKnownItems;
import com.mercuriusxeno.goo.network.PlexerTargetPickPayload;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The plexer's known items menu: a Screen only because a Screen is what takes the mouse and the keys.
 * It draws a darkening fill, a title, a search bar, a close button, Cancel and the items the player
 * knows in their language's alphabetical order, and a scroll bar when they overflow, and keeps the
 * game running beneath it. Typing narrows the items, the wheel or a drag on the thumb scrolls them, a
 * click on an item sets the plexer's target and closes, Cancel clears it and closes, and Escape closes.
 * decision menu-is-a-darkening-overlay-not-a-screen
 * decision right-click-on-the-plexer-opens-the-known-items-menu
 * decision cancel-cell-clears-the-plexer-target
 * decision menu-lists-known-items-alphabetically-with-search-and-scroll
 * decision menu-header-reserves-the-target
 */
public final class KnownItemsOverlay extends Screen {

    private static final double HALF = 0.5;
    /** The close button's face, a red X. */
    private static final String CLOSE_MARK = "X";
    private static final int ROW_UP = -1;
    private static final int ROW_DOWN = 1;

    private final BlockPos plexer;
    private final ItemStack target;
    private final List<KnownItemsOverlayRenderer.KnownItem> ordered;
    private final Locale locale;
    private final SearchInput search;
    private int firstRow;
    private KnownItemsOverlayRenderer.@Nullable Drawn drawn;
    private boolean draggingThumb;

    private KnownItemsOverlay(BlockPos plexer, ItemStack target, List<KnownItemsOverlayRenderer.KnownItem> ordered,
                              Locale locale) {
        super(Component.empty());
        this.plexer = plexer;
        this.target = target;
        this.ordered = ordered;
        this.locale = locale;
        this.search = new SearchInput();
    }

    /**
     * Opens the overlay for a plexer, whose target a pick sets.
     * decision right-click-on-the-plexer-opens-the-known-items-menu
     *
     * @param plexer the plexer's position
     */
    public static void open(BlockPos plexer) {
        Minecraft mc = Minecraft.getInstance();
        Locale locale = KnownItemsListing.localeOf(mc.getLanguageManager().getSelected());
        List<KnownItemsOverlayRenderer.KnownItem> known = ClientKnownItems.current().items().stream()
                .map(id -> new KnownItemsOverlayRenderer.KnownItem(id,
                        new ItemStack(BuiltInRegistries.ITEM.getValue(id))))
                .filter(item -> !item.stack().isEmpty())
                .toList();
        mc.setScreen(new KnownItemsOverlay(plexer, targetOf(mc.level, plexer), KnownItemsListing.order(known, locale),
                locale));
    }

    /**
     * Reads the plexer's current target from the client level, which the plexer's block entity sync keeps current.
     * decision menu-header-reserves-the-target
     *
     * @param level  the client level, or null before one loads
     * @param plexer the plexer's position
     * @return a copy of the plexer's target, empty when none is set or no plexer stands there
     */
    private static ItemStack targetOf(@Nullable Level level, BlockPos plexer) {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        return level.getBlockEntity(plexer, GooBlockEntities.PLEXER.get()).map(PlexerBlockEntity::getTargetItem)
                .map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    /**
     * Places the close button, a red X in the top right corner; Escape closes as well.
     */
    @Override
    protected void init() {
        KnownItemsOverlayRenderer.Box close = KnownItemsOverlayRenderer.closeButton(width);
        addRenderableWidget(Button.builder(Component.literal(CLOSE_MARK).withStyle(ChatFormatting.RED), button -> onClose())
                .bounds(close.left(), close.top(), close.size(), close.size())
                .build());
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
                target, KnownItemsListing.filter(ordered, search.text(), locale), search.text(), firstRow, mouseX,
                mouseY));
        firstRow = drawn.scroll().firstRow();
        KnownItemsOverlayRenderer.nameWhatTheCursorRestsOn(graphics, font, drawn, mouseX, mouseY,
                ClientGooValues.current());
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
        KnownItemsOverlayRenderer.Pick pick = KnownItemsOverlayRenderer.pickAt(drawn, event.x(), event.y());
        if (pick == null) {
            return super.mouseClicked(event, doubleClick);
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new PlexerTargetPickPayload(plexer, pick.item())));
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
     * Types into the search bar, which shows the grid from its first row again.
     */
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) {
            return false;
        }
        search.type(event.codepointAsString());
        firstRow = 0;
        return true;
    }

    /**
     * Backspace erases the search bar's last character; every other key, Escape among them, acts as
     * a screen's would.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_BACKSPACE && search.eraseLast()) {
            firstRow = 0;
            return true;
        }
        return super.keyPressed(event);
    }
}
