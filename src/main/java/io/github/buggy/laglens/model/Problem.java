package io.github.buggy.laglens.model;

/**
 * Одна найденная проблема с готовой текстовой рекомендацией.
 *
 * @param title          заголовок проблемы (например, «Чанк (312, -88) в world - 267 тайл-энтити»)
 * @param detail         уточнение (например, «из них 267 хопперов»); может быть пустым
 * @param recommendation что с этим делать - человеческим языком
 */
public record Problem(
        String title,
        String detail,
        String recommendation
) {
}
