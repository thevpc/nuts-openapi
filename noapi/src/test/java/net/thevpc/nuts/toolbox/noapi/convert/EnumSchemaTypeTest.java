package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.lib.md.MdDocument;
import net.thevpc.nuts.lib.md.MdWriter;
import net.thevpc.nuts.lib.md.asciidoctor.AsciiDoctorWriter;
import net.thevpc.nuts.toolbox.noapi.model.DocItemInfo;
import net.thevpc.nuts.toolbox.noapi.model.MStoreModel;
import net.thevpc.nuts.toolbox.noapi.model.TypeInfo;
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
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Non-object schema types (notably enums) must be listed under SCHEMA TYPES and be visible in every
 * input format, exactly like object schemas.
 */
public class EnumSchemaTypeTest {

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

    private static String generatedAdoc(NPath source, NoApiFormat format) throws Exception {
        NElement element = NOpenAPIService.readApi(source, format);
        SwaggerStore store = new SwaggerStore();
        MStoreModel model = store.loadStoreModel(element);

        MStoreAndModel rmodel = new MStoreAndModel();
        rmodel.store = store;
        rmodel.model = model;
        rmodel.sourcePath = source;
        rmodel.sourceFolder = source.parent();
        rmodel.format = format;
        MFileInfo fileInfo = new MFileInfo(rmodel, "", new DocItemInfo());

        AppMessages msg = new AppMessages(null, MainMarkdownGenerator.class
                .getResource("/net/thevpc/nuts/toolbox/noapi/messages-en.json"));
        MdDocument document = new MainMarkdownGenerator(msg)
                .createMarkdown(fileInfo, source.parent(), new ArrayList<>());

        File adoc = Files.createTempFile("enum-schema-", ".adoc").toFile();
        try (MdWriter writer = new AsciiDoctorWriter(NPath.of(adoc.getAbsolutePath()))) {
            writer.write(document);
        }
        return new String(Files.readAllBytes(adoc.toPath()), "UTF-8");
    }

    @Test
    public void enumSchemaTypeIsReadFromEveryFormat() {
        NPath jsonPath = examples("enums/api.json");
        NPath yamlPath = examples("enums/api.yaml");
        NPath tsonPath = examples("enums/api.tson");

        NElement json = NoApiConversion.readModel(jsonPath, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NElement yaml = NoApiConversion.readModel(yamlPath, NoApiFormat.YAML, NoApiConversion.Kind.API);
        NElement tson = NoApiConversion.readModel(tsonPath, NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNull("json vs yaml: " + NoApiTreeComparator.diff(json, yaml, "$"),
                NoApiTreeComparator.diff(json, yaml, "$"));
        assertNull("json vs tson: " + NoApiTreeComparator.diff(json, tson, "$"),
                NoApiTreeComparator.diff(json, tson, "$"));

        TypeInfo color = new SwaggerStore().loadStoreModel(json).getTypesMap().get("Color");
        assertEquals("string", color.getType());
        assertEquals(Arrays.asList("RED", "GREEN", "BLUE", "YELLOW"), color.getEnumValues());
    }

    @Test
    public void enumSchemaTypeIsListedInGeneratedDocumentInEveryFormat() throws Exception {
        String[] files = {"api.json", "api.yaml", "api.tson"};
        NoApiFormat[] formats = {NoApiFormat.JSON, NoApiFormat.YAML, NoApiFormat.TSON};
        for (int i = 0; i < files.length; i++) {
            String text = generatedAdoc(examples("enums/" + files[i]), formats[i]);
            String file = files[i];
            assertTrue(file + ":\n" + text, text.contains("== SCHEMA TYPES"));
            assertTrue(file + ":\n" + text, text.contains("=== Color"));
            assertTrue(file + ":\n" + text, text.contains("The color options available."));
            assertTrue(file + ":\n" + text,
                    text.contains("enum ALLOWED {'RED', 'GREEN', 'BLUE', 'YELLOW'}"));
        }
    }

    @Test
    public void enumJsonConvertsToCondensedTson() {
        String json = "{ \"openapi\": \"3.0.3\", \"info\": { \"title\": \"t\", \"version\": \"1\" },"
                + " \"components\": { \"schemas\": { \"Color\": { \"type\": \"string\","
                + " \"description\": \"The color options available.\","
                + " \"enum\": [\"RED\", \"GREEN\", \"BLUE\", \"YELLOW\"] } } } }";
        NElement model = NoApiConversion.readModelString(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NoApiConversion.Result written = NoApiConversion.toTson(model, NoApiConversion.Kind.API, true);
        assertTrue("should condense: " + written.note(), written.condensed());
        assertTrue(written.text(), written.text().contains(
                "Color: enum(values: [RED GREEN BLUE YELLOW] description: \"The color options available.\")"));
        NElement back = NoApiConversion.readModelString(written.text(), NoApiFormat.TSON, NoApiConversion.Kind.API);
        assertNull(NoApiTreeComparator.diff(model, back, "$"));
    }
}
