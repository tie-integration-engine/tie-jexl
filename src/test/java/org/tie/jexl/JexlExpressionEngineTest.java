package org.tie.jexl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tie.RecordView;
import org.tie.config.Direction;
import org.tie.spi.ExpressionContext;
import org.tie.spi.ExpressionException;

class JexlExpressionEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private final JexlExpressionEngine engine = new JexlExpressionEngine();

    /** A record with these fields (declared names) and values (a missing value is an absent field). */
    record Ctx(Object value, Map<String, Object> values, Set<String> fields, ExpressionContext parent,
               Map<String, String> variables) implements ExpressionContext {

        static Ctx of(Object value, Object... namesAndValues) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < namesAndValues.length; i += 2) {
                values.put((String) namesAndValues[i], namesAndValues[i + 1]);
            }
            return new Ctx(value, values, new LinkedHashSet<>(values.keySet()), null, Map.of());
        }

        Ctx withParent(ExpressionContext parent) {
            return new Ctx(value, values, fields, parent, variables);
        }

        Ctx declaring(String... names) {
            Set<String> all = new LinkedHashSet<>(fields);
            all.addAll(List.of(names));
            return new Ctx(value, values, all, parent, variables);
        }

        @Override
        public RecordView record() {
            return new RecordView() {
                @Override
                public boolean has(String field) {
                    return values.containsKey(field);
                }

                @Override
                public Object get(String field) {
                    return values.get(field);
                }

                @Override
                public <T> T get(String field, Class<T> type) {
                    return type.cast(values.get(field));
                }

                @Override
                public Map<String, String> rawValues() {
                    return Map.of();
                }
            };
        }

        @Override
        public LocalDate today() {
            return TODAY;
        }

        @Override
        public Direction direction() {
            return Direction.IMPORT;
        }
    }

    private Object eval(String source, ExpressionContext context) {
        return engine.compile(source).evaluate(context);
    }

    @Nested
    class Language {

        @Test
        void fieldsValueAndArithmetic() {
            Ctx ctx = Ctx.of(5L, "nombre", "Ana", "apellido", "Pérez", "importe", new BigDecimal("1000.50"));

            assertThat(eval("nombre + ' ' + apellido", ctx)).isEqualTo("Ana Pérez");
            assertThat(((Number) eval("value * 2", ctx)).longValue()).isEqualTo(10L);
            assertThat(eval("importe > 1000 ? 'ALTO' : 'NORMAL'", ctx)).isEqualTo("ALTO");
            assertThat(eval("nombre.toUpperCase()", ctx)).isEqualTo("ANA");
            assertThat(eval("record['apellido']", ctx)).isEqualTo("Pérez");
        }

        @Test
        void parentAndGrandparent() {
            Ctx grandparent = Ctx.of(null, "codigo", "G1");
            Ctx parent = Ctx.of(null, "documento", "X1").withParent(grandparent);
            Ctx child = Ctx.of(null, "n", "1").withParent(parent);

            assertThat(eval("parent.documento", child)).isEqualTo("X1");
            assertThat(eval("parent.parent.codigo", child)).isEqualTo("G1");
            assertThat(eval("parent == null ? 'SIN' : 'CON'", Ctx.of(null))).isEqualTo("SIN");
        }

        @Test
        void todayAndVariables() {
            Ctx ctx = new Ctx(null, Map.of(), Set.of(), null, Map.of("tenant", "A"));

            assertThat(eval("today", ctx)).isEqualTo(TODAY);
            assertThat(eval("vars.tenant", ctx)).isEqualTo("A");
        }

        @Test
        @DisplayName("AC-296 (JEXL) an unknown name is an error; a declared field that is absent is null")
        void strictNames() {
            Ctx ctx = Ctx.of(null, "nombre", "Ana").declaring("vacio");

            assertThat(eval("vacio", ctx)).isNull();
            assertThat(eval("vacio == null ? 'x' : vacio", ctx)).isEqualTo("x");
            assertThatThrownBy(() -> eval("nomre", ctx)).hasMessageContaining("nomre");
            assertThatThrownBy(() -> eval("parent.nomre", Ctx.of(null).withParent(ctx))).hasMessageContaining("nomre");
        }
    }

    @Nested
    class Sandbox {

        @Test
        @DisplayName("AC-302 a syntax error, new and loops do not compile")
        void forbiddenConstructs() {
            for (String source : List.of("nombre +", "new('java.io.File', '/')", "while (true) { 1 }",
                    "for (var x : [1, 2]) { x }")) {
                assertThatThrownBy(() -> engine.compile(source)).as(source).isInstanceOf(ExpressionException.class);
            }
        }

        @Test
        @DisplayName("AC-303 classes, reflection and the system are out of reach; nothing is executed")
        void noEscape() {
            Ctx ctx = Ctx.of(null, "nombre", "Ana");
            for (String source : List.of("java.lang.System.exit(0)", "nombre.getClass().forName('java.lang.Runtime')",
                    "nombre.getClass().getClassLoader()", "''.class.forName('java.lang.Runtime')")) {
                assertThatThrownBy(() -> eval(source, ctx)).as(source).isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        @DisplayName("AC-304 assignments and changes to the record do not compile or fail")
        void readOnly() {
            for (String source : List.of("x = 1", "nombre = 'b'", "record.nombre = 'b'", "var y = 1")) {
                assertThatThrownBy(() -> eval(source, Ctx.of(null, "nombre", "Ana")))
                        .as(source).isInstanceOf(RuntimeException.class);
            }
        }
    }

    @Nested
    class Functions {

        private final TieFunctions tie = new TieFunctions(TODAY);

        @Test
        @DisplayName("AC-312 tie:age, years, blank, date, format and coalesce, with values and with null")
        void functions() {
            assertThat(tie.age(LocalDate.of(2015, 11, 10))).isEqualTo(10);
            assertThat(tie.age(LocalDate.of(2015, 11, 9))).isEqualTo(11);
            assertThat(tie.age(LocalDateTime.of(2000, 1, 1, 10, 0))).isEqualTo(26);
            assertThat(tie.age(null)).isNull();
            assertThat(tie.years(LocalDate.of(2000, 6, 1), LocalDate.of(2010, 5, 31))).isEqualTo(9);
            assertThat(tie.years(null, TODAY)).isNull();
            assertThat(tie.blank(null)).isTrue();
            assertThat(tie.blank("  ")).isTrue();
            assertThat(tie.blank("x")).isFalse();
            assertThat(tie.date("31/12/2026", "dd/MM/yyyy")).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(tie.date(" ", "dd/MM/yyyy")).isNull();
            assertThatThrownBy(() -> tie.date("31/02/2026", "dd/MM/yyyy")).isInstanceOf(RuntimeException.class);
            assertThat(tie.format(LocalDate.of(2026, 12, 31), "yyyyMMdd")).isEqualTo("20261231");
            assertThat(tie.format(null, "yyyyMMdd")).isNull();
            assertThat(tie.coalesce(null, " ", "b", "c")).isEqualTo("b");
            assertThat(tie.coalesce(null, "")).isNull();
        }

        @Test
        void functionsFromExpressions() {
            Ctx ctx = Ctx.of(null, "nacido", LocalDate.of(2015, 3, 1), "doc", "", "otro", "Y9");

            assertThat(eval("tie:age(nacido)", ctx)).isEqualTo(11);
            assertThat(eval("tie:coalesce(doc, otro)", ctx)).isEqualTo("Y9");
            assertThat(eval("tie:blank(doc)", ctx)).isEqualTo(true);
            assertThat(eval("tie:format(tie:date('31/12/2026', 'dd/MM/yyyy'), 'yyyy-MM-dd')", ctx)).isEqualTo("2026-12-31");
        }
    }
}
