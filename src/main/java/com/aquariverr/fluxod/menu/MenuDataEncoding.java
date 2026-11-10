package com.aquariverr.fluxod.menu;

final class MenuDataEncoding {

    static int lowWord(int value) {
        return value & 0xFFFF;
    }

    static int highWord(int value) {
        return value >>> 16 & 0xFFFF;
    }

    static int combineWords(int lowWord, int highWord) {
        return lowWord & 0xFFFF | (highWord & 0xFFFF) << 16;
    }

    private MenuDataEncoding() {}
}
