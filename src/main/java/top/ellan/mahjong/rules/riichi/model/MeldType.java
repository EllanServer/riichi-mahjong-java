package top.ellan.mahjong.rules.riichi.model;

public enum MeldType {
    CHII, PON, MINKAN, ANKAN, KAKAN;

    public boolean isKan() {
        return this == MINKAN || this == ANKAN || this == KAKAN;
    }

    public boolean isOpen() {
        return this != ANKAN;
    }
}
