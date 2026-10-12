package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.io.NPath;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
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
}
