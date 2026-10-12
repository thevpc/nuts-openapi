package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementType;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.MStoreModel;
import net.thevpc.nuts.toolbox.noapi.store.swagger.SwaggerStore;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 1 tests: format detection, reading JSON/YAML/plain TSON into a single {@link NElement}
 * pivot, fragment normalization and the hand-off into the existing {@link SwaggerStore}.
 */
public class Phase1FormatsTest {

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

    @Test
    public void extensionDetection() {
        assertEquals(NoApiFormat.JSON, NoApiFormats.byExtension("a.json"));
        assertEquals(NoApiFormat.YAML, NoApiFormats.byExtension("a.yaml"));
        assertEquals(NoApiFormat.YAML, NoApiFormats.byExtension("a.yml"));
        assertEquals(NoApiFormat.TSON, NoApiFormats.byExtension("a.tson"));
        assertNull(NoApiFormats.byExtension("a.txt"));
        assertEquals(NoApiFormat.JSON, NoApiFormats.byExtension("a.config.json"));
    }

    @Test
    public void explicitOverrideWins() {
        NPath p = NPath.of("/tmp/whatever.unknown");
        assertEquals(NoApiFormat.TSON, NoApiFormats.detect(p, NoApiFormat.TSON));
    }

    @Test
    public void readJsonAndTsonProduceTheSameTree() {
        NPath json = examples("openapi-json/sample-01/sample-api-01.json");
        NElement fromJson = NoApiFormats.read(json, NoApiFormat.JSON);

        String tsonText = NoApiFormats.write(fromJson, NoApiFormat.TSON);
        NElement fromTson = NoApiFormats.readString(tsonText, NoApiFormat.TSON);

        assertEquals(fromJson.normalize(net.thevpc.nuts.text.NContentType.JSON).toPrettyString(),
                fromTson.normalize(net.thevpc.nuts.text.NContentType.JSON).toPrettyString());
    }

    @Test
    public void plainTsonHandsOffToSwaggerStore() {
        NPath json = examples("openapi-json/sample-01/sample-api-01.json");
        NElement fromJson = NoApiFormats.read(json, NoApiFormat.JSON);
        MStoreModel modelFromJson = new SwaggerStore().loadStoreModel(fromJson);

        String tsonText = NoApiFormats.write(fromJson, NoApiFormat.TSON);
        NElement fromTson = NoApiFormats.readString(tsonText, NoApiFormat.TSON);
        MStoreModel modelFromTson = new SwaggerStore().loadStoreModel(fromTson);

        assertEquals(modelFromJson.getTitle(), modelFromTson.getTitle());
        assertEquals(modelFromJson.getId(), modelFromTson.getId());
        assertEquals(modelFromJson.getPaths().size(), modelFromTson.getPaths().size());
        assertTrue(modelFromTson.getPaths().size() > 0);
    }

    @Test
    public void jsonFragmentNormalization() {
        // A TSON document whose top level is several pairs parses to a FRAGMENT.
        NElement e = NoApiFormats.readString("a: 1\nb: 2", NoApiFormat.TSON);
        assertEquals(NElementType.OBJECT, e.type());
        assertEquals(2, e.asObject().get().size());
    }

    @Test
    public void sample02IsReadableAndHasPaths() {
        NPath json = examples("openapi-json/sample-02/sample-api-02.json");
        NElement fromJson = NoApiFormats.read(json, NoApiFormat.JSON);
        MStoreModel model = new SwaggerStore().loadStoreModel(fromJson);
        assertNotNull(model.getTitle());
        assertTrue(model.getPaths().size() > 0);
    }

    @Test
    public void configDetection() {
        NElement api = NoApiFormats.readString("openapi: \"3.0.0\"\ninfo { title: t }", NoApiFormat.TSON);
        assertEquals(NoApiFileKind.API, NoApiFileKind.detect("x.tson", api));
        NElement config = NoApiFormats.readString("openapi-config: \"3.0.0\"\ntarget-name: \"T\"", NoApiFormat.TSON);
        assertEquals(NoApiFileKind.CONFIG, NoApiFileKind.detect("x.tson", config));
        assertEquals(NoApiFileKind.CONFIG, NoApiFileKind.detect("x.config.json", null));
    }
}
