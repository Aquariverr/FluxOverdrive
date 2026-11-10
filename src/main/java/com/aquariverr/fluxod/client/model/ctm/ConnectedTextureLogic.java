package com.aquariverr.fluxod.client.model.ctm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.BOTTOM;
import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.BOTTOM_LEFT;
import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.BOTTOM_RIGHT;
import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.LEFT;
import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.RIGHT;
import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.TOP;
import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.TOP_LEFT;
import static com.aquariverr.fluxod.client.model.ctm.ConnectedTextureMask.TOP_RIGHT;

final class ConnectedTextureLogic {
    private static final Direction[] FACE_RIGHT = {
            Direction.EAST, Direction.EAST, Direction.WEST,
            Direction.EAST, Direction.SOUTH, Direction.NORTH
    };
    private static final Direction[] FACE_TOP = {
            Direction.SOUTH, Direction.NORTH, Direction.UP,
            Direction.UP, Direction.UP, Direction.UP
    };

    static long compute(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return compute(pos, cursor -> level.getBlockState(cursor).is(block));
    }

    static long compute(BlockPos pos, Predicate<BlockPos> occupiedPredicate) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int occupiedMask = 0;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    cursor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (occupiedPredicate.test(cursor)) {
                        occupiedMask |= 1 << index(x, y, z);
                    }
                }
            }
        }

        long packed = 0;
        for (Direction face : Direction.values()) {
            int mask = computeFaceMask(occupiedMask, face);
            packed |= (long) mask << (face.get3DDataValue() * 8);
        }
        return packed;
    }

    static Direction faceRight(Direction face) {
        return FACE_RIGHT[face.get3DDataValue()];
    }

    static Direction faceTop(Direction face) {
        return FACE_TOP[face.get3DDataValue()];
    }

    private static int computeFaceMask(int occupied, Direction face) {
        Direction right = faceRight(face);
        Direction top = faceTop(face);

        boolean topEdge = surfaceSame(occupied, face, top.getStepX(), top.getStepY(), top.getStepZ());
        boolean rightEdge = surfaceSame(occupied, face, right.getStepX(), right.getStepY(), right.getStepZ());
        boolean bottomEdge = surfaceSame(occupied, face, -top.getStepX(), -top.getStepY(), -top.getStepZ());
        boolean leftEdge = surfaceSame(occupied, face, -right.getStepX(), -right.getStepY(), -right.getStepZ());

        int mask = 0;
        if (topEdge) mask |= TOP;
        if (rightEdge) mask |= RIGHT;
        if (bottomEdge) mask |= BOTTOM;
        if (leftEdge) mask |= LEFT;

        if (topEdge && rightEdge && surfaceSame(occupied, face,
                top.getStepX() + right.getStepX(),
                top.getStepY() + right.getStepY(),
                top.getStepZ() + right.getStepZ())) {
            mask |= TOP_RIGHT;
        }
        if (rightEdge && bottomEdge && surfaceSame(occupied, face,
                right.getStepX() - top.getStepX(),
                right.getStepY() - top.getStepY(),
                right.getStepZ() - top.getStepZ())) {
            mask |= BOTTOM_RIGHT;
        }
        if (bottomEdge && leftEdge && surfaceSame(occupied, face,
                -top.getStepX() - right.getStepX(),
                -top.getStepY() - right.getStepY(),
                -top.getStepZ() - right.getStepZ())) {
            mask |= BOTTOM_LEFT;
        }
        if (leftEdge && topEdge && surfaceSame(occupied, face,
                top.getStepX() - right.getStepX(),
                top.getStepY() - right.getStepY(),
                top.getStepZ() - right.getStepZ())) {
            mask |= TOP_LEFT;
        }
        return mask;
    }

    private static boolean surfaceSame(int occupied, Direction face, int x, int y, int z) {
        if (!isOccupied(occupied, x, y, z)) {
            return false;
        }
        return !isOccupied(occupied,
                x + face.getStepX(),
                y + face.getStepY(),
                z + face.getStepZ());
    }

    private static boolean isOccupied(int occupied, int x, int y, int z) {
        return (occupied & 1 << index(x, y, z)) != 0;
    }

    private static int index(int x, int y, int z) {
        return (x + 1) * 9 + (y + 1) * 3 + z + 1;
    }

    private ConnectedTextureLogic() {}
}
