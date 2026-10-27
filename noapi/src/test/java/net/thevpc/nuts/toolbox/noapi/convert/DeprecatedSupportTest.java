package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NObjectElement;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Support of the {@code deprecated} flag: the OpenAPI flag on operations and schema properties, the
 * condensed TSON {@code @deprecated} annotation, and the path-level form (pushed down to each
 * operation like the other path fields).
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
                "  }",
                "}");
        NElement model = NoApiConversion.readModelString(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
        NObjectElement old = model.asObject().get().getObject("components").get().getObject("schemas").get()
                .getObject("Old").get();
        assertEquals("true", old.get("deprecated").get().toString());
        assertNotNull(old.get("properties").get());
    }
}
