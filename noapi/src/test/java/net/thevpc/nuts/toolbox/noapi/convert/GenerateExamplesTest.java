package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.io.NPath;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

/**
 * Temporary generator used to (re)create the committed condensed {@code .tson} examples from the
 * JSON samples. Run explicitly, not part of the normal suite (kept because regenerating the
 * examples is occasionally needed and documents exactly how they are produced).
 */
public class GenerateExamplesTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static NPath examplesDir() {
        File module = new File(".").getAbsoluteFile();
        File[] candidates = {
                new File(module, "../examples"),
                new File(module, "examples"),
                new File(module, "../../examples")
        };
        for (File c : candidates) {
            if (c.exists()) {
                return NPath.of(c.getAbsolutePath());
            }
        }
        throw new IllegalStateException("examples dir not found from " + module);
    }

    @Test
    public void generate() {
        NPath examples = examplesDir();
        //noinspection ConstantConditions
        if (!Boolean.getBoolean("noapi.generate.examples")) {
            return;
        }
        for (String sample : new String[]{"sample-01/sample-api-01", "sample-02/sample-api-02"}) {
            NPath json = examples.resolve("openapi-json/" + sample + ".json");
            NPath out = examples.resolve("open-api-tson/" + sample + ".tson");
            NoapiConvertCommand.run(new NoapiConvertCommand.Options()
                    .setInput(json.toString())
                    .setOutput(out.toString())
                    .setTo(NoApiFormat.TSON)
                    .setForce(true));
            System.err.println("GENERATED " + out);
        }
    }

    @Test
    public void generatedExamplesRoundTrip() {
        NPath examples = examplesDir();
        for (String sample : new String[]{"sample-01/sample-api-01", "sample-02/sample-api-02"}) {
            NPath json = examples.resolve("openapi-json/" + sample + ".json");
            NPath tson = examples.resolve("open-api-tson/" + sample + ".tson");
            if (!tson.exists()) {
                continue;
            }
            NElement fromJson = NoApiConversion.readModel(json, NoApiFormat.JSON, NoApiConversion.Kind.API);
            NElement fromTson = NoApiConversion.readModel(tson, NoApiFormat.TSON, NoApiConversion.Kind.API);
            String diff = NoApiTreeComparator.diff(fromJson, fromTson, "$");
            org.junit.Assert.assertNull("diff: " + diff, diff);
        }
    }
}
