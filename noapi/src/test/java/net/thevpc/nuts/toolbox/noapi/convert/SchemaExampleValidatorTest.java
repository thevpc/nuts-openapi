package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests of the validation of the examples: every example of a document must follow the
 * constraints of the schema it documents (type, enum, lengths, ranges, required properties, …).
 */
public class SchemaExampleValidatorTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static NoApiDiagnostics validate(String tson) {
        NElement parsed = NoApiFormats.readString(tson, NoApiFormat.TSON);
        NoApiDiagnostics diagnostics = new NoApiDiagnostics("test");
        NElement model = new CondensedTsonDesugarer(diagnostics).desugar(parsed);
        SchemaExampleValidator.validateDocument(model, diagnostics);
        return diagnostics;
    }

    private static List<NoApiDiagnostics.Diagnostic> errors(NoApiDiagnostics diagnostics) {
        List<NoApiDiagnostics.Diagnostic> r = new ArrayList<>();
        for (NoApiDiagnostics.Diagnostic d : diagnostics.all()) {
            if (d.error) {
                r.add(d);
            }
        }
        return r;
    }

    private static void assertOneError(NoApiDiagnostics diagnostics, String expectedMessagePart) {
        List<NoApiDiagnostics.Diagnostic> errs = errors(diagnostics);
        assertEquals("expected exactly one error, got " + errs, 1, errs.size());
        NoApiDiagnostics.Diagnostic e = errs.get(0);
        assertEquals("E_EXAMPLE_MISMATCH", e.code);
        assertTrue("message must say what was found and what to write: " + e.message,
                e.message.contains(expectedMessagePart));
    }

    private static String doc(String schemas) {
        return ""
                + "@openapi(\"3.0.0\")\n"
                + "info { title: \"T\", version: \"1.0.0\" }\n"
                + "paths {\n"
                + "  POST(\"/thing\") {\n"
                + "    requestBody: @required Thing(json) {\n"
                + "      description: \"the thing\"\n"
                + "      example: { \"name\": \"abc\" }\n"
                + "    }\n"
                + "    out(200): Thing(json) {\n"
                + "      description: \"the thing\"\n"
                + "      example: { \"name\": \"abc\" }\n"
                + "    }\n"
                + "  }\n"
                + "}\n"
                + "components {\n"
                + "  schemas {\n"
                + schemas
                + "  }\n"
                + "}\n";
    }

    @Test
    public void aValidDocumentHasNoExampleError() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      @required name: string(minLength: 2 maxLength: 5 description: \"the name\") { example: \"abc\" }\n"
                + "      kind: enum(values: [A B]) { description: \"the kind\" example: \"A\" }\n"
                + "    }\n"));
        assertEquals(errors(d).toString(), 0, errors(d).size());
    }

    @Test
    public void anExampleOfTheWrongTypeIsReported() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      @required name: string(description: \"the name\") { example: 12 }\n"
                + "    }\n"));
        assertOneError(d, "is a number");
        assertTrue("message must say what to write: " + errors(d).get(0).message,
                errors(d).get(0).message.contains("requires a string"));
    }

    @Test
    public void anExampleOutsideOfTheEnumIsReported() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      kind: enum(values: [A B]) { description: \"the kind\" example: \"C\" }\n"
                + "    }\n"));
        assertOneError(d, "allows only one of");
    }

    @Test
    public void anExampleThatIsTooShortIsReported() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      name: string(minLength: 3 description: \"the name\") { example: \"ab\" }\n"
                + "    }\n"));
        assertOneError(d, "at least 3 characters");
    }

    @Test
    public void anExampleThatIsTooLongIsReported() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      name: string(maxLength: 3 description: \"the name\") { example: \"abcd\" }\n"
                + "    }\n"));
        assertOneError(d, "at most 3 characters");
    }

    @Test
    public void anExampleThatDoesNotMatchThePatternIsReported() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      name: string(pattern: \"^[a-z]+$\" description: \"the name\") { example: \"ABC\" }\n"
                + "    }\n"));
        assertOneError(d, "does not match the pattern");
    }

    @Test
    public void aNumberOutsideOfTheRangeIsReported() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      size: number(minimum: 1 maximum: 10 description: \"the size\") { example: 42 }\n"
                + "    }\n"));
        assertOneError(d, "less than or equal to 10");
    }

    @Test
    public void anExampleMissingARequiredPropertyIsReported() {
        NoApiDiagnostics d = validate(doc(""
                + "    @example({ \"other\": \"x\" })\n"
                + "    Thing: object {\n"
                + "      @required name: string(description: \"the name\")\n"
                + "    }\n"));
        assertOneError(d, "missing the required property \"name\"");
    }

    @Test
    public void anExampleOfANestedPropertyIsChecked() {
        NoApiDiagnostics d = validate(doc(""
                + "    @example({ \"child\": { \"name\": 12 } })\n"
                + "    Thing: object {\n"
                + "      child: Child\n"
                + "    }\n"
                + "    Child: object {\n"
                + "      @required name: string(description: \"the name\")\n"
                + "    }\n"));
        assertOneError(d, "is a number");
    }

    @Test
    public void anExampleOfAnArrayItemIsChecked() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      names: array(description: \"the names\" example: [\"a\" 2])[string]\n"
                + "    }\n"));
        assertOneError(d, "is a number");
    }

    @Test
    public void anExampleOfARequestBodyIsCheckedAgainstTheReferencedSchema() {
        String tson = ""
                + "@openapi(\"3.0.0\")\n"
                + "info { title: \"T\", version: \"1.0.0\" }\n"
                + "paths {\n"
                + "  POST(\"/thing\") {\n"
                + "    requestBody: @required Thing(json) {\n"
                + "      description: \"the thing\"\n"
                + "      example: { \"name\": 12 }\n"
                + "    }\n"
                + "    out(200): void { description: \"nothing\" }\n"
                + "  }\n"
                + "}\n"
                + "components {\n"
                + "  schemas {\n"
                + "    Thing: object {\n"
                + "      @required name: string(description: \"the name\")\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        NoApiDiagnostics d = validate(tson);
        assertOneError(d, "is a number");
    }

    @Test
    public void anExampleOfAResponseIsCheckedAgainstTheReferencedSchema() {
        String tson = ""
                + "@openapi(\"3.0.0\")\n"
                + "info { title: \"T\", version: \"1.0.0\" }\n"
                + "paths {\n"
                + "  POST(\"/thing\") {\n"
                + "    out(200): Thing(json) {\n"
                + "      description: \"the thing\"\n"
                + "      example: { \"name\": 12 }\n"
                + "    }\n"
                + "  }\n"
                + "}\n"
                + "components {\n"
                + "  schemas {\n"
                + "    Thing: object {\n"
                + "      @required name: string(description: \"the name\")\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        NoApiDiagnostics d = validate(tson);
        assertOneError(d, "is a number");
    }

    @Test
    public void anExampleOfADeclaredSchemaIsCheckedAgainstItsOwnSchema() {
        NoApiDiagnostics d = validate(doc(""
                + "    @example({ \"name\": 12 })\n"
                + "    Thing: object {\n"
                + "      @required name: string(description: \"the name\")\n"
                + "    }\n"));
        assertOneError(d, "is a number");
    }

    @Test
    public void anExampleOfATimestampMustBeADateTime() {
        NoApiDiagnostics d = validate(doc(""
                + "    Thing: object {\n"
                + "      at: timestamp { description: \"the date\" example: \"not a date\" }\n"
                + "    }\n"));
        assertOneError(d, "not a valid date-time");
    }

    @Test
    public void theDocumentIsRejectedWhenAnExampleIsInvalid() {
        String tson = ""
                + "@openapi(\"3.0.0\")\n"
                + "info { title: \"T\", version: \"1.0.0\" }\n"
                + "paths {\n"
                + "}\n"
                + "components {\n"
                + "  schemas {\n"
                + "    Thing: object {\n"
                + "      @required name: string(description: \"the name\") { example: 12 }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        try {
            NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
            fail("the document must be rejected");
        } catch (NoApiDesugarException ex) {
            String all = ex.getMessage();
            assertTrue("the error must be reported: " + all, all.contains("E_EXAMPLE_MISMATCH"));
        }
    }
}
