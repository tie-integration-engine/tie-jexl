package org.tie.jexl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/** The functions of the {@code tie} namespace (§5.3): {@code tie:age(fecha)}, {@code tie:coalesce(a, b)}… */
public final class TieFunctions {

    private final LocalDate today;

    TieFunctions(LocalDate today) {
        this.today = today;
    }

    /** Whole years from {@code date} to today; null if {@code date} is null. */
    public Integer age(Object date) {
        return years(date, today);
    }

    /** Whole years between two dates ({@code LocalDate} or {@code LocalDateTime}); null if either is null. */
    public Integer years(Object from, Object to) {
        LocalDate start = localDate(from);
        LocalDate end = localDate(to);
        return start == null || end == null ? null : Period.between(start, end).getYears();
    }

    /** Whether {@code value} is null or a text that is empty or only spaces. */
    public boolean blank(Object value) {
        return value == null || value instanceof CharSequence text && text.toString().isBlank();
    }

    /** The date of {@code text} with {@code pattern} (strict, root locale); null if the text is blank. */
    public LocalDate date(String text, String pattern) {
        return blank(text) ? null : LocalDate.parse(text.strip(), strict(pattern));
    }

    /** The text of a date or date-time with {@code pattern} (root locale); null if {@code date} is null. */
    public String format(Object date, String pattern) {
        if (date == null) {
            return null;
        }
        if (!(date instanceof TemporalAccessor temporal)) {
            throw new IllegalArgumentException("tie:format needs a date, got " + date.getClass().getSimpleName());
        }
        return strict(pattern).format(temporal);
    }

    /** The first value that is neither null nor a blank text; null if there is none. */
    public Object coalesce(Object... values) {
        for (Object value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private static LocalDate localDate(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDate date -> date;
            case LocalDateTime dateTime -> dateTime.toLocalDate();
            default -> throw new IllegalArgumentException("expected a date, got " + value.getClass().getSimpleName());
        };
    }

    /** A strict formatter where {@code y} is the proleptic year, as in typeFormat. */
    private static DateTimeFormatter strict(String pattern) {
        StringBuilder converted = new StringBuilder(pattern.length());
        boolean quoted = false;
        for (char c : pattern.toCharArray()) {
            if (c == '\'') {
                quoted = !quoted;
            }
            converted.append(!quoted && c == 'y' ? 'u' : c);
        }
        return DateTimeFormatter.ofPattern(converted.toString(), Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }
}
