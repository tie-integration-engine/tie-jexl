package org.tie.jexl;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlFeatures;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.tie.spi.CompiledExpression;
import org.tie.spi.ExpressionEngine;
import org.tie.spi.ExpressionException;

/**
 * Field expressions with Apache Commons JEXL in a restricted sandbox (§5.3):
 * <ul>
 *   <li>{@link JexlPermissions#RESTRICTED}, plus this package: no reflection, class loading, files, network or
 *       processes;</li>
 *   <li>no loops, {@code new}, assignments, side effects, lambdas, local variables or pragmas, so an
 *       expression always ends;</li>
 *   <li>strict: an unknown name is an error, while a declared field that is empty or absent is null.</li>
 * </ul>
 * An expression sees {@code value}, the fields of its record by name, {@code record}, {@code parent},
 * {@code today}, {@code vars} and the functions {@code tie:} of {@link TieFunctions}. Thread-safe.
 */
public final class JexlExpressionEngine implements ExpressionEngine {

    private static final JexlFeatures FEATURES = new JexlFeatures()
            .loops(false)
            .newInstance(false)
            .sideEffect(false)
            .sideEffectGlobal(false)
            .lambda(false)
            .fatArrow(false)
            .thinArrow(false)
            .localVar(false)
            .pragma(false)
            .importPragma(false)
            .namespacePragma(false)
            .annotation(false);

    private final JexlEngine jexl = new JexlBuilder()
            // RESTRICTED denies reflection, class loading, files, network and processes; this package adds only
            // the record view and the tie: functions, the only objects of it an expression can reach
            .permissions(JexlPermissions.RESTRICTED.compose("org.tie.jexl.*"))
            .features(FEATURES)
            .strict(true)
            .silent(false)
            .safe(false)
            .antish(false)
            .cache(512)
            .create();

    @Override
    public CompiledExpression compile(String source) {
        JexlExpression expression;
        try {
            expression = jexl.createExpression(source);
        } catch (JexlException e) {
            throw new ExpressionException(e.getMessage(), e);
        }
        return context -> expression.evaluate(new TieJexlContext(context));
    }
}
