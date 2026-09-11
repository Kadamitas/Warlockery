package com.kadamitas.warlockery.compat.emi;

final class EmiTextPages {
    private final int lines;
    private final int pageSize;
    private int page;

    EmiTextPages(int lines, int pageSize) {
        this.lines = Math.max(0, lines);
        this.pageSize = Math.max(1, pageSize);
    }

    int pageCount() { return Math.max(1, (lines + pageSize - 1) / pageSize); }
    int page() { return page; }
    int start() { return page * pageSize; }
    int end() { return Math.min(lines, start() + pageSize); }
    void move(int delta) { page = Math.floorMod(page + delta, pageCount()); }
}
