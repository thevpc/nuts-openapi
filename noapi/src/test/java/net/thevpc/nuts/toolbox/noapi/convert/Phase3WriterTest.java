package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.io.NPath;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 3 tests: conversion of an OpenAPI/config model to condensed TSON (with the mandatory
 * self-verification of the writer) and full round trips JSON -&gt; TSON -&gt; JSON.
 */
public class Phase3WriterTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static NPath examples(String relative) {
        File module = new File(".").getAbsoluteFile();
        File[] candidates = {
                new File(module, "../examples/" + relative),
                new File(module, "examples/" + relative),
                new File(module, "../../examples/" + relative)
        };
        for (File c : candidates) {
            if (c.exists()) {
                return NPath.of(c.getAbsolutePath());
            }
        }
        throw new IllegalStateException("examples dir not found from " + module);
    }

    private void roundTripJsonToTson(String sample) {
        NPath json = examples(sample);
        NElement model = NoApiConversion.readModel(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NoApiConversion.Result tson = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue(sample + " should condense: " + tson.note(), tson.condensed());

        NElement back = NoApiConversion.readModelString(tson.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        String diff = NoApiTreeComparator.diff(model, back, "$");
        org.junit.Assert.assertNull(sample + " round trip diff: " + diff, diff);
    }

    @Test
    public void sample01JsonToTsonToJson() {
        roundTripJsonToTson("openapi-json/sample-01/sample-api-01.json");
    }

    @Test
    public void sample02JsonToTsonToJson() {
        roundTripJsonToTson("openapi-json/sample-02/sample-api-02.json");
    }

    @Test
    public void plainTsonRoundTrips() {
        NPath json = examples("openapi-json/sample-01/sample-api-01.json");
        NElement model = NoApiConversion.readModel(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        // force plain
        NoApiConversion.Result plain = NoApiConversion.toTson(model, NoApiConversion.Kind.API, false);
        NElement back = NoApiConversion.readModelString(plain.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNullDiff(model, back);
    }

    @Test
    public void condensedTsonIsIdempotent() {
        NPath json = examples("openapi-json/sample-02/sample-api-02.json");
        NElement model = NoApiConversion.readModel(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        String tson1 = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true).text();
        NElement model2 = NoApiConversion.readModelString(tson1, NoApiFormat.TSON, NoApiConversion.Kind.API);
        String tson2 = NoApiConversion.toTson(model2, NoApiConversion.Kind.API, true).text();
        assertEquals(tson1, tson2);
    }

    @Test
    public void canonicalizerPushesPathLevelFieldsAndDropsEmpties() {
        String json = "{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\n"
                + "    \"/a\": {\"enabled\": \"${x}\", \"summary\": \"s\", \"get\": {\"responses\": {}}}\n"
                + "  },\n"
                + "  \"tags\": [],\n"
                + "  \"components\": {\"responses\": {}, \"parameters\": {}}\n"
                + "}";
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NElement paths = model.asObject().get().get("paths").get();
        NElement a = paths.asObject().get().get("/a").get();
        // path item should now only contain the get operation
        assertEquals(1, a.asObject().get().size());
        NElement get = a.asObject().get().get("get").get();
        assertEquals("${x}", Tson.asString(get.asObject().get().get("enabled").get()));
        assertEquals("s", Tson.asString(get.asObject().get().get("summary").get()));
        // empty tags and empty components.subobjects dropped
        assertTrue(!model.asObject().get().get("tags").isPresent());
        NElement comps = model.asObject().get().get("components").get();
        assertTrue(!comps.asObject().get().get("responses").isPresent());
        assertTrue(!comps.asObject().get().get("parameters").isPresent());
    }

    @Test
    public void securitySchemeIsNotTreatedAsParameter() {
        String json = "{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"components\": {\"securitySchemes\": {\"k\": {\"type\": \"apiKey\", \"name\": \"X\", \"in\": \"header\"}}}\n"
                + "}";
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NElement k = model.asObject().get().get("components").get().asObject().get()
                .get("securitySchemes").get().asObject().get().get("k").get();
        assertEquals("apiKey", Tson.asString(k.asObject().get().get("type").get()));
        assertTrue(!k.asObject().get().get("schema").isPresent());
    }

    @Test
    public void parametersStillMoveSchemaKeywords() {
        String json = "{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"get\": {\"parameters\": [{\"name\": \"q\", \"in\": \"query\", \"type\": \"string\", \"format\": \"date\"}], \"responses\": {}}}}\n"
                + "}";
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NElement p = model.asObject().get().get("paths").get().asObject().get().get("/a").get()
                .asObject().get().get("get").get().asObject().get().get("parameters").get()
                .asArray().get().children().get(0);
        NElement schema = p.asObject().get().get("schema").get();
        assertEquals("string", Tson.asString(schema.asObject().get().get("type").get()));
        assertEquals("date", Tson.asString(schema.asObject().get().get("format").get()));
        assertTrue(!p.asObject().get().get("type").isPresent());
    }

    private void assertNullDiff(NElement a, NElement b) {
        String diff = NoApiTreeComparator.diff(a, b, "$");
        org.junit.Assert.assertNull("round trip diff: " + diff, diff);
    }

    // ------------------------------------------------------------------ revision 2 : writer

    private static String write(String json) {
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NoApiConversion.Result r = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue("should condense: " + r.note(), r.condensed());
        return r.text();
    }

    @Test
    public void writerNeverEmitsCommas() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\", \"description\": \"d\"},\n"
                + "  \"paths\": {\"/a\": {\"post\": {\n"
                + "     \"summary\": \"s\",\n"
                + "     \"tags\": [\"x\", \"y\"],\n"
                + "     \"security\": [{\"A\": []}, {\"B\": [\"read\"]}],\n"
                + "     \"requestBody\": {\"description\": \"d\", \"required\": true, \"content\": {\"application/json\": {\"schema\": {\"type\": \"string\"}}}},\n"
                + "     \"responses\": {\"200\": {\"description\": \"ok\"}}\n"
                + "  }}},\n"
                + "  \"components\": {\"schemas\": {\"S\": {\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"string\"}}}}}\n"
                + "}");
        assertFalse("no comma expected in: " + tson, tson.contains(","));
    }

    @Test
    public void writerQuotesFreeTextAndKeepsNamesBare() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"My API\", \"version\": \"1.0.0\"},\n"
                + "  \"paths\": {},\n"
                + "  \"components\": {\"securitySchemes\": {\n"
                + "     \"ApiKeyAuth\": {\"type\": \"apiKey\", \"in\": \"header\", \"name\": \"X-API-KEY\"},\n"
                + "     \"JWTAuth\": {\"type\": \"http\", \"scheme\": \"bearer\", \"bearerFormat\": \"JWT\"}\n"
                + "  }}\n"
                + "}");
        assertTrue(tson, tson.contains("title: \"My API\""));
        assertTrue(tson, tson.contains("version: \"1.0.0\""));
        assertTrue(tson, tson.contains("ApiKeyAuth: apiKey(header: X-API-KEY)"));
        assertTrue(tson, tson.contains("JWTAuth: http(bearer bearerFormat: JWT)"));
        assertTrue(tson, tson.contains("bearerFormat: JWT"));
    }

    @Test
    public void writerEmitsAllGroupForAMultiKeyRequirement() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"security\": [{\"A\": [], \"B\": []}]\n"
                + "}");
        assertTrue(tson, tson.contains("all(A B)"));
    }

    @Test
    public void writerEmitsAllGroupInTheOperationAnnotation() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"get\": {\"security\": [{\"A\": [], \"B\": [\"read\"]}], \"responses\": {}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("@security(all(A B(read)))"));
    }

    @Test
    public void writerEmitsEnumAndTimestampSugar() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"components\": {\"schemas\": {\n"
                + "     \"E\": {\"type\": \"string\", \"enum\": [\"A\", \"B\"]},\n"
                + "     \"T\": {\"type\": \"string\", \"format\": \"date-time\"}\n"
                + "  }}\n"
                + "}");
        assertTrue(tson, tson.contains("enum(values: [A B])"));
        assertTrue(tson, tson.contains("timestamp"));
    }

    @Test
    public void writerKeepsShortDescriptionsInTheParentheses() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"components\": {\"schemas\": {\"S\": {\"type\": \"string\", \"description\": \"short\"}}}\n"
                + "}");
        assertTrue(tson, tson.contains("string(description: \"short\")"));
    }

    @Test
    public void writerEmitsLongDescriptionsInTheBody() {
        String longDescription = "0123456789012345678901234567890123456789012345678901234567890123";
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"components\": {\"schemas\": {\"S\": {\"type\": \"string\", \"description\": \"" + longDescription + "\"}}}\n"
                + "}");
        assertTrue(tson, tson.contains("string {"));
        assertTrue(tson, tson.contains("description: \"" + longDescription + "\""));
    }

    @Test
    public void writerEmitsTheCondensedRequestBodyWithTheInKey() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"post\": {\n"
                + "     \"requestBody\": {\"description\": \"d\", \"required\": true, \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/LoginModel\"}}}},\n"
                + "     \"responses\": {}\n"
                + "  }}},\n"
                + "  \"components\": {\"schemas\": {\"LoginModel\": {\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"string\"}}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("in: LoginModel(json description: \"d\")"));
        assertFalse(tson, tson.contains("requestBody:"));
    }

    @Test
    public void writerQuotesEveryStringValueOfAnExampleBlock() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"get\": {\"responses\": {\"200\": {\"description\": \"ok\", \"content\": {\"application/json\": {\"schema\": {\"type\": \"object\", \"properties\": {\"code\": {\"type\": \"string\"}, \"n\": {\"type\": \"number\"}, \"b\": {\"type\": \"boolean\"}}}, \"example\": {\"code\": \"E1\", \"n\": 1, \"b\": true, \"nil\": null}}}}}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("example: {"));
        assertTrue(tson, tson.contains("code: \"E1\""));
        assertTrue(tson, tson.contains("n: 1"));
        assertTrue(tson, tson.contains("b: true"));
        assertTrue(tson, tson.contains("nil: null"));
    }

    // ------------------------------------------------------------------ revision 2 : the document id

    @Test
    public void writerEmitsTheDocumentIdInTheInfoSection() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"custom\": {\"openapi-document-id\": \"my-api\", \"config\": {\"a\": 1}}\n"
                + "}");
        assertTrue(tson, tson.contains("id: my-api"));
        assertFalse("the document id is not repeated in the custom section: " + tson,
                tson.contains("openapi-document-id"));
    }

    @Test
    public void theDocumentIdSurvivesTheRoundTrip() {
        String json = "{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"custom\": {\"openapi-document-id\": \"my-api\", \"config\": {\"a\": 1}}\n"
                + "}";
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        String tson = write(json);
        NElement back = NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNullDiff(model, back);
        assertEquals("my-api", Tson.asString(back.asObject().get().get("custom").get()
                .asObject().get().get("openapi-document-id").get()));
    }

    // ------------------------------------------------------------------ revision 2 : no paragraph markers

    @Test
    public void writerNeverEmitsParagraphMarkers() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\", \"description\": \"line1\\nline2\"},\n"
                + "  \"paths\": {},\n"
                + "  \"components\": {\"schemas\": {\"S\": {\"type\": \"object\", \"description\": \"a\\nb\"}}}\n"
                + "}");
        assertFalse("no paragraph marker expected in: " + tson, tson.contains("¶"));
    }

    @Test
    public void writerKeepsAnEmptyResponsesObject() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"get\": {\"responses\": {}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("responses: {}"));
    }

    @Test
    public void writerOmitsAnEmptyCustomSection() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"custom\": {\"openapi-document-id\": \"my-api\"}\n"
                + "}");
        assertFalse("an empty custom section is not expected in: " + tson, tson.contains("custom {"));
    }

    @Test
    public void writerOmitsTheEmptyBodyOfARequestBody() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"post\": {\"requestBody\": {\"required\": true, \"content\": {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/S\"}}}}, \"responses\": {}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("@required in: S(json)"));
        assertFalse("an empty body is not expected in: " + tson, tson.contains("S(json) {"));
    }

    @Test
    public void writerEmitsTheAttributesOfAReference() {
        String json = "{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"components\": {\"schemas\": {\n"
                + "    \"Tokens\": {\"type\": \"object\", \"properties\": {\"accessToken\": {\"type\": \"string\"}}},\n"
                + "    \"Holder\": {\"type\": \"object\", \"properties\": {\n"
                + "      \"data\": {\"$ref\": \"#/components/schemas/Tokens\", \"description\": \"the tokens\"}\n"
                + "    }}\n"
                + "  }},\n"
                + "  \"paths\": {}\n"
                + "}";
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        String tson = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true).text();
        assertTrue(tson, tson.contains("data: Tokens(description: \"the tokens\")"));
        NElement back = NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNullDiff(model, back);
    }

    // ------------------------------------------------------------------ revision 4 : the single form

    @Test
    public void writerEmitsTheObjectDeclarationWithoutTheObjectKeyword() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"components\": {\"schemas\": {\"LoginRequest\": {\"type\": \"object\", \"description\": \"d\",\n"
                + "     \"properties\": {\"a\": {\"type\": \"string\"}}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("LoginRequest(description: \"d\") {"));
        assertFalse(tson, tson.contains("LoginRequest: object"));
    }

    @Test
    public void writerEmitsTheResponseComponentsAndTheirReferences() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"get\": {\"responses\": {\"500\": {\"$ref\": \"#/components/responses/InternalError\"}}}}},\n"
                + "  \"components\": {\"responses\": {\"InternalError\": {\"description\": \"boom\", \"content\":\n"
                + "     {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/ErrorResponse\"},\n"
                + "      \"example\": {\"error\": \"InternalError\"}}}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("responses {"));
        assertTrue(tson, tson.contains("InternalError: ErrorResponse(json description: \"boom\" example: { error: \"InternalError\" })"));
        assertTrue(tson, tson.contains("out(500): InternalError"));
    }

    @Test
    public void writerEmitsTheServerDescriptionInTheHead() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {},\n"
                + "  \"servers\": [{\"url\": \"https://x/api\", \"description\": \"d\"}]\n"
                + "}");
        assertTrue(tson, tson.contains("url(\"https://x/api\" description: \"d\")"));
    }

    @Test
    public void writerEmitsTheResponseAttributesInTheHead() {
        String tson = write("{\n"
                + "  \"openapi\": \"3.0.0\",\n"
                + "  \"info\": {\"title\": \"t\", \"version\": \"1\"},\n"
                + "  \"paths\": {\"/a\": {\"get\": {\"responses\": {\"200\": {\"description\": \"ok\", \"content\":\n"
                + "     {\"application/json\": {\"schema\": {\"$ref\": \"#/components/schemas/S\"},\n"
                + "      \"example\": {\"a\": \"b\"}}}}}}}},\n"
                + "  \"components\": {\"schemas\": {\"S\": {\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"string\"}}}}}\n"
                + "}");
        assertTrue(tson, tson.contains("out(200): S(json description: \"ok\" example: { a: \"b\" })"));
    }
}
