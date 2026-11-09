package org.tie.jexl;

import org.apache.commons.jexl3.JexlContext;
import org.tie.spi.ExpressionContext;

/**
 * The read-only JEXL context of one evaluation: {@code value}, {@code record}, {@code parent}, {@code today},
 * {@code vars}, the record's fields by name, and the {@code tie} namespace. These reserved names win over a
 * field with the same name, which is still reachable as {@code record['name']}.
 */
final class TieJexlContext implements JexlContext, JexlContext.NamespaceResolver {

    private final ExpressionContext context;
    private final TieFunctions functions;

    TieJexlContext(ExpressionContext context) {
        this.context = context;
        this.functions = new TieFunctions(context.today());
    }

    @Override
    public Object get(String name) {
        return switch (name) {
            case "value" -> context.value();
            case "record" -> new JexlRecord(context);
            case "parent" -> JexlRecord.parentOf(context);
            case "today" -> context.today();
            case "vars" -> context.variables();
            default -> context.fields().contains(name) ? context.record().get(name) : null;
        };
    }

    @Override
    public boolean has(String name) {
        return switch (name) {
            case "value", "record", "parent", "today", "vars" -> true;
            default -> context.fields().contains(name);
        };
    }

    @Override
    public void set(String name, Object value) {
        throw new UnsupportedOperationException("expressions are read-only: cannot set '" + name + "'");
    }

    @Override
    public Object resolveNamespace(String name) {
        return "tie".equals(name) ? functions : null;
    }
}
