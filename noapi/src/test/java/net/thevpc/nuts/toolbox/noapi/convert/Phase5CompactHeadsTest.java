package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NArrayElement;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NObjectElement;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 5 tests: the "single form" of the condensed dialect, where the attributes of a type are
 * written in the parentheses of its head ({@code string(description: "d")},
 * {@code LoginRequest(description: "d") { … }}) instead of in a trailing body.
 *
 * <p>The two forms must give the very same tree: the compact one is only a shorter spelling.</p>
 */
public class Phase5CompactHeadsTest {

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

    private static String str(NElement root, String... path) {
        return node(root, path).asStringValue().orElse(null);
    }

    private static List<String> strings(NElement arr) {
        List<String> r = new ArrayList<>();
        for (NElement e : arr.asArray().get().children()) {
            r.add(e.asStringValue().orElse(null));
        }
        return r;
    }

    @Test
    public void schemaDeclarationWithoutTheObjectKeyword() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components {\n"
                + "  schemas {\n"
                + "    LoginRequest(\n"
                + "      description: \"Credentials.\"\n"
                + "      example: { username: \"u\" }\n"
                + "    ) {\n"
                + "      @required username: string(description: \"Login.\", minLength: 3, maxLength: 64, example: \"u\")\n"
                + "    }\n"
                + "  }\n"
                + "}\n");
        NElement s = node(model, "components", "schemas", "LoginRequest");
        assertEquals("object", str(s, "type"));
        assertEquals("Credentials.", str(s, "description"));
        assertEquals("u", str(s, "example", "username"));
        assertEquals("string", str(s, "properties", "username", "type"));
        assertEquals("Login.", str(s, "properties", "username", "description"));
        assertEquals("3", str(s, "properties", "username", "minLength"));
        assertEquals("64", str(s, "properties", "username", "maxLength"));
        assertEquals("u", str(s, "properties", "username", "example"));
        assertEquals(Arrays.asList("username"), strings(node(s, "required")));
    }

    @Test
    public void theTwoSpellingsGiveTheSameTree() {
        NElement compact = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components {\n"
                + "  schemas {\n"
                + "    LoginRequest(description: \"Credentials.\", example: { username: \"u\" }) {\n"
                + "      @required username: string(description: \"Login.\", minLength: 3, example: \"u\")\n"
                + "    }\n"
                + "  }\n"
                + "}\n");
        NElement verbose = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components {\n"
                + "  schemas {\n"
                + "    @description(\"Credentials.\")\n"
                + "    @example({ username: \"u\" })\n"
                + "    LoginRequest: object {\n"
                + "      @required username: string {\n"
                + "        description: \"Login.\"\n"
                + "        minLength: 3\n"
                + "        example: \"u\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n");
        String diff = NoApiTreeComparator.diff(verbose, compact, "$");
        assertNull("the two spellings must give the same tree: " + diff, diff);
    }

    @Test
    public void enumAndTimestampAttributesInTheHead() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components {\n"
                + "  schemas {\n"
                + "    S {\n"
                + "      @required coverage: enum(values: [COVERED UNKNOWN], description: \"The coverage.\", example: \"UNKNOWN\")\n"
                + "      @required resolvedAt: timestamp(description: \"When.\", example: \"2026-10-10T09:15:00Z\")\n"
                + "    }\n"
                + "  }\n"
                + "}\n");
        NElement s = node(model, "components", "schemas", "S");
        assertEquals("string", str(s, "properties", "coverage", "type"));
        assertEquals("The coverage.", str(s, "properties", "coverage", "description"));
        assertEquals("UNKNOWN", str(s, "properties", "coverage", "example"));
        assertEquals(Arrays.asList("COVERED", "UNKNOWN"),
                strings(node(s, "properties", "coverage", "enum")));
        assertEquals("string", str(s, "properties", "resolvedAt", "type"));
        assertEquals("date-time", str(s, "properties", "resolvedAt", "format"));
        assertEquals("When.", str(s, "properties", "resolvedAt", "description"));
    }

    @Test
    public void requestBodyAndResponseAttributesInTheHead() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components { schemas { LoginRequest { @required username: string } } }\n"
                + "paths {\n"
                + "  POST(\"/auth/login\") {\n"
                + "    operationId: login\n"
                + "    in: @required LoginRequest(json, description: \"Credentials.\", example: { username: \"u\" })\n"
                + "    out(200): LoginRequest(json, description: \"Tokens.\", example: { username: \"v\" })\n"
                + "  }\n"
                + "}\n");
        NElement op = node(model, "paths", "/auth/login", "post");
        assertEquals("true", str(op, "requestBody", "required"));
        assertEquals("Credentials.", str(op, "requestBody", "description"));
        assertEquals("#/components/schemas/LoginRequest",
                str(op, "requestBody", "content", "application/json", "schema", "$ref"));
        assertEquals("u", str(op, "requestBody", "content", "application/json", "example", "username"));
        assertEquals("Tokens.", str(op, "responses", "200", "description"));
        assertEquals("#/components/schemas/LoginRequest",
                str(op, "responses", "200", "content", "application/json", "schema", "$ref"));
        assertEquals("v", str(op, "responses", "200", "content", "application/json", "example", "username"));
        // the named params are attributes, not media types
        assertFalse(op.asObject().get().get("requestBody").get().asObject().get()
                .get("content").get().asObject().get().get("description").isPresent());
    }

    @Test
    public void responseComponents() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components {\n"
                + "  schemas { ErrorResponse { @required error: string } }\n"
                + "  responses {\n"
                + "    InternalError: ErrorResponse(json, description: \"Unexpected failure.\", example: { error: \"InternalError\" })\n"
                + "  }\n"
                + "}\n"
                + "paths {\n"
                + "  GET(\"/x\") {\n"
                + "    out(500): InternalError\n"
                + "  }\n"
                + "}\n");
        NElement comp = node(model, "components", "responses", "InternalError");
        assertEquals("Unexpected failure.", str(comp, "description"));
        assertEquals("#/components/schemas/ErrorResponse",
                str(comp, "content", "application/json", "schema", "$ref"));
        assertEquals("InternalError", str(comp, "content", "application/json", "example", "error"));
        assertEquals("#/components/responses/InternalError",
                str(model, "paths", "/x", "get", "responses", "500", "$ref"));
    }

    @Test
    public void securitySchemesWithAttributesInTheHead() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components {\n"
                + "  securitySchemes {\n"
                + "    ApiKeyAuth: apiKey(header: X-API-KEY, description: \"The key.\")\n"
                + "    JWTAuth: http(bearer, bearerFormat: JWT, description: \"The token.\")\n"
                + "  }\n"
                + "}\n");
        NElement apiKey = node(model, "components", "securitySchemes", "ApiKeyAuth");
        assertEquals("apiKey", str(apiKey, "type"));
        assertEquals("header", str(apiKey, "in"));
        assertEquals("X-API-KEY", str(apiKey, "name"));
        assertEquals("The key.", str(apiKey, "description"));
        NElement jwt = node(model, "components", "securitySchemes", "JWTAuth");
        assertEquals("http", str(jwt, "type"));
        assertEquals("bearer", str(jwt, "scheme"));
        assertEquals("JWT", str(jwt, "bearerFormat"));
        assertEquals("The token.", str(jwt, "description"));
    }

    @Test
    public void serversWithAttributesInTheHead() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "servers [ url(\"https://x/api\", description: \"The server.\") ]\n");
        assertEquals("https://x/api", str(model, "servers", "0", "url"));
        assertEquals("The server.", str(model, "servers", "0", "description"));
    }

    @Test
    public void summaryAnnotationOnTheOperationHead() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "paths {\n"
                + "  @summary(\"Authenticate a client\")\n"
                + "  POST(\"/auth/login\") {\n"
                + "    operationId: login\n"
                + "  }\n"
                + "}\n");
        assertEquals("Authenticate a client", str(model, "paths", "/auth/login", "post", "summary"));
    }

    @Test
    public void commasAreAcceptedAsSeparators() {
        NElement model = desugar(""
                + "info { id: \"x\", title: \"T\", version: \"1.0.0\" }\n"
                + "components { schemas { S { @required a: string(minLength: 1, maxLength: 2) } } }\n");
        NElement s = node(model, "components", "schemas", "S");
        assertEquals("1", str(s, "properties", "a", "minLength"));
        assertEquals("2", str(s, "properties", "a", "maxLength"));
    }
}
