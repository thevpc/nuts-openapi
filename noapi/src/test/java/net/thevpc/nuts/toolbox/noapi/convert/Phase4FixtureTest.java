package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NArrayElement;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NObjectElement;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Revision 2 acceptance test on the {@code ftusa-api.tson} fixture: the condensed document is
 * read into the expected OpenAPI tree, and the tree is written back to condensed TSON that reads
 * to the very same tree (self-verification, no fallback to plain TSON).
 */
public class Phase4FixtureTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static String fixture() throws IOException {
        try (InputStream is = Phase4FixtureTest.class.getResourceAsStream("/ftusa-api.tson")) {
            assertNotNull("fixture /ftusa-api.tson not found", is);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                bos.write(buffer, 0, read);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static NElement node(NElement root, String... path) {
        NElement cur = root;
        for (String p : path) {
            assertNotNull("missing " + String.join(".", path), cur);
            NObjectElement o = cur.asObject().orNull();
            if (o != null) {
                cur = o.get(p).orNull();
            } else {
                NArrayElement a = cur.asArray().orNull();
                cur = a == null ? null : a.get(Integer.parseInt(p)).orNull();
            }
        }
        assertNotNull("missing " + String.join(".", path), cur);
        return cur;
    }

    private static String str(NElement root, String... path) {
        return node(root, path).asStringValue().orElse(null);
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

    private static List<String> strings(NElement arr) {
        List<String> r = new ArrayList<>();
        for (NElement e : arr.asArray().get().children()) {
            r.add(e.asStringValue().orElse(null));
        }
        return r;
    }

    private static List<String> keys(NElement object) {
        List<String> r = new ArrayList<>();
        for (NElement e : object.asObject().get()) {
            r.add(Tson.nameOf(e));
        }
        return r;
    }

    @Test
    public void fixtureReadsToTheExpectedTree() throws IOException {
        NElement model = NoApiConversion.readModelString(fixture(), NoApiFormat.TSON, NoApiConversion.Kind.API);

        // global security
        NElement security = node(model, "security");
        assertEquals(1, security.asArray().get().size());
        assertEquals(0, node(model, "security", "0", "ApiKeyAuth").asArray().get().size());

        // security schemes
        assertEquals("apiKey", str(model, "components", "securitySchemes", "ApiKeyAuth", "type"));
        assertEquals("header", str(model, "components", "securitySchemes", "ApiKeyAuth", "in"));
        assertEquals("X-API-KEY", str(model, "components", "securitySchemes", "ApiKeyAuth", "name"));
        assertEquals("http", str(model, "components", "securitySchemes", "JWTAuth", "type"));
        assertEquals("bearer", str(model, "components", "securitySchemes", "JWTAuth", "scheme"));
        assertEquals("JWT", str(model, "components", "securitySchemes", "JWTAuth", "bearerFormat"));

        // login operation: no security of its own, condensed request body
        assertTrue("login must not define security", !has(model, "paths", "/auth/login", "post", "security"));
        assertEquals(Boolean.TRUE, node(model, "paths", "/auth/login", "post", "requestBody", "required")
                .asBooleanValue().orElse(null));
        assertEquals("#/components/schemas/LoginRequest", str(model, "paths", "/auth/login", "post",
                "requestBody", "content", "application/json", "schema", "$ref"));

        // vehicle status operation: both schemes are required
        NElement opSecurity = node(model, "paths", "/vehicle/status", "post", "security");
        assertEquals(1, opSecurity.asArray().get().size());
        NElement requirement = opSecurity.asArray().get().children().get(0);
        assertEquals(2, requirement.asObject().get().size());
        assertEquals(0, requirement.asObject().get().get("ApiKeyAuth").get().asArray().get().size());
        assertEquals(0, requirement.asObject().get().get("JWTAuth").get().asArray().get().size());

        // enum sugar
        NElement coverage = node(model, "components", "schemas", "VehicleStatusResponse",
                "properties", "coverage");
        assertEquals("string", str(coverage, "type"));
        assertEquals(Arrays.asList("COVERED", "NOT_COVERED", "UNKNOWN"), strings(node(coverage, "enum")));
        assertNotNull(str(coverage, "description"));
        assertNotNull(str(coverage, "example"));

        // timestamp sugar
        NElement resolvedAt = node(model, "components", "schemas", "VehicleStatusResponse",
                "properties", "resolvedAt");
        assertEquals("string", str(resolvedAt, "type"));
        assertEquals("date-time", str(resolvedAt, "format"));
        assertNotNull(str(resolvedAt, "description"));

        // required lists
        assertEquals(Arrays.asList("success", "apiVersion", "data"), strings(node(model, "components", "schemas",
                "VehicleStatusResponseSuccess", "required")));
        assertEquals(Arrays.asList("success", "apiVersion", "data"), strings(node(model, "components", "schemas",
                "LoginResponseSuccess", "required")));
        assertEquals(Arrays.asList("success", "apiVersion", "error", "details"), strings(node(model, "components",
                "schemas", "ErrorResponse", "required")));
        assertEquals(Arrays.asList("coverage", "insuranceIdentity", "vehicleStatus", "resolvedAt"),
                strings(node(model, "components", "schemas", "VehicleStatusResponse", "required")));

        // the nested objects are named schemas, and a reference keeps its description
        assertEquals("#/components/schemas/VehicleStatusResponse", str(model, "components", "schemas",
                "VehicleStatusResponseSuccess", "properties", "data", "$ref"));
        assertEquals("Consolidated insurance situation of the vehicle.",
                str(model, "components", "schemas", "VehicleStatusResponseSuccess", "properties", "data", "description"));
        assertEquals("#/components/schemas/LoginResponse", str(model, "components", "schemas",
                "LoginResponseSuccess", "properties", "data", "$ref"));
        assertEquals("Tokens issued to the authenticated client.",
                str(model, "components", "schemas", "LoginResponseSuccess", "properties", "data", "description"));

        // the document id, the contact and the change log
        assertEquals("ftusa-api", str(model, "custom", "openapi-document-id"));
        assertEquals("support@avidea.com", str(model, "info", "contact", "email"));
        assertEquals("Avidea - FTUSA integration support", str(model, "info", "contact", "name"));
        assertEquals("1.0.0", str(model, "info", "changes", "0", "version"));

        // the common headers
        assertEquals("string", str(model, "components", "headers", "X-API-KEY", "schema", "type"));
        assertNotNull(str(model, "components", "headers", "X-API-KEY", "description"));

        // the examples of the request bodies and of the responses
        assertNotNull(node(model, "paths", "/vehicle/status", "post", "requestBody", "content",
                "application/json", "example"));
        assertNotNull(node(model, "paths", "/vehicle/status", "post", "responses", "200", "content",
                "application/json", "example"));

        // every failure mode is documented, including the internal error
        assertEquals(Arrays.asList("200", "400", "504", "401", "500", "429"), keys(node(model, "paths",
                "/vehicle/status", "post", "responses")));
        assertEquals(Arrays.asList("200", "401", "429", "500"), keys(node(model, "paths",
                "/auth/login", "post", "responses")));
    }

    @Test
    public void fixtureDeclaresTheSharedResponsesAndReferencesThem() throws IOException {
        NElement model = NoApiConversion.readModelString(fixture(), NoApiFormat.TSON, NoApiConversion.Kind.API);

        // components.responses: the shared responses of the document
        NElement responses = node(model, "components", "responses");
        assertEquals(Arrays.asList("InternalError", "Unauthorized", "TooManyRequests"), keys(responses));
        assertEquals("#/components/schemas/ErrorResponse", str(responses, "InternalError", "content",
                "application/json", "schema", "$ref"));
        assertEquals("Unexpected server failure; the request can be retried.",
                str(responses, "InternalError", "description"));

        // a response that references one of them is a $ref
        assertEquals("#/components/responses/TooManyRequests", str(model, "paths", "/auth/login", "post",
                "responses", "429", "$ref"));
        assertEquals("#/components/responses/InternalError", str(model, "paths", "/vehicle/status", "post",
                "responses", "500", "$ref"));
        assertEquals("#/components/responses/Unauthorized", str(model, "paths", "/vehicle/status", "post",
                "responses", "401", "$ref"));

        // the other responses stay inline
        assertEquals("#/components/schemas/ErrorResponse", str(model, "paths", "/auth/login", "post",
                "responses", "401", "content", "application/json", "schema", "$ref"));
    }

    @Test
    public void fixtureRoundTripsThroughTheCondensedWriter() throws IOException {
        NElement model = NoApiConversion.readModelString(fixture(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        NoApiConversion.Result result = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue("must stay condensed: " + result.note(), result.condensed());
        NElement back = NoApiConversion.readModelString(result.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        String diff = NoApiTreeComparator.diff(model, back, "$");
        assertNull("round trip diff: " + diff, diff);
    }
}
