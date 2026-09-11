package com.kadamitas.warlockery.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class ManualPagination {
    private ManualPagination() {
    }

    static <T> List<List<T>> pages(
        final List<T> lines,
        final int firstCapacity,
        final int continuationCapacity,
        final Predicate<T> blank
    ) {
        final List<List<T>> pages = new ArrayList<>();
        int index = 0;
        int capacity = Math.max(0, firstCapacity);
        if (capacity == 0) {
            pages.add(List.of());
            capacity = Math.max(1, continuationCapacity);
        }
        while (index < lines.size()) {
            while (index < lines.size() && blank.test(lines.get(index))) {
                index++;
            }
            if (index == lines.size()) {
                break;
            }
            final int end = Math.min(lines.size(), index + capacity);
            int visibleEnd = end;
            while (visibleEnd > index && blank.test(lines.get(visibleEnd - 1))) {
                visibleEnd--;
            }
            pages.add(List.copyOf(lines.subList(index, visibleEnd)));
            index = end;
            capacity = Math.max(1, continuationCapacity);
        }
        return pages.isEmpty() ? List.of(List.of()) : List.copyOf(pages);
    }
}
