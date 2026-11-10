package com.aquariverr.fluxod.client.gui.miniature;

import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridInteraction.Intent;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridInteraction.Kind;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridViewport.Hit;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.CellPreview.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiniatureGridInteractionTest {

    @ParameterizedTest(name = "{0}, button {1}, carried empty {2}: {3}")
    @MethodSource("clicks")
    void clicksFollowTheDocumentedPriorityTable(Type type, int button, boolean empty, Intent expected) {
        assertEquals(expected, MiniatureGridInteraction.decide(type, button, empty));
    }

    static Stream<Arguments> clicks() {
        return Stream.of(
                Arguments.of(Type.EMPTY, 0, true, Intent.none()),
                Arguments.of(Type.EMPTY, 0, false, Intent.command(Action.INSERT_MACHINE)),
                Arguments.of(Type.EMPTY, 1, true, Intent.command(Action.CREATE_ARROW)),
                Arguments.of(Type.EMPTY, 1, false, Intent.command(Action.SET_MARKER_FROM_CARRIED)),
                Arguments.of(Type.MACHINE, 0, true, Intent.local(Kind.SELECT_MACHINE)),
                Arguments.of(Type.MACHINE, 0, false, Intent.command(Action.INSERT_MACHINE)),
                Arguments.of(Type.MACHINE, 1, true, Intent.command(Action.ROTATE_DIRECTION)),
                Arguments.of(Type.MACHINE, 1, false, Intent.command(Action.ROTATE_DIRECTION)),
                Arguments.of(Type.MARKER, 0, true, Intent.local(Kind.SELECT_MARKER)),
                Arguments.of(Type.MARKER, 0, false, Intent.none()),
                Arguments.of(Type.MARKER, 1, true, Intent.command(Action.ROTATE_DIRECTION)),
                Arguments.of(Type.MARKER, 1, false, Intent.command(Action.ROTATE_DIRECTION)),
                Arguments.of(Type.ARROW, 0, true, Intent.local(Kind.SELECT_ARROW)),
                Arguments.of(Type.ARROW, 0, false, Intent.none()),
                Arguments.of(Type.ARROW, 1, true, Intent.command(Action.ROTATE_ARROW)),
                Arguments.of(Type.ARROW, 1, false, Intent.command(Action.ROTATE_ARROW)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void outputClicksOnlySelectTheRowEvenWithCarriedItems(int button) {
        Hit output = new Hit(3, 1, true);
        for (Type type : Type.values()) {
            assertEquals(Intent.local(Kind.SELECT_OUTPUT),
                    MiniatureGridInteraction.decide(output, type, button, true));
            assertEquals(Intent.local(Kind.SELECT_OUTPUT),
                    MiniatureGridInteraction.decide(output, type, button, false));
        }
        assertEquals(Intent.local(Kind.SELECT_OUTPUT), MiniatureGridInteraction.decideOutput(button));
        assertNull(MiniatureGridInteraction.decide(output, null, button, false).action());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, 3, 4})
    void otherMouseButtonsNeverEditOrSelect(int button) {
        for (Type type : Type.values()) {
            assertEquals(Intent.none(), MiniatureGridInteraction.decide(type, button, true));
            assertEquals(Intent.none(), MiniatureGridInteraction.decide(type, button, false));
        }
        assertEquals(Intent.none(), MiniatureGridInteraction.decideOutput(button));
    }

    @Test
    void ordinaryHitUsesTheCellIntentAndKeepsInsertionValidationOnTheServer() {
        Hit ordinary = new Hit(1, 1, false);
        assertEquals(Intent.command(Action.INSERT_MACHINE),
                MiniatureGridInteraction.decide(ordinary, Type.MACHINE, 0, false));
        assertEquals(Intent.command(Action.ROTATE_DIRECTION),
                MiniatureGridInteraction.decide(ordinary, Type.MARKER, 1, false));
    }

    @Test
    void shiftRightClickKeepsRecipeSelectionAndExplicitMarkerReplacementAvailable() {
        assertEquals(Intent.command(Action.CYCLE_RECIPE), MiniatureGridInteraction.decide(Type.MACHINE, 1, true, true));
        assertEquals(Intent.none(), MiniatureGridInteraction.decide(Type.MACHINE, 1, false, true));
        assertEquals(Intent.command(Action.SET_MARKER_FROM_CARRIED), MiniatureGridInteraction.decide(Type.MARKER, 1, false, true));
        assertEquals(Intent.command(Action.ROTATE_DIRECTION), MiniatureGridInteraction.decide(Type.MARKER, 1, true, true));
    }

    @Test
    void localSelectionsCannotAccidentallyCarryMutationCommands() {
        assertThrows(IllegalArgumentException.class, () -> new Intent(Kind.SELECT_OUTPUT, Action.INSERT_MACHINE));
        assertThrows(IllegalArgumentException.class, () -> new Intent(Kind.NONE, Action.CLEAR_CONFIGURATION));
        assertThrows(IllegalArgumentException.class, () -> new Intent(Kind.COMMAND, null));
    }
}
