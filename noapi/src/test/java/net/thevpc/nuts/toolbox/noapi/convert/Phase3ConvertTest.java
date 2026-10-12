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
import static org.junit.Assert.fail;

/**
 * End to end test of the {@code noapi convert} command: JSON → TSON → JSON through the file
 * system, using the real condensed writer (self verified) and refusing to overwrite.
 */
public class Phase3ConvertTest {

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

    private static NPath tmpDir() {
        NPath dir = NPath.of("/tmp/opencode/noapi-convert-" + System.nanoTime());
        dir.mkdirs();
        return dir;
    }

    @Test
    public void jsonToTsonToJsonThroughFiles() {
        NPath dir = tmpDir();
        NPath jsonIn = examples("openapi-json/sample-02/sample-api-02.json");
        NPath tson = dir.resolve("api.tson");
        NPath jsonOut = dir.resolve("api.json");

        NoapiConvertCommand.Result r1 = NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(jsonIn.toString())
                .setOutput(tson.toString())
                .setTo(NoApiFormat.TSON));
        assertTrue("should condense", r1.condensed());
        assertTrue(tson.exists());

        NoapiConvertCommand.Result r2 = NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(tson.toString())
                .setOutput(jsonOut.toString())
                .setTo(NoApiFormat.JSON));
        assertTrue(jsonOut.exists());

        NElement m1 = NoApiConversion.readModel(jsonIn, NoApiFormat.JSON, NoApiConversion.Kind.API);
        NElement m2 = NoApiConversion.readModel(jsonOut, NoApiFormat.JSON, NoApiConversion.Kind.API);
        String diff = NoApiTreeComparator.diff(m1, m2, "$");
        org.junit.Assert.assertNull("diff: " + diff, diff);
    }

    @Test
    public void refusesToOverwriteWithoutForce() {
        NPath dir = tmpDir();
        NPath jsonIn = examples("openapi-json/sample-01/sample-api-01.json");
        NPath out = dir.resolve("api.tson");
        NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(jsonIn.toString()).setOutput(out.toString()).setTo(NoApiFormat.TSON));
        try {
            NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                    .setInput(jsonIn.toString()).setOutput(out.toString()).setTo(NoApiFormat.TSON));
            fail("expected overwrite refusal");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("--force"));
        }
        // with force it works
        NoapiConvertCommand.Result r = NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(jsonIn.toString()).setOutput(out.toString()).setTo(NoApiFormat.TSON).setForce(true));
        assertNotNull(r.output());
    }

    @Test
    public void defaultOutputNameReplacesExtension() {
        NPath dir = tmpDir();
        NPath jsonIn = examples("openapi-json/sample-01/sample-api-01.json");
        NPath copy = dir.resolve("my-api.json");
        jsonIn.copyTo(copy);
        NoapiConvertCommand.Result r = NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(copy.toString()).setTo(NoApiFormat.TSON));
        assertEquals("my-api.tson", r.output().name());
        assertTrue(r.output().exists());
    }

    @Test
    public void jsonOutputUsesRequestedFormat() {
        NPath dir = tmpDir();
        NPath jsonIn = examples("openapi-json/sample-01/sample-api-01.json");
        NPath out = dir.resolve("out.json");
        NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(jsonIn.toString()).setOutput(out.toString()).setTo(NoApiFormat.JSON));
        String text = out.readString();
        assertTrue(text.trim().startsWith("{"));
    }

    @Test
    public void outputExtensionDrivesFormat() {
        NPath dir = tmpDir();
        NPath jsonIn = examples("openapi-json/sample-01/sample-api-01.json");
        // TSON -> JSON by extension only (no explicit --to)
        NPath tson = dir.resolve("a.tson");
        NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(jsonIn.toString()).setOutput(tson.toString()));
        assertTrue(tson.readString().contains("@openapi"));
        NPath backJson = dir.resolve("b.json");
        NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                .setInput(tson.toString()).setOutput(backJson.toString()));
        assertTrue("expected JSON, got: " + backJson.readString().substring(0, 1),
                backJson.readString().trim().startsWith("{"));
    }
}
