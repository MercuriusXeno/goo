package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GloveSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * The glove's one radial wheel, opened by holding right click past the
 * threshold: hovering or scrolling to a type fans its abilities out, a left
 * click on an ability writes it to the glove and closes, and any other click
 * closes with the glove unchanged (decision type-recedes-and-abilities-fan-out).
 */
public final class GloveRadialScreen extends Screen {

    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;
    private static final int HALF = 2;

    private final List<ResourceKey<GooTypeDefinition>> types;
    private final List<List<ClientAbility>> abilities;
    private final Map<ResourceKey<GooTypeDefinition>, Integer> available;
    private final RadialWheel wheel;

    private GloveRadialScreen(Map<ResourceKey<GooTypeDefinition>, Integer> available) {
        super(Component.empty());
        this.types = GooTypes.order();
        this.abilities = types.stream().map(AbilitySyncHandler::getAbilitiesForType).toList();
        this.available = available;
        this.wheel = new RadialWheel(types.size(), type -> abilities.get(type).size());
    }

    /**
     * Opens the wheel, snapshotting the goo the player holds.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || GooTypes.order().isEmpty()) {
            return;
        }
        mc.setScreen(new GloveRadialScreen(GooSourceScanner.aggregateAvailable(player)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int radius() {
        return (int) RadialWheel.outerRadius(width, height);
    }

    @Override
    public void mouseMoved(double x, double y) {
        wheel.moveCursor(x - width / (double) HALF, y - height / (double) HALF, radius());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        wheel.scroll(scrollY);
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        RadialWheelRenderer.render(graphics, font, new RadialWheelRenderer.Frame(wheel, types, abilities, available,
                width / HALF, height / HALF, radius()));
    }

    /**
     * A left click on an ability writes it to the glove; any other click
     * closes with the glove unchanged.
     *
     * @param event       the mouse button click event
     * @param doubleClick true if this is a double-click
     * @return true if the event was handled
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        RadialWheel.Outcome outcome = switch (event.button()) {
            case LEFT_BUTTON -> wheel.click();
            case RIGHT_BUTTON -> wheel.rightClick();
            default -> null;
        };
        if (outcome == null) {
            return super.mouseClicked(event, doubleClick);
        }
        if (outcome.selects()) {
            selectAbility(types.get(outcome.type()), abilities.get(outcome.type()).get(outcome.ability()));
        }
        onClose();
        return true;
    }

    private static void selectAbility(ResourceKey<GooTypeDefinition> type, ClientAbility ability) {
        ItemStack glove = findGloveStack();
        if (glove == null) {
            return;
        }
        GloveSelection selection = GloveSelection.ofAbility(type, ability.id());
        GooGloveItem.setSelection(glove, selection);
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                    new GloveSelectPayload(selection.gooTypeId(), selection.abilityId())));
        }
    }

    private static @Nullable ItemStack findGloveStack() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof GooGloveItem) {
                return held;
            }
        }
        return null;
    }
}
