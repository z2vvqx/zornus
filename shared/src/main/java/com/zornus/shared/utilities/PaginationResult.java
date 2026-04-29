package com.zornus.shared.utilities;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Generic pagination result for consistent pagination handling.
 *
 * @param <T> Type of items being paginated
 */
public record PaginationResult<T>(
        @NotNull List<T> items,
        int currentPage,
        int maximumPages,
        int totalItems,
        boolean isValidPage
) {

    public static <T> PaginationResult<T> invalidPage(int maximumPages) {
        return new PaginationResult<>(List.of(), 0, maximumPages, 0, false);
    }

    public static <T> PaginationResult<T> validPage(@NotNull List<T> items, int currentPage, int totalItems, int itemsPerPage) {
        int maximumPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        return new PaginationResult<>(items, currentPage, maximumPages, totalItems, true);
    }

    public static <T> PaginationResult<T> paginate(@NotNull List<T> allItems, int page, int itemsPerPage) {
        if (allItems.isEmpty()) {
            return invalidPage(1);
        }

        int totalItems = allItems.size();
        int maximumPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));

        if (page < 1 || page > maximumPages) {
            return invalidPage(maximumPages);
        }

        int fromIndex = (page - 1) * itemsPerPage;
        int toIndex = Math.min(fromIndex + itemsPerPage, totalItems);
        List<T> pageItems = allItems.subList(fromIndex, toIndex);

        return new PaginationResult<>(pageItems, page, maximumPages, totalItems, true);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int getCurrentPageSize() {
        return items.size();
    }

    public boolean hasMultiplePages() {
        return maximumPages > 1;
    }
}
