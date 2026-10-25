package com.mercuriusxeno.goo.command;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the /goo command tree's dev gate: lab and orphans register only when
 * the gate is open, and the everyday subcommands register either way.
 */
class GooCommandTest {

    private static final String LAB = "lab";
    private static final String ORPHANS = "orphans";
    private static final String LOOKUP = "lookup";
    private static final String INVENTORY = "inventory";

    /**
     * A closed gate leaves lab and orphans out of the tree.
     */
    @Test
    void closedGateOmitsDevSubcommands() {
        LiteralCommandNode<CommandSourceStack> root = GooCommand.commandTree(false).build();
        assertNull(root.getChild(LAB));
        assertNull(root.getChild(ORPHANS));
        assertNotNull(root.getChild(LOOKUP));
    }

    /**
     * An open gate adds lab and orphans beside the everyday subcommands.
     */
    @Test
    void openGateAddsDevSubcommands() {
        LiteralCommandNode<CommandSourceStack> root = GooCommand.commandTree(true).build();
        assertNotNull(root.getChild(LAB));
        assertNotNull(root.getChild(ORPHANS));
        assertNotNull(root.getChild(LOOKUP));
    }

    /**
     * An open gate stands learn and forget under lab, so knowledge comes on request
     * (decision lab-kit-teaches-every-recipe); a closed gate holds no lab to stand them under.
     */
    @Test
    void openGateLabHoldsLearnAndForget() {
        CommandNode<CommandSourceStack> lab = GooCommand.commandTree(true).build().getChild(LAB);
        assertNotNull(lab.getChild("learn"));
        assertNotNull(lab.getChild("forget"));
    }

    /**
     * The inventory dump stands under the goo root on any server, and only an operator runs it
     * (decision inventory-dump-from-a-gametest-and-a-command).
     */
    @Test
    void inventoryIsAnOperatorSubcommand() {
        CommandNode<CommandSourceStack> inventory = GooCommand.commandTree(false).build().getChild(INVENTORY);
        assertNotNull(inventory);
        assertTrue(inventory.canUse(sourceWith(PermissionSet.ALL_PERMISSIONS)));
        assertFalse(inventory.canUse(sourceWith(PermissionSet.NO_PERMISSIONS)));
    }

    /**
     * The workbench stands under the goo root with the dev gate closed, as a shipped jar runs it, and only an
     * operator runs it; it serves no page, so no stop stands beneath it
     * (decision goo-workbench-opens-the-in-game-manager-screen).
     */
    @Test
    void workbenchIsAnOperatorSubcommandOutsideTheDevGate() {
        CommandNode<CommandSourceStack> workbench = GooCommand.commandTree(false).build().getChild("workbench");
        assertNotNull(workbench);
        assertNull(workbench.getChild("stop"));
        assertTrue(workbench.canUse(sourceWith(PermissionSet.ALL_PERMISSIONS)));
        assertFalse(workbench.canUse(sourceWith(PermissionSet.NO_PERMISSIONS)));
    }

    private static CommandSourceStack sourceWith(PermissionSet permissions) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.permissions()).thenReturn(permissions);
        return source;
    }
}
