package com.mercuriusxeno.goo.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the /goo command tree's dev gate: lab and orphans register only when
 * the gate is open, and the everyday subcommands register either way.
 */
class GooCommandTest {

    private static final String LAB = "lab";
    private static final String ORPHANS = "orphans";
    private static final String LOOKUP = "lookup";

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
}
