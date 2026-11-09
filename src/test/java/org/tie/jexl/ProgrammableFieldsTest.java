package org.tie.jexl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tie.ImportContext;
import org.tie.ImportResult;
import org.tie.ResultStatus;
import org.tie.Tie;
import org.tie.TypedRecord;
import org.tie.config.FieldDefinition;
import org.tie.config.FieldType;
import org.tie.config.FileFormat;
import org.tie.config.IntegrationDefinition;
import org.tie.config.Layout;
import org.tie.config.ProvidedStreamChannel;
import org.tie.spi.Outcome;

/** The example of the spec: a family member under 16 without a document takes the holder's (§4.3). */
class ProgrammableFieldsTest {

    private static final String DOCUMENT =
            "value != null ? value : (tie:age(fechaNacimiento) < 16 ? parent.documentoIdentidad : null)";

    @TempDir
    Path work;

    private final Tie tie = Tie.builder()
            .clock(Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC))
            .expressionEngine(new JexlExpressionEngine())
            .build();

    private static IntegrationDefinition definition(String expression) {
        return IntegrationDefinition.importing("familias")
                .inputChannel(ProvidedStreamChannel.INSTANCE)
                .layout(Layout.builder("titular").allowedFormats(FileFormat.JSON)
                        .field(FieldDefinition.builder("documentoIdentidad")))
                .layout(Layout.builder("familiar").parent("titular", "familiares")
                        .field(FieldDefinition.builder("fechaNacimiento").type(FieldType.DATE))
                        .field(FieldDefinition.builder("documentoIdentidad").expression(expression)))
                .build();
    }

    private List<TypedRecord> run(String json) {
        List<TypedRecord> records = new ArrayList<>();
        ImportResult result = tie.importFiles(definition(DOCUMENT), ImportContext.builder(record -> {
                    records.add(record);
                    return Outcome.APPLIED;
                })
                .workingDirectory(work)
                .providedInput("titular", "familias.json", new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
                .build());
        assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS);
        return records;
    }

    @Test
    @DisplayName("AC-291 a family member under 16 without a document takes the holder's; one of 16 or more does not")
    void minorTakesTheHoldersDocument() {
        List<TypedRecord> records = run("""
                [{"documentoIdentidad": "X1", "familiares": [
                    {"fechaNacimiento": "2015-03-01"},
                    {"fechaNacimiento": "2000-03-01"}]}]
                """);

        assertThat(records).filteredOn(record -> record.layout().equals("familiar"))
                .extracting(record -> record.get("documentoIdentidad")).containsExactly("X1", null);
    }

    @Test
    @DisplayName("AC-292 a family member under 16 with a document keeps it")
    void minorWithADocumentKeepsIt() {
        List<TypedRecord> records = run("""
                [{"documentoIdentidad": "X1", "familiares": [{"fechaNacimiento": "2015-03-01", "documentoIdentidad": "Y9"}]}]
                """);

        assertThat(records.getLast().get("documentoIdentidad")).isEqualTo("Y9");
    }

    @Test
    @DisplayName("AC-302 an expression that does not compile is a configuration problem naming the field")
    void compileProblemsNameTheField() {
        assertThat(tie.validate(definition("while (true) { 1 }"))).singleElement().asString()
                .contains("layout 'familiar'", "field 'documentoIdentidad'", "expression does not compile");
    }
}
