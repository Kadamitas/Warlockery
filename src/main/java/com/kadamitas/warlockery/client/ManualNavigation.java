package com.kadamitas.warlockery.client;

import java.util.List;

final class ManualNavigation {
    static final int GAP = 2;

    private ManualNavigation() {
    }

    static int end(final List<Integer> heights, final int offset, final int space) {
        int end = offset;
        int used = 0;
        while (end < heights.size()) {
            final int next = used + (end > offset ? GAP : 0) + heights.get(end);
            if (next > space) {
                break;
            }
            used = next;
            end++;
        }
        return end;
    }

    static int reveal(final List<Integer> heights, final int offset, final int selected, final int space) {
        int start = Math.clamp(offset, 0, Math.max(0, heights.size() - 1));
        if (selected < 0 || selected >= heights.size()) {
            return start;
        }
        start = Math.min(start, selected);
        while (start < selected && end(heights, start, space) <= selected) {
            start++;
        }
        return start;
    }
}
