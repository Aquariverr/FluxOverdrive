package com.aquariverr.fluxod.client.gui.miniature;

import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.CellPreview.Type;

import java.util.Objects;

public final class MiniatureGridInteraction {

    public static Intent decide(MiniatureGridViewport.Hit hit, Type type, int mouseButton, boolean carriedEmpty) {
        Objects.requireNonNull(hit, "hit");
        return hit.output() ? decideOutput(mouseButton) : decide(type, mouseButton, carriedEmpty);
    }

    public static Intent decide(Type type, int mouseButton, boolean carriedEmpty) {
        return decide(type, mouseButton, carriedEmpty, false);
    }

    public static Intent decide(Type type, int mouseButton, boolean carriedEmpty, boolean shiftDown) {
        Objects.requireNonNull(type, "type");
        if (mouseButton == 0) {
            return switch (type) {
                case EMPTY -> carriedEmpty ? Intent.none() : Intent.command(Action.INSERT_MACHINE);
                case MACHINE -> carriedEmpty ? Intent.local(Kind.SELECT_MACHINE) : Intent.command(Action.INSERT_MACHINE);
                case MARKER -> carriedEmpty ? Intent.local(Kind.SELECT_MARKER) : Intent.none();
                case ARROW -> carriedEmpty ? Intent.local(Kind.SELECT_ARROW) : Intent.none();
            };
        }
        if (mouseButton == 1) {
            return switch (type) {
                case EMPTY -> Intent.command(carriedEmpty ? Action.CREATE_ARROW : Action.SET_MARKER_FROM_CARRIED);
                case MACHINE -> shiftDown ? carriedEmpty ? Intent.command(Action.CYCLE_RECIPE) : Intent.none()
                        : Intent.command(Action.ROTATE_DIRECTION);
                case MARKER -> Intent.command(shiftDown && !carriedEmpty ? Action.SET_MARKER_FROM_CARRIED : Action.ROTATE_DIRECTION);
                case ARROW -> Intent.command(Action.ROTATE_ARROW);
            };
        }
        return Intent.none();
    }

    public static Intent decideOutput(int mouseButton) {
        return mouseButton == 0 || mouseButton == 1 ? Intent.local(Kind.SELECT_OUTPUT) : Intent.none();
    }

    public enum Kind { COMMAND, SELECT_MARKER, SELECT_OUTPUT, NONE, SELECT_ARROW, SELECT_MACHINE }

    // Commands are requests only; acceptance and every asset transfer remain server-authoritative.
    public record Intent(Kind kind, Action action) {
        public Intent {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.COMMAND) != (action != null)) {
                throw new IllegalArgumentException("Only a command intent can carry an action");
            }
        }

        public static Intent command(Action action) {
            return new Intent(Kind.COMMAND, Objects.requireNonNull(action, "action"));
        }

        public static Intent local(Kind kind) {
            return new Intent(kind, null);
        }

        public static Intent none() {
            return local(Kind.NONE);
        }
    }

    private MiniatureGridInteraction() {}
}
