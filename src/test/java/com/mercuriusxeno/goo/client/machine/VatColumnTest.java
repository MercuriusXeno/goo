package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the one vat column walk the HUD and the vat renderer both read
 * (decision one-panel-painter-takes-rows): walked from any member, a column
 * answers the same bottom, top and sum, and a missing member splits it even
 * while its neighbours still carry the links it left. A column reads bottom
 * first, one character a Y: 'V' a vat whose links match its neighbours, 'S' a
 * vat still linked both ways the tick after a neighbour broke, '_' no vat. The
 * vat at Y holds 100 * (Y + 1) mB of rock.
 */
class VatColumnTest {

    /**
     * Columns, a member to walk from, and the span and rock sum it answers.
     *
     * @return one argument set per case: column, start Y, bottom, top, rock sum
     */
    static Stream<Arguments> columnsAndSpans() {
        return Stream.of(
                Arguments.of("VVV", 0, 0, 2, 600),
                Arguments.of("VVV", 2, 0, 2, 600),
                Arguments.of("VV_VV", 1, 0, 1, 300),
                Arguments.of("VV_VV", 3, 3, 4, 900),
                Arguments.of("SS_SS", 0, 0, 1, 300),
                Arguments.of("SS_SS", 1, 0, 1, 300),
                Arguments.of("SS_SS", 3, 3, 4, 900),
                Arguments.of("SS_SS", 4, 3, 4, 900),
                Arguments.of("V", 0, 0, 0, 100));
    }

    /**
     * Every member of a column walks to the same span and sum, and no walk crosses a missing member.
     *
     * @param column the column, bottom first
     * @param startY the member walked from
     * @param bottom the bottom Y the column answers
     * @param top    the top Y the column answers
     * @param rock   the rock sum the column answers
     */
    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("columnsAndSpans")
    void everyMemberWalksToTheSameColumn(String column, int startY, int bottom, int top, int rock) {
        VatColumn walked = walk(column, startY);
        assertEquals(new VatColumn(bottom, top), walked);
        for (int y = bottom; y <= top; y++) {
            assertEquals(walked, walk(column, y), "walked from member " + y);
        }
        GooContents sum = walked.sum(y -> new GooContents(Map.of(GooTypes.ROCK, 100 * (y + 1))));
        assertEquals(rock, sum.getVolume(GooTypes.ROCK));
    }

    /**
     * Walks a described column from a Y.
     *
     * @param column the column, bottom first
     * @param startY the Y to walk from
     * @return the walked span
     */
    private static VatColumn walk(String column, int startY) {
        return VatColumn.walk(startY,
                y -> cell(column, y) != '_',
                y -> cell(column, y) == 'S' || cell(column, y + 1) != '_',
                y -> cell(column, y) == 'S' || cell(column, y - 1) != '_');
    }

    /**
     * Reads the cell at a Y, '_' outside the column.
     *
     * @param column the column, bottom first
     * @param y      the Y to read
     * @return the cell
     */
    private static char cell(String column, int y) {
        return y < 0 || y >= column.length() ? '_' : column.charAt(y);
    }
}
