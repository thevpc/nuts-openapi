package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NArrayElement;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementType;
import net.thevpc.nuts.elem.NObjectElement;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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

    // ------------------------------------------------------------------ revision 2 : separators (T1)

    @Test
    public void separatorsAreAcceptedInBlocks() {
        NElement root = desugar("components { schemas { S: object { a: string, b: number, } } }");
        assertEquals("string", str(root, "components", "schemas", "S", "properties", "a", "type"));
        assertEquals("number", str(root, "components", "schemas", "S", "properties", "b", "type"));
    }

    @Test
    public void separatorsAreAcceptedInArrays() {
        NElement root = desugar("security [ ApiKeyAuth, JWTAuth, ]");
        assertEquals(2, node(root, "security").asArray().get().size());
    }

    @Test
    public void separatorsAreAcceptedInParentheses() {
        NElement root = desugar("components { schemas { S: enum(values: [A, B,], description: \"d\",) } }");
        assertEquals("string", str(root, "components", "schemas", "S", "type"));
        assertEquals(2, node(root, "components", "schemas", "S", "enum").asArray().get().size());
        assertEquals("d", str(root, "components", "schemas", "S", "description"));
    }

    // ------------------------------------------------------------------ revision 2 : quoting (T2)

    @Test
    public void bareAndQuotedValuesAreBothAccepted() {
        NElement root = desugar("components { schemas { S: enum(values: [A \"B\"]) { description: bare } } }");
        NElement values = node(root, "components", "schemas", "S", "enum");
        assertEquals("A", values.asArray().get().children().get(0).asStringValue().get());
        assertEquals("B", values.asArray().get().children().get(1).asStringValue().get());
        assertEquals("bare", str(root, "components", "schemas", "S", "description"));
    }

    // ------------------------------------------------------------------ revision 2 : security groups (T3)

    @Test
    public void securityGroupExpandsToASingleRequirement() {
        NElement root = desugar("components { securitySchemes { ApiKeyAuth: http(bearer) JWTAuth: http(bearer) } }\nsecurity [ all(ApiKeyAuth JWTAuth) ]");
        NElement req = node(root, "security").asArray().get().children().get(0);
        assertEquals(1, node(root, "security").asArray().get().size());
        assertTrue(req.asObject().get().get("ApiKeyAuth").isPresent());
        assertTrue(req.asObject().get().get("JWTAuth").isPresent());
        assertEquals(0, req.asObject().get().get("ApiKeyAuth").get().asArray().get().size());
        assertEquals(0, req.asObject().get().get("JWTAuth").get().asArray().get().size());
    }

    @Test
    public void securityGroupWithScopesExpandsToASingleRequirement() {
        NElement root = desugar("components { securitySchemes { ApiKeyAuth: http(bearer) OAuth: http(bearer) } }\nsecurity [ all(ApiKeyAuth OAuth(read)) ]");
        NElement req = node(root, "security").asArray().get().children().get(0);
        assertEquals(1, node(root, "security").asArray().get().size());
        assertEquals(0, req.asObject().get().get("ApiKeyAuth").get().asArray().get().size());
        NElement scopes = req.asObject().get().get("OAuth").get();
        assertEquals(1, scopes.asArray().get().size());
        assertEquals("read", scopes.asArray().get().children().get(0).asStringValue().get());
    }

    @Test
    public void emptySecurityListMeansNoAuthentication() {
        NElement root = desugar("security []");
        assertEquals(0, node(root, "security").asArray().get().size());
    }

    @Test
    public void operationWithoutSecurityHasNoSecurityKey() {
        NElement root = desugar("paths { GET(\"/x\") { out(200): void { description: \"d\" } } }");
        assertTrue(!has(root, "paths", "/x", "get", "security"));
    }

    @Test
    public void operationSecurityMemberIsDesugared() {
        NElement root = desugar("components { securitySchemes { ApiKeyAuth: http(bearer) JWTAuth: http(bearer) } }\n"
                + "paths { GET(\"/x\") { security [ all(ApiKeyAuth JWTAuth) ] out(200): void { description: \"d\" } } }");
        NElement sec = node(root, "paths", "/x", "get", "security");
        assertEquals(1, sec.asArray().get().size());
        assertEquals(2, sec.asArray().get().children().get(0).asObject().get().size());
    }

    @Test
    public void emptySecurityGroupIsRejected() {
        expectError("components { securitySchemes { ApiKeyAuth: http(bearer) } }\nsecurity [ all() ]", "E_BAD_SECURITY");
    }

    @Test
    public void securitySchemeNamedAllIsRejected() {
        expectError("components { securitySchemes { all: http(bearer) } }", "E_RESERVED_NAME");
    }

    // ------------------------------------------------------------------ revision 2 : enum/timestamp sugar (T4)

    @Test
    public void enumSugarExpandsToStringWithEnum() {
        NElement root = desugar("components { schemas { S: enum(values: [A B]) } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("string", str(s, "type"));
        NElement values = node(s, "enum");
        assertEquals(2, values.asArray().get().size());
        assertEquals("A", values.asArray().get().children().get(0).asStringValue().get());
        assertEquals("B", values.asArray().get().children().get(1).asStringValue().get());
    }

    @Test
    public void timestampSugarExpandsToStringWithDateTime() {
        NElement root = desugar("components { schemas { S: timestamp } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("string", str(s, "type"));
        assertEquals("date-time", str(s, "format"));
    }

    @Test
    public void enumAndTimestampAreReservedSchemaNames() {
        expectError("components { schemas { enum: object { a: string } } }", "E_RESERVED_NAME");
        expectError("components { schemas { timestamp: object { a: string } } }", "E_RESERVED_NAME");
    }

    // ------------------------------------------------------------------ revision 2 : attributes in body (T5)

    @Test
    public void primitiveAttributesMayBeGivenInTheBody() {
        NElement root = desugar("components { schemas { S: string(minLength: 1) { @metadata description: \"d\" } } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("string", str(s, "type"));
        assertEquals("d", str(s, "description"));
        assertEquals("1", str(s, "minLength"));
    }

    @Test
    public void primitiveAttributesInParenthesesAndBodyAreMerged() {
        NElement root = desugar("components { schemas { S: string(maxLength: 10) { @metadata description: \"d\" } } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("10", str(s, "maxLength"));
        assertEquals("d", str(s, "description"));
    }

    @Test
    public void primitiveBodyDescriptionIsAnAttribute() {
        NElement root = desugar("components { schemas { S: string { description: \"d\" } } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("d", str(s, "description"));
    }

    @Test
    public void duplicatedAttributeIsRejected() {
        expectError("components { schemas { S: string(description: \"a\") { @metadata description: \"b\" } } }", "E_DUPLICATE_ATTR");
    }

    // ------------------------------------------------------------------ revision 2 : properties block (T6)

    @Test
    public void explicitPropertiesBlockIsRead() {
        NElement root = desugar("components { schemas { S: object { properties { a: string b: number } } } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("string", str(s, "properties", "a", "type"));
        assertEquals("number", str(s, "properties", "b", "type"));
    }

    @Test
    public void explicitPropertiesBlockKeepsTheSchemaExample() {
        NElement root = desugar("components { schemas { S: object { @metadata example: { a: 1 } properties { a: string } } } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("string", str(s, "properties", "a", "type"));
        assertEquals("1", str(s, "example", "a"));
    }

    @Test
    public void definitionIsAnOrdinaryProperty() {
        NElement root = desugar("components { schemas { S: object { definition { type: string } } } }");
        NElement s = node(root, "components", "schemas", "S");
        assertTrue("definition is a property", s.asObject().get().get("properties").get()
                .asObject().get().get("definition").isPresent());
        assertEquals("string", str(s, "properties", "definition", "type"));
    }

    // ------------------------------------------------------------------ revision 2 : request body key (T7)

    @Test
    public void condensedRequestBodyIsAccepted() {
        NElement root = desugar("paths { POST(\"/x\") { requestBody: @required LoginRequest(json) } }");
        NElement rb = node(root, "paths", "/x", "post", "requestBody");
        assertEquals(Boolean.TRUE, rb.asObject().get().get("required").orNull().asBooleanValue().orElse(null));
        assertEquals("#/components/schemas/LoginRequest",
                str(rb, "content", "application/json", "schema", "$ref"));
    }

    @Test
    public void twoRequestBodiesAreRejected() {
        expectError("paths { POST(\"/x\") { in: LoginRequest(json) requestBody: LoginRequest(json) } }", "E_DUP_KEY");
    }

    @Test
    public void plainRequestBodyIsAccepted() {
        NElement root = desugar("paths { POST(\"/x\") { requestBody: { description: \"d\" required: true content: { \"application/json\": { schema: { type: string } } } } } }");
        assertEquals("d", str(root, "paths", "/x", "post", "requestBody", "description"));
        assertEquals("string", str(root, "paths", "/x", "post", "requestBody", "content", "application/json", "schema", "type"));
    }

    // ------------------------------------------------------------------ revision 2 : apiKey arguments (T8)

    @Test
    public void apiKeyWithHeaderArgumentIsAccepted() {
        NElement root = desugar("components { securitySchemes { ApiKeyAuth: apiKey(header: X-API-KEY) } }");
        NElement k = node(root, "components", "securitySchemes", "ApiKeyAuth");
        assertEquals("apiKey", str(k, "type"));
        assertEquals("header", str(k, "in"));
        assertEquals("X-API-KEY", str(k, "name"));
    }

    @Test
    public void apiKeyWithQuotedNameIsAccepted() {
        NElement root = desugar("components { securitySchemes { ApiKeyAuth: apiKey(query: \"api key\") } }");
        NElement k = node(root, "components", "securitySchemes", "ApiKeyAuth");
        assertEquals("query", str(k, "in"));
        assertEquals("api key", str(k, "name"));
    }

    @Test
    public void apiKeyWithNameAndInIsRejected() {
        expectError("components { securitySchemes { ApiKeyAuth: apiKey(name: X-API-KEY in: header) } }", "E_APIKEY_ARGS");
    }

    // ------------------------------------------------------------------ paragraph strings and temporal literals

    @Test
    public void lineAndBlockStringsAreReadAsText() {
        NElement root = desugar("info {\n title: \"t\"\n version: \"1\"\n description: ¶¶ first line\n¶¶ second line\n}");
        assertEquals("first line\nsecond line", str(root, "info", "description"));
    }

    @Test
    public void schemaMetadataDescriptionMayUseABlockString() {
        NElement root = desugar("components {\n schemas {\n S: string(minLength: 1) {\n @metadata description: ¶¶ long text\n¶¶ more text\n }\n }\n}");
        assertEquals("long text\nmore text", str(root, "components", "schemas", "S", "description"));
    }

    @Test
    public void temporalLiteralsAreKeptAsStrings() {
        NElement root = desugar("components { schemas { S: object { a: string } } }\n"
                + "paths { GET(\"/x\") { out(200): S(json) { description: \"d\"\n example: {\n at: 2026-02-03T10:15:00Z\n day: 2026-02-03\n }\n } } }");
        assertEquals("2026-02-03T10:15:00Z", str(root, "paths", "/x", "get", "responses", "200", "content", "application/json", "example", "at"));
        assertEquals("2026-02-03", str(root, "paths", "/x", "get", "responses", "200", "content", "application/json", "example", "day"));
    }

    // ------------------------------------------------------------------ media types

    @Test
    public void mediatypesAreExpandedToContentTypes() {
        NElement root = desugar("components { schemas { S: object { a: string } } }\n"
                + "paths { GET(\"/x\") { out(200): S(csv) { description: \"d\" } } }");
        assertEquals("#/components/schemas/S", str(root, "paths", "/x", "get", "responses", "200", "content", "text/csv", "schema", "$ref"));
    }

    // ------------------------------------------------------------------ revision 2 : datetime alias (T9)

    @Test
    public void datetimeIsAnAliasOfTimestamp() {
        NElement root = desugar("components { schemas { S: datetime } }");
        NElement s = node(root, "components", "schemas", "S");
        assertEquals("string", str(s, "type"));
        assertEquals("date-time", str(s, "format"));
    }

    @Test
    public void datetimeIsAReservedSchemaName() {
        expectError("components { schemas { datetime: object { a: string } } }", "E_RESERVED_NAME");
    }

    // ------------------------------------------------------------------ revision 2 : the document id (T10)

    @Test
    public void infoIdBecomesTheCustomDocumentId() {
        NElement root = desugar("info { id: my-api title: \"t\" version: \"1\" }");
        assertEquals("my-api", str(root, "custom", "openapi-document-id"));
        assertTrue("the id is removed from the info", !has(root, "info", "id"));
    }

    @Test
    public void infoIdIsMergedWithAnExplicitCustomSection() {
        NElement root = desugar("info { id: my-api title: \"t\" version: \"1\" }\ncustom { config: { a: 1 } }");
        assertEquals("my-api", str(root, "custom", "openapi-document-id"));
        assertEquals("1", str(root, "custom", "config", "a"));
    }

    @Test
    public void twoDocumentIdsAreRejected() {
        expectError("info { id: my-api title: \"t\" version: \"1\" }\ncustom { openapi-document-id: other }", "E_DUPLICATE_ID");
    }

    @Test
    public void theCanonicalizerMovesTheInfoIdToTheCustomSection() {
        NElement model = NoApiConversion.readModelString(
                "{\"openapi\":\"3.0.0\",\"info\":{\"id\":\"my-api\",\"title\":\"t\",\"version\":\"1\"}}",
                NoApiFormat.JSON, NoApiConversion.Kind.API);
        assertEquals("my-api", str(model, "custom", "openapi-document-id"));
        assertTrue("the id is removed from the info", !has(model, "info", "id"));
    }

    // ------------------------------------------------------------------ revision 2 : duplicated keys (T11)

    @Test
    public void duplicatedKeysKeepTheLastValue() {
        NoApiDiagnostics diag = new NoApiDiagnostics("test");
        NElement root = new CondensedTsonDesugarer(diag).desugar(
                NoApiFormats.readString("info { title: \"a\" version: \"1\" title: \"b\" }", NoApiFormat.TSON));
        assertEquals("b", str(root, "info", "title"));
        boolean warned = false;
        for (NoApiDiagnostics.Diagnostic d : diag.all()) {
            if ("W_DUP_KEY".equals(d.code)) {
                warned = true;
            }
        }
        assertTrue("a W_DUP_KEY warning is expected", warned);
    }

    @Test
    public void duplicatedPropertiesKeepTheLastValue() {
        NElement root = desugar("components { schemas { S: object { a: string a: number } } }");
        assertEquals("number", str(root, "components", "schemas", "S", "properties", "a", "type"));
    }

    @Test
    public void theCustomSectionIsPlacedRightAfterTheInfo() {
        NElement root = desugar("info { title: \"t\" version: \"1\" }\npaths {}\ncustom { config: { a: 1 } }");
        List<String> keys = new ArrayList<>();
        for (NElement m : root.asObject().get().children()) {
            keys.add(Tson.nameOf(m));
        }
        assertEquals("[info, custom, paths]", keys.toString());
    }

    // ------------------------------------------------------------------ revision 2 : the operation object members

    @Test
    public void theOperationBodyAcceptsTheWholeOperationObject() {
        NElement root = desugar("paths { POST(\"/x\") {\n"
                + "  summary: \"s\"\n"
                + "  description: \"d\"\n"
                + "  operationId: op\n"
                + "  tags: [a b]\n"
                + "  deprecated: true\n"
                + "  externalDocs: { url: \"https://example.com\" }\n"
                + "  servers: [ { url: \"https://example.com\" } ]\n"
                + "  callbacks: { cb: { \"https://example.com\": { post: { responses: {} } } } }\n"
                + "  parameters: [ { name: \"q\" in: query } ]\n"
                + "  responses: { 200: { description: \"ok\" } }\n"
                + "} }");
        NElement op = node(root, "paths", "/x", "post");
        assertEquals("s", str(op, "summary"));
        assertEquals("d", str(op, "description"));
        assertEquals("op", str(op, "operationId"));
        assertEquals("a", str(op, "tags", "0"));
        assertEquals(Boolean.TRUE, op.asObject().get().get("deprecated").orNull().asBooleanValue().orElse(null));
        assertEquals("https://example.com", str(op, "externalDocs", "url"));
        assertEquals("https://example.com", str(op, "servers", "0", "url"));
        assertTrue("the callback operation is expected", has(op, "callbacks", "cb", "https://example.com", "post", "responses"));
        assertEquals("q", str(op, "parameters", "0", "name"));
        assertEquals("ok", str(op, "responses", "200", "description"));
    }

    @Test
    public void theResponseBodyAcceptsTheWholeResponseObject() {
        NElement root = desugar("paths { GET(\"/x\") { out(200): string(json) {\n"
                + "  description: \"ok\"\n"
                + "  headers: { \"X-Rate\": { schema: { type: integer } } }\n"
                + "  links: { self: { operationId: \"getX\" } }\n"
                + "} } }");
        NElement resp = node(root, "paths", "/x", "get", "responses", "200");
        assertEquals("ok", str(resp, "description"));
        assertEquals("integer", str(resp, "headers", "X-Rate", "schema", "type"));
        assertEquals("getX", str(resp, "links", "self", "operationId"));
    }

    @Test
    public void severalExamplesAreReadAsTheMediaExamplesMap() {
        NElement root = desugar("paths { GET(\"/x\") { out(200): string(json) {\n"
                + "  description: \"ok\"\n"
                + "  examples { first { summary: \"the first one\" value { code: \"E1\" } } }\n"
                + "} } }");
        NElement ex = node(root, "paths", "/x", "get", "responses", "200", "content", "application/json", "examples");
        assertEquals("the first one", str(ex, "first", "summary"));
        assertEquals("E1", str(ex, "first", "value", "code"));
    }

    @Test
    public void anExampleAndSeveralExamplesInTheSameBodyAreRejected() {
        expectError("paths { GET(\"/x\") { out(200): string(json) { example: { a: 1 } examples { first { value: { a: 1 } } } } } }",
                "E_DUP_KEY");
    }

    @Test
    public void aMemberWithoutNameIsRejected() {
        expectError("components { schemas { S: object { @example{ a: 1 } } } }", "E_SYNTAX");
    }

    @Test
    public void aReferenceKeepsItsAttributes() {
        String tson = ""
                + "components {\n"
                + "  schemas {\n"
                + "    Tokens: object { accessToken: string }\n"
                + "    Holder: object {\n"
                + "      data: Tokens(description: \"the tokens\", example: \"abc\")\n"
                + "      @description(\"annotated\")\n"
                + "      other: Tokens\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        NElement root = desugar(tson);
        NElement data = node(root, "components", "schemas", "Holder", "properties", "data");
        assertEquals("#/components/schemas/Tokens", str(data, "$ref"));
        assertEquals("the tokens", str(data, "description"));
        assertEquals("abc", str(data, "example"));
        NElement other = node(root, "components", "schemas", "Holder", "properties", "other");
        assertEquals("#/components/schemas/Tokens", str(other, "$ref"));
        assertEquals("annotated", str(other, "description"));
    }

    private static void expectError(String tson, String code) {
        NoApiDiagnostics diag = new NoApiDiagnostics("test");
        try {
            new CondensedTsonDesugarer(diag).desugar(NoApiFormats.readString(tson, NoApiFormat.TSON));
        } catch (NoApiDesugarException ex) {
            assertTrue("expected " + code + " but got: " + ex.getMessage(), ex.getMessage().contains(code));
            return;
        }
        throw new AssertionError("expected " + code);
    }
}
