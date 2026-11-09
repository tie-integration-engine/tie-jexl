package org.tie.jexl;

import org.tie.spi.ExpressionContext;

/**
 * A record as an expression sees it: {@code record.campo}, {@code record['cliente.nif']}, {@code parent.campo},
 * {@code parent.parent}. A name that is not a field of the layout is an error, so a typo does not pass silently.
 */
public final class JexlRecord {

    private final ExpressionContext context;

    JexlRecord(ExpressionContext context) {
        this.context = context;
    }

    static JexlRecord parentOf(ExpressionContext context) {
        return context.parent() == null ? null : new JexlRecord(context.parent());
    }

    /** The value of a field (null if empty or absent), or {@code parent} if no field has that name. */
    public Object get(String name) {
        if (context.fields().contains(name)) {
            return context.record().get(name);
        }
        if ("parent".equals(name)) {
            return parentOf(context);
        }
        throw new IllegalArgumentException("unknown field '" + name + "'");
    }

    @Override
    public String toString() {
        return "record" + context.fields();
    }
}
