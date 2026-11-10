package com.aquariverr.fluxod.miniature.model;

public enum GridDirection {
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0),
    UP(0, -1);

    private final int deltaX;
    private final int deltaY;

    GridDirection(int deltaX, int deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaY() {
        return deltaY;
    }

    public GridDirection clockwise() {
        return switch (this) {
            case RIGHT -> DOWN;
            case DOWN -> LEFT;
            case LEFT -> UP;
            case UP -> RIGHT;
        };
    }
}
