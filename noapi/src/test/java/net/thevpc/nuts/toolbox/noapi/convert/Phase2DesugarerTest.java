package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NArrayElement;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementType;
import net.thevpc.nuts.elem.NObjectElement;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase 2 tests: the condensed reader (desugarer) and its validation pass, including the golden
 * mapping of prompt section 5.
 */
public class Phase2DesugarerTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static NElement desugar(String tson) {
        NElement parsed = NoApiFormats.readString(tson, NoApiFormat.TSON);
        return new CondensedTsonDesugarer(new NoApiDiagnostics("test")).desugar(parsed);
    }

    private static NElement node(NElement root, String... path) {
        NElement cur = root;
        for (String p : path) {
            if (cur == null) {
                break;
            }
            NObjectElement o = cur.asObject().orNull();
            if (o != null) {
                cur = o.get(p).orNull();
            } else {
                NArrayElement a = cur.asArray().orNull();
                cur = a == null ? null : a.get(Integer.parseInt(p)).orNull();
            }
        }
        assertTrue("missing " + String.join(".", path), cur != null);
        return cur;
    }

    private static boolean has(NElement root, String... path) {
        NElement cur = root;
        for (String p : path) {
            if (cur == null) {
                return false;
            }
            NObjectElement o = cur.asObject().orNull();
            if (o != null) {
                cur = o.get(p).orNull();
            } else {
                NArrayElement a = cur.asArray().orNull();
                if (a == null) {
                    return false;
                }
                try {
                    cur = a.get(Integer.parseInt(p)).orNull();
                } catch (RuntimeException ex) {
                    return false;
                }
            }
        }
        return cur != null;
    }

    private static String str(NElement root, String... path) {
        NElement e = node(root, path);
        return e.asStringValue().orElse(null);
    }

    @Test
    public void goldenMapping() {
        String tson = ""
                + "@openapi(\"3.0.0\")\n"
                + "info { title: \"T\", version: \"1.0.0\" }\n"
                + "paths {\n"
                + "  @summary(\"Updates the claim number\")\n"
                + "  POST(\"/claim/set-claim-number\") {\n"
                + "    @required in: SetClaimNumberModel(json) { description: \"Claim number information\", example: \"{...}\" }\n"
                + "    out(200): void { description: \"Returns an empty result\" }\n"
                + "  }\n"
                + "}\n"
                + "components {\n"
                + "  schemas {\n"
                + "    @description(\"Payment request\")\n"
                + "    AddAdjusterPaymentModel: object(format: intermediate-structure) {\n"
                + "      @required claimNumbers: array(description: \"claim numbers\")[string]\n"
                + "      paymentAmount: number(description: \"Amount\", example: \"100\")\n"
                + "    }\n"
                + "    SetClaimNumberModel: object { claimNumber: string }\n"
                + "  }\n"
                + "}\n";

        NElement root = desugar(tson);

        // @openapi lifted from info
        assertEquals("3.0.0", str(root, "openapi"));
        assertEquals(NElementType.OBJECT, root.type());

        // operation
        assertEquals("Updates the claim number", str(root, "paths", "/claim/set-claim-number", "post", "summary"));
        assertEquals("Claim number information",
                str(root, "paths", "/claim/set-claim-number", "post", "requestBody", "description"));
        assertEquals(Boolean.TRUE,
                node(root, "paths", "/claim/set-claim-number", "post", "requestBody", "required").asBooleanValue().orElse(null));
        assertEquals("#/components/schemas/SetClaimNumberModel",
                str(root, "paths", "/claim/set-claim-number", "post", "requestBody", "content", "application/json", "schema", "$ref"));
        assertEquals("{...}",
                str(root, "paths", "/claim/set-claim-number", "post", "requestBody", "content", "application/json", "example"));
        assertEquals("Returns an empty result",
                str(root, "paths", "/claim/set-claim-number", "post", "responses", "200", "description"));
        // void response has no content
        assertTrue(!has(root, "paths", "/claim/set-claim-number", "post", "responses", "200", "content"));

        // schema
        NElement schema = node(root, "components", "schemas", "AddAdjusterPaymentModel");
        assertEquals("object", str(schema, "type"));
        assertEquals("object::intermediate-structure", str(schema, "format"));
        assertEquals("Payment request", str(schema, "description"));
        assertEquals("array", str(schema, "properties", "claimNumbers", "type"));
        assertEquals("string", str(schema, "properties", "claimNumbers", "items", "type"));
        assertEquals("claim numbers", str(schema, "properties", "claimNumbers", "description"));
        assertEquals("number", str(schema, "properties", "paymentAmount", "type"));
        assertEquals("Amount", str(schema, "properties", "paymentAmount", "description"));
        assertEquals("100", str(schema, "properties", "paymentAmount", "example"));
        java.util.List<String> req = new java.util.ArrayList<>();
        for (NElement r : node(schema, "required").asArray().get().children()) {
            req.add(r.asStringValue().get());
        }
        assertEquals(java.util.Collections.singletonList("claimNumbers"), req);
    }

    @Test
    public void plainTsonPassesThrough() {
        // the expanded golden tree written as plain TSON must survive desugaring unchanged
        String tson = ""
                + "info { title: \"T\", version: \"1.0.0\" }\n"
                + "paths {\n"
                + "  \"/x\": {\n"
                + "    get: { summary: \"s\" }\n"
                + "  }\n"
                + "}\n";
        NElement root = desugar(tson);
        assertEquals("s", str(root, "paths", "/x", "get", "summary"));
    }

    @Test
    public void reservedSchemaNameIsRejected() {
        String tson = "components { schemas { string: object { a: string } } }";
        NoApiDiagnostics diag = new NoApiDiagnostics("test");
        try {
            new CondensedTsonDesugarer(diag).desugar(NoApiFormats.readString(tson, NoApiFormat.TSON));
            throw new AssertionError("expected E_RESERVED_NAME");
        } catch (NoApiDesugarException ex) {
            assertTrue(ex.getMessage().contains("E_RESERVED_NAME"));
        }
    }

    @Test
    public void duplicateOperationIsRejected() {
        String tson = "paths {\n  GET(\"/x\") { out(200): void { description: \"d\" } }\n  GET(\"/x\") { out(200): void { description: \"d\" } }\n}";
        NoApiDiagnostics diag = new NoApiDiagnostics("test");
        try {
            new CondensedTsonDesugarer(diag).desugar(NoApiFormats.readString(tson, NoApiFormat.TSON));
            throw new AssertionError("expected E_DUP_OPERATION");
        } catch (NoApiDesugarException ex) {
            assertTrue(ex.getMessage().contains("E_DUP_OPERATION"));
        }
    }

    @Test
    public void missingResponseDescriptionUsesReasonPhrase() {
        NElement root = desugar("paths {\n  GET(\"/x\") { out(200): void }\n}");
        assertEquals("OK", str(root, "paths", "/x", "get", "responses", "200", "description"));
    }

    @Test
    public void securityListShorthand() {
        NElement root = desugar("security [ JWTAuth ]");
        NElement first = node(root, "security").asArray().get().children().get(0);
        NElement scopes = first.asObject().get().get("JWTAuth").orNull();
        assertTrue("JWTAuth entry", scopes != null);
        assertEquals(NElementType.ARRAY, scopes.asArray().isPresent() ? NElementType.ARRAY : scopes.type());
        assertEquals(0, scopes.asArray().get().size());
    }

    @Test
    public void configVariablesShortForm() {
        NElement parsed = NoApiFormats.readString("openapi-config: \"1.0\"\nvariables { server-url: \"https://x\" }", NoApiFormat.TSON);
        NElement root = new CondensedTsonDesugarer(new NoApiDiagnostics("test")).desugarConfig(parsed);
        assertEquals("https://x", str(root, "variables", "server-url", "value"));
        assertEquals("", str(root, "variables", "server-url", "observations"));
    }
}
