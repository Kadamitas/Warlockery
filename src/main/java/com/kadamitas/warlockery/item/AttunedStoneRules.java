package com.kadamitas.warlockery.item;

public final class AttunedStoneRules {
    public static final int CAPACITY = 2_000;
    public static final int TRANSFER_RATE = 250;

    private AttunedStoneRules() {
    }

    public static Transfer withdraw(final int stonePower, final int altarPower) {
        final int available = Math.max(0, altarPower);
        final int moved = Math.min(Math.min(CAPACITY - bounded(stonePower), available), TRANSFER_RATE);
        return new Transfer(bounded(stonePower) + moved, available - moved, moved);
    }

    public static Transfer deposit(final int stonePower, final int altarPower, final int altarCapacity) {
        final int moved = Math.min(Math.min(bounded(stonePower), Math.max(0, altarCapacity - altarPower)), TRANSFER_RATE);
        return new Transfer(bounded(stonePower) - moved, Math.max(0, altarPower) + moved, moved);
    }

    public static int bounded(final int power) {
        return Math.clamp(power, 0, CAPACITY);
    }

    public record Transfer(int stonePower, int altarPower, int moved) {
        public boolean succeeded() {
            return moved > 0;
        }
    }
}
