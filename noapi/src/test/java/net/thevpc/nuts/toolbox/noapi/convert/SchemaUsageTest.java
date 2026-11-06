package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.lib.md.MdDocument;
import net.thevpc.nuts.lib.md.MdWriter;
import net.thevpc.nuts.lib.md.asciidoctor.AsciiDoctorWriter;
import net.thevpc.nuts.toolbox.noapi.model.DocItemInfo;
import net.thevpc.nuts.toolbox.noapi.model.MStoreModel;
import net.thevpc.nuts.toolbox.noapi.service.MFileInfo;
import net.thevpc.nuts.toolbox.noapi.service.MStoreAndModel;
import net.thevpc.nuts.toolbox.noapi.service.docs.MainMarkdownGenerator;
import net.thevpc.nuts.toolbox.noapi.store.swagger.SwaggerStore;
import net.thevpc.nuts.toolbox.noapi.util.AppMessages;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Schema types must follow the enabled/disabled state of the operations that use them:
 * a type used only by disabled operations is hidden, a type used by an enabled operation is shown,
 * a type used by another shown schema is kept, and the "used in" list only mentions enabled
 * operations.
 */
public class SchemaUsageTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static final String JSON = String.join("\n",
            "{",
            "  \"openapi\": \"3.0.3\",",
            "  \"info\": { \"title\": \"t\", \"version\": \"1\" },",
            "  \"custom\": { \"openapi-document-id\": \"schema-usage\" },",
            "  \"paths\": {",
            "    \"/multi\": {",
            "      \"get\": { \"responses\": { \"200\": { \"description\": \"ok\", \"content\": { \"application/json\": {",
            "        \"schema\": { \"$ref\": \"#/components/schemas/Shared\" } } } } } },",
            "      \"post\": { \"enabled\": \"false\", \"responses\": { \"400\": { \"description\": \"no\", \"content\": { \"application/json\": {",
            "        \"schema\": { \"$ref\": \"#/components/schemas/Shared\" } } } } } }",
            "    },",
            "    \"/disabled\": { \"get\": { \"enabled\": \"false\", \"responses\": { \"200\": { \"description\": \"ok\", \"content\": { \"application/json\": {",
            "      \"schema\": { \"$ref\": \"#/components/schemas/DisabledOnly\" } } } } } } },",
            "    \"/parent\": { \"get\": { \"responses\": { \"200\": { \"description\": \"ok\", \"content\": { \"application/json\": {",
            "      \"schema\": { \"$ref\": \"#/components/schemas/Parent\" } } } } } } },",
            "    \"/enabled-parent\": { \"get\": { \"responses\": { \"200\": { \"description\": \"ok\", \"content\": { \"application/json\": {",
            "      \"schema\": { \"$ref\": \"#/components/schemas/EnabledParent\" } } } } } } },",
            "    \"/disabled-parent\": { \"get\": { \"enabled\": \"false\", \"responses\": { \"200\": { \"description\": \"ok\", \"content\": { \"application/json\": {",
            "      \"schema\": { \"$ref\": \"#/components/schemas/DisabledParent\" } } } } } } }",
            "  },",
            "  \"components\": { \"schemas\": {",
            "    \"Shared\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } },",
            "    \"DisabledOnly\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } },",
            "    \"Parent\": { \"type\": \"object\", \"properties\": { \"child\": { \"$ref\": \"#/components/schemas/Child\" } } },",
            "    \"Child\": { \"type\": \"object\", \"properties\": { \"leaf\": { \"$ref\": \"#/components/schemas/GrandChild\" } } },",
            "    \"GrandChild\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } },",
            "    \"EnabledParent\": { \"type\": \"object\", \"properties\": { \"shared\": { \"$ref\": \"#/components/schemas/SharedChild\" } } },",
            "    \"SharedChild\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } },",
            "    \"DisabledParent\": { \"type\": \"object\", \"properties\": { \"hidden\": { \"$ref\": \"#/components/schemas/HiddenChild\" } } },",
            "    \"HiddenChild\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } },",
            "    \"Unused\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } }",
            "  } }",
            "}");

    private static final String TSON = String.join("\n",
            "openapi: 3.0.3",
            "info { id: schema-usage title: t version: 1 }",
            "paths {",
            "  @enabled(\"false\")",
            "  GET(/disabled) { out(200): DisabledOnly }",
            "  GET(/parent) { out(200): Parent }",
            "  @enabled(\"false\")",
            "  GET(/disabled-parent) { out(200): DisabledParent }",
            "}",
            "components {",
            "  schemas {",
            "    DisabledOnly { x: string }",
            "    Parent { child: Child }",
            "    Child { leaf: GrandChild }",
            "    GrandChild { id: string }",
            "    DisabledParent { hidden: HiddenChild }",
            "    HiddenChild { id: string }",
            "    Unused { id: string }",
            "  }",
            "}");

    private static String render(String document, NoApiFormat format) throws Exception {
        NElement element = NoApiConversion.readModelString(document, format, NoApiConversion.Kind.API);
        SwaggerStore store = new SwaggerStore();
        MStoreModel model = store.loadStoreModel(element);

        File folder = Files.createTempDirectory("schema-usage-").toFile();
        MStoreAndModel rmodel = new MStoreAndModel();
        rmodel.store = store;
        rmodel.model = model;
        rmodel.sourceFolder = NPath.of(folder.getAbsolutePath());
        rmodel.format = format;
        MFileInfo fileInfo = new MFileInfo(rmodel, "", new DocItemInfo());

        AppMessages msg = new AppMessages(null, MainMarkdownGenerator.class
                .getResource("/net/thevpc/nuts/toolbox/noapi/messages-en.json"));
        MdDocument md = new MainMarkdownGenerator(msg)
                .createMarkdown(fileInfo, rmodel.sourceFolder, new ArrayList<>());

        File adoc = Files.createTempFile("schema-usage-", ".adoc").toFile();
        try (MdWriter writer = new AsciiDoctorWriter(NPath.of(adoc.getAbsolutePath()))) {
            writer.write(md);
        }
        return new String(Files.readAllBytes(adoc.toPath()), "UTF-8");
    }

    private static String section(String text, String title) {
        int start = text.indexOf(title);
        if (start < 0) {
            return "";
        }
        int next = text.indexOf("\n=== ", start + title.length());
        return next < 0 ? text.substring(start) : text.substring(start, next);
    }

    private static void assertShown(String text, String name) {
        assertTrue("missing schema " + name + ":\n" + text, text.contains("=== " + name));
    }

    private static void assertHidden(String text, String name) {
        assertFalse("unexpected schema " + name + ":\n" + text, text.contains("=== " + name));
    }

    @Test
    public void jsonSchemaVisibilityFollowsEnabledOperations() throws Exception {
        String text = render(JSON, NoApiFormat.JSON);

        assertShown(text, "Shared");
        assertShown(text, "Parent");
        assertShown(text, "Child");
        assertShown(text, "GrandChild");
        assertShown(text, "EnabledParent");
        assertShown(text, "SharedChild");
        assertShown(text, "Unused");

        assertHidden(text, "DisabledOnly");
        assertHidden(text, "DisabledParent");
        assertHidden(text, "HiddenChild");

        // Shared is used by an enabled GET (200) and a disabled POST (400): only the enabled one
        // is part of the "used in" list.
        String shared = section(text, "=== Shared");
        assertTrue(shared, shared.contains("`/multi`"));
        assertTrue(shared, shared.contains("Response (\"200\")"));
        assertFalse(shared, shared.contains("Response (\"400\")"));

        // the closure through Parent -> Child -> GrandChild keeps nested schemas visible
        assertTrue(section(text, "=== Parent"), section(text, "=== Parent").contains("`/parent`"));
    }

    @Test
    public void parameterSchemaVisibilityFollowsEnabledOperations() throws Exception {
        String json = "{ \"openapi\": \"3.0.3\", \"info\": { \"title\": \"t\", \"version\": \"1\" },"
                + " \"custom\": { \"openapi-document-id\": \"param-usage\" },"
                + " \"paths\": {"
                + "   \"/disabled-param\": { \"get\": { \"enabled\": \"false\", \"parameters\": ["
                + "     { \"name\": \"p\", \"in\": \"query\", \"schema\": { \"$ref\": \"#/components/schemas/ParamDisabled\" } }"
                + "   ], \"responses\": { \"200\": { \"description\": \"ok\" } } } },"
                + "   \"/enabled-param\": { \"get\": { \"parameters\": ["
                + "     { \"name\": \"p\", \"in\": \"query\", \"schema\": { \"$ref\": \"#/components/schemas/ParamEnabled\" } }"
                + "   ], \"responses\": { \"200\": { \"description\": \"ok\" } } } }"
                + " },"
                + " \"components\": { \"schemas\": {"
                + "   \"ParamDisabled\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } },"
                + "   \"ParamEnabled\": { \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } }"
                + " } } }";
        String text = render(json, NoApiFormat.JSON);
        assertShown(text, "ParamEnabled");
        assertHidden(text, "ParamDisabled");
        String section = section(text, "=== ParamEnabled");
        assertTrue(section, section.contains("`/enabled-param`"));
        assertTrue(section, section.contains("Query Parameter"));
    }

    @Test
    public void tsonSchemaVisibilityUsesTheSameRules() throws Exception {
        String text = render(TSON, NoApiFormat.TSON);

        assertShown(text, "Parent");
        assertShown(text, "Child");
        assertShown(text, "GrandChild");
        assertShown(text, "Unused");

        assertHidden(text, "DisabledOnly");
        assertHidden(text, "DisabledParent");
        assertHidden(text, "HiddenChild");
    }
}
