package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NArrayElement;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NObjectElement;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.lib.md.MdDocument;
import net.thevpc.nuts.lib.md.MdWriter;
import net.thevpc.nuts.lib.md.asciidoctor.AsciiDoctorWriter;
import net.thevpc.nuts.toolbox.noapi.model.DocItemInfo;
import net.thevpc.nuts.toolbox.noapi.model.MStoreModel;
import net.thevpc.nuts.toolbox.noapi.service.MFileInfo;
import net.thevpc.nuts.toolbox.noapi.service.MStoreAndModel;
import net.thevpc.nuts.toolbox.noapi.service.NOpenAPIService;
import net.thevpc.nuts.toolbox.noapi.service.docs.MainMarkdownGenerator;
import net.thevpc.nuts.toolbox.noapi.store.swagger.SwaggerStore;
import net.thevpc.nuts.toolbox.noapi.util.AppMessages;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Support of the {@code deprecated} flag in every input format: the OpenAPI flag on operations,
 * parameters, headers, schemas and properties, the condensed TSON {@code @deprecated} annotation,
 * the path-level form (pushed down to each operation), the JSON/YAML/TSON round trips and the
 * explicit marker written in the generated document/PDF.
 */
public class DeprecatedSupportTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static final String TSON = String.join("\n",
            "openapi: 3.0.3",
            "info { title: t version: 1 }",
            "paths {",
            "  @deprecated",
            "  GET(/legacy) {",
            "    out(200): string(description: \"ok\")",
            "  }",
            "  @deprecated(false)",
            "  POST(/old) {",
            "    out(200): string(description: \"ok\")",
            "  }",
            "}",
            "components {",
            "  schemas {",
            "    LoginRequest {",
            "      @required @deprecated password: string",
            "      login: string",
            "    }",
            "  }",
            "}",
            "");

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

    private static boolean deprecated(NElement object) {
        NElement d = object.asObject().get().get("deprecated").orNull();
        return d != null && d.asBooleanValue().orElse(false);
    }

    @Test
    public void condensedDeprecatedRoundTrips() {
        NElement model = NoApiConversion.readModelString(TSON, NoApiFormat.TSON, NoApiConversion.Kind.API);
        NObjectElement get = model.asObject().get().getObject("paths").get()
                .getObject("/legacy").get().getObject("get").get();
        assertEquals("true", get.get("deprecated").get().toString());
        NObjectElement post = model.asObject().get().getObject("paths").get().getObject("/old").get().getObject("post").get();
        assertEquals("false", post.get("deprecated").get().toString());
        NObjectElement password = model.asObject().get().getObject("components").get().getObject("schemas").get()
                .getObject("LoginRequest").get().getObject("properties").get().getObject("password").get();
        assertEquals("true", password.get("deprecated").get().toString());

        // the written TSON keeps the annotation form and is idempotent
        NoApiConversion.Result tson = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue("should condense: " + tson.note(), tson.condensed());
        assertTrue(tson.text(), tson.text().contains("@deprecated"));
        assertTrue(tson.text(), tson.text().contains("@deprecated(false)"));
        NElement back = NoApiConversion.readModelString(tson.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNull(NoApiTreeComparator.diff(model, back, "$"));
    }

    @Test
    public void jsonDeprecatedRoundTripsThroughTson() {
        String json = "{ \"openapi\": \"3.0.3\", \"info\": { \"title\": \"t\", \"version\": \"1\" },"
                + " \"paths\": { \"/a\": { \"get\": { \"deprecated\": true, \"responses\": { \"200\": { \"description\": \"ok\" } } } } },"
                + " \"components\": { \"schemas\": { \"T\": { \"type\": \"object\","
                + " \"properties\": { \"f\": { \"type\": \"string\", \"deprecated\": true } } } } } }";
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NoApiConversion.Result tson = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue("should condense: " + tson.note(), tson.condensed());
        NElement back = NoApiConversion.readModelString(tson.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNull(NoApiTreeComparator.diff(model, back, "$"));
    }

    @Test
    public void pathLevelDeprecatedIsPushedIntoEachOperation() {
        String tson = String.join("\n",
                "openapi: 3.0.3",
                "info { title: t version: 1 }",
                "paths {",
                "  \"/shared\": {",
                "    deprecated: true",
                "    get { out(200): string(description: \"ok\") }",
                "    post { out(200): string(description: \"ok\") }",
                "  }",
                "}");
        NElement model = NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
        NObjectElement paths = model.asObject().get().getObject("paths").get().getObject("/shared").get();
        assertNull("the path item must not keep the flag", paths.get("deprecated").orNull());
        assertEquals("true", paths.getObject("get").get().get("deprecated").get().toString());
        assertEquals("true", paths.getObject("post").get().get("deprecated").get().toString());
    }

    @Test
    public void pathLevelDeprecatedAnnotationIsPushedIntoEachOperation() {
        String tson = String.join("\n",
                "openapi: 3.0.3",
                "info { title: t version: 1 }",
                "paths {",
                "  @deprecated",
                "  \"/shared\": {",
                "    get { out(200): string(description: \"ok\") }",
                "    post { out(200): string(description: \"ok\") }",
                "  }",
                "}");
        NElement model = NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
        NObjectElement paths = model.asObject().get().getObject("paths").get().getObject("/shared").get();
        assertNull("the path item must not keep the flag", paths.get("deprecated").orNull());
        assertEquals("true", paths.getObject("get").get().get("deprecated").get().toString());
        assertEquals("true", paths.getObject("post").get().get("deprecated").get().toString());
        // the path argument of a condensed operation inside a path item is optional
        assertNotNull(paths.getObject("get").get().get("responses").get());
    }

    @Test
    public void deprecatedIsAcceptedOnSchemaDeclarations() {
        String tson = String.join("\n",
                "openapi: 3.0.3",
                "info { title: t version: 1 }",
                "paths { GET(/a) { out(200): string(description: \"ok\") } }",
                "components {",
                "  schemas {",
                "    @deprecated",
                "    Old(object) {",
                "      x: string",
                "    }",
                "    @deprecated(false)",
                "    New(object) {",
                "      y: string",
                "    }",
                "    Holder {",
                "      @deprecated(false) current: string",
                "    }",
                "  }",
                "}");
        NElement model = NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
        NObjectElement old = model.asObject().get().getObject("components").get().getObject("schemas").get()
                .getObject("Old").get();
        assertEquals("true", old.get("deprecated").get().toString());
        assertNotNull(old.get("properties").get());
        NObjectElement newType = model.asObject().get().getObject("components").get().getObject("schemas").get()
                .getObject("New").get();
        assertEquals("false", newType.get("deprecated").get().toString());
        NObjectElement current = model.asObject().get().getObject("components").get().getObject("schemas").get()
                .getObject("Holder").get().getObject("properties").get().getObject("current").get();
        assertEquals("false", current.get("deprecated").get().toString());

        NoApiConversion.Result written = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue("should condense: " + written.note(), written.condensed());
        assertTrue(written.text(), written.text().contains("@deprecated Old"));
        assertTrue(written.text(), written.text().contains("@deprecated(false) New"));
        assertTrue(written.text(), written.text().contains("@deprecated(false) current"));
        NElement back = NoApiConversion.readModelString(written.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNull(NoApiTreeComparator.diff(model, back, "$"));
    }

    @Test
    public void deprecatedAnnotationsWorkOnParametersAndHeaders() {
        String tson = String.join("\n",
                "openapi: 3.0.3",
                "info { title: t version: 1 }",
                "paths {",
                "  GET(/a) {",
                "    @query @deprecated old: string",
                "    @query @deprecated(false) current: string",
                "    out(200): string(description: \"ok\")",
                "  }",
                "}",
                "components {",
                "  headers {",
                "    @deprecated X-Old: string()",
                "    @deprecated(false) X-Current: string()",
                "  }",
                "}");
        NElement model = NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
        NArrayElement params = model.asObject().get().getObject("paths").get().getObject("/a").get()
                .getObject("get").get().getArray("parameters").get();
        assertEquals("true", params.get(0).get().asObject().get().get("deprecated").get().toString());
        assertEquals("false", params.get(1).get().asObject().get().get("deprecated").get().toString());
        NObjectElement headers = model.asObject().get().getObject("components").get().getObject("headers").get();
        assertEquals("true", headers.getObject("X-Old").get().get("deprecated").get().toString());
        assertEquals("false", headers.getObject("X-Current").get().get("deprecated").get().toString());

        NoApiConversion.Result written = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue("should condense: " + written.note(), written.condensed());
        assertTrue(written.text(), written.text().contains("@deprecated X-Old"));
        assertTrue(written.text(), written.text().contains("@deprecated(false) X-Current"));
        NElement back = NoApiConversion.readModelString(written.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNull(NoApiTreeComparator.diff(model, back, "$"));
    }

    @Test
    public void jsonYamlAndTsonExamplesCarryTheSameDeprecation() {
        NPath jsonPath = examples("deprecated/api.json");
        NPath yamlPath = examples("deprecated/api.yaml");
        NPath tsonPath = examples("deprecated/api.tson");

        NElement json = NoApiConversion.readModel(jsonPath, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NElement yaml = NoApiConversion.readModel(yamlPath, NoApiFormat.YAML, NoApiConversion.Kind.API);
        NElement tson = NoApiConversion.readModel(tsonPath, NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNull("json vs yaml: " + NoApiTreeComparator.diff(json, yaml, "$"),
                NoApiTreeComparator.diff(json, yaml, "$"));
        assertNull("json vs tson: " + NoApiTreeComparator.diff(json, tson, "$"),
                NoApiTreeComparator.diff(json, tson, "$"));

        NObjectElement root = json.asObject().get();
        assertTrue(deprecated(root.getObjectByPath("paths", "/users/{id}", "delete").get()));
        assertTrue(deprecated(root.getObjectByPath("paths", "/legacy/status", "get").get()));
        assertTrue(deprecated(root.getObjectByPath("paths", "/legacy/status", "post").get()));
        assertTrue(deprecated(root.getByPath("paths", "/users/{id}", "get", "parameters").get()
                .asArray().get().get(1).get()));
        assertTrue(deprecated(root.getObjectByPath("components", "schemas", "LegacyUser").get()));
        assertTrue(deprecated(root.getObjectByPath("components", "schemas", "User", "properties", "oldEmail").get()));
        assertTrue(deprecated(root.getObjectByPath("components", "headers", "X-Legacy-Token").get()));

        // the conversion reads the flag in each format and writes it back in that format
        String jsonText = NoApiConversion.convert(json, NoApiConversion.Kind.API, NoApiFormat.JSON, false).text();
        String yamlText = NoApiConversion.convert(json, NoApiConversion.Kind.API, NoApiFormat.YAML, false).text();
        NoApiConversion.Result tsonResult = NoApiConversion.toTson(json, NoApiConversion.Kind.API, true);
        assertTrue("should condense: " + tsonResult.note(), tsonResult.condensed());
        assertTrue(jsonText, jsonText.contains("\"deprecated\": true"));
        assertTrue(yamlText, yamlText.contains("deprecated: true"));
        assertTrue(tsonResult.text(), tsonResult.text().contains("@deprecated"));
    }

    @Test
    public void generatedDocumentShowsDeprecatedExplicitlyInEveryFormat() throws Exception {
        String[] files = {"api.json", "api.yaml", "api.tson"};
        NoApiFormat[] formats = {NoApiFormat.JSON, NoApiFormat.YAML, NoApiFormat.TSON};
        for (int i = 0; i < files.length; i++) {
            NPath source = examples("deprecated/" + files[i]);
            NElement element = NOpenAPIService.readApi(source, formats[i]);
            SwaggerStore store = new SwaggerStore();
            MStoreModel model = store.loadStoreModel(element);

            MStoreAndModel rmodel = new MStoreAndModel();
            rmodel.store = store;
            rmodel.model = model;
            rmodel.sourcePath = source;
            rmodel.sourceFolder = source.parent();
            rmodel.format = formats[i];
            MFileInfo fileInfo = new MFileInfo(rmodel, "", new DocItemInfo());

            AppMessages msg = new AppMessages(null, MainMarkdownGenerator.class
                    .getResource("/net/thevpc/nuts/toolbox/noapi/messages-en.json"));
            MdDocument document = new MainMarkdownGenerator(msg)
                    .createMarkdown(fileInfo, source.parent(), new ArrayList<>());

            File adoc = Files.createTempFile("deprecated-document-", ".adoc").toFile();
            try (MdWriter writer = new AsciiDoctorWriter(NPath.of(adoc.getAbsolutePath()))) {
                writer.write(document);
            }
            String text = new String(Files.readAllBytes(adoc.toPath()), "UTF-8");

            // These are exactly the lines rendered by the AsciiDoctor PDF backend.
            String file = files[i];
            assertTrue(file + ":\n" + text, text.contains("DELETE /users/{id} [DEPRECATED]"));
            assertTrue(file + ":\n" + text, text.contains("GET /legacy/status [DEPRECATED]"));
            assertTrue(file + ":\n" + text, text.contains("POST /legacy/status [DEPRECATED]"));
            assertTrue(file + ":\n" + text, text.contains("legacy [DEPRECATED]"));
            assertTrue(file + ":\n" + text, text.contains("X-Legacy-Token [DEPRECATED]"));
            assertTrue(file + ":\n" + text, text.contains("LegacyUser [DEPRECATED]"));
            assertTrue(file + ":\n" + text, text.contains("oldEmail [DEPRECATED]"));
        }
    }
}
