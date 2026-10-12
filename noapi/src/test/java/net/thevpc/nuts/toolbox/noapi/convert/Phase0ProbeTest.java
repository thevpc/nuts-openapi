package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NArrayElement;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementAnnotation;
import net.thevpc.nuts.elem.NElementReader;
import net.thevpc.nuts.elem.NElementType;
import net.thevpc.nuts.elem.NObjectElement;
import net.thevpc.nuts.elem.NPairElement;
import net.thevpc.nuts.elem.NTupleElement;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 0 probe suite. Documents the behaviour of the real Nuts 1.1.0 TSON parser as observed
 * during recon (see PHASE0-NOTES.md). These tests do not exercise NoAPI code; they pin the
 * assumptions the condensed reader/writer rely on.
 */
public class Phase0ProbeTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static NElement tson(String s) {
        return NElementReader.ofTson().read(new StringReader(s));
    }

    @Test
    public void p1_responsePairHasNamedTupleKey() {
        NElement e = tson("out(200) : Foo(json) { description: \"x\" }");
        NPairElement p = e.asPair().get();
        NTupleElement key = p.key().asTuple().get();
        assertEquals("out", key.name().get());
        assertEquals(200, key.params().get(0).asIntValue().get().intValue());
        NObjectElement value = p.value().asObject().get();
        assertEquals("Foo", value.name().get());
        assertEquals("json", value.param(0).asStringValue().get());
        assertEquals("x", value.getStringValue("description").get());
    }

    @Test
    public void p2_annotationAttachesToPairKey() {
        NElement e = tson("@required name: string(description: \"d\")");
        NPairElement p = e.asPair().get();
        assertTrue(p.key().isAnnotated("required"));
        assertFalse(p.isAnnotated("required"));
        assertEquals("string", p.value().asTuple().get().name().get());
    }

    @Test
    public void p3_multipleAnnotationsPreservedInOrder() {
        NElement e = tson("@a @b(\"x\") name: T");
        NPairElement p = e.asPair().get();
        assertEquals(2, p.key().annotations().size());
        assertEquals("a", p.key().annotations().get(0).name());
        assertEquals("b", p.key().annotations().get(1).name());
    }

    @Test
    public void p4a_fullArrayParamsBeforeItems() {
        NElement e = tson("array(description: \"d\")[string]");
        assertEquals(NElementType.FULL_ARRAY, e.type());
        NArrayElement a = e.asArray().get();
        assertEquals("array", a.name().get());
        assertEquals("d", a.param("description").get().asStringValue().get());
        assertEquals("string", a.getAt(0).get().asStringValue().get());
    }

    @Test
    public void p4b_nestedArrays() {
        NElement e = tson("array[array[string]]");
        assertEquals(NElementType.NAMED_ARRAY, e.type());
        NArrayElement a = e.asArray().get();
        assertEquals("array", a.getAt(0).get().asArray().get().name().get());
    }

    @Test
    public void p4c_bracketsBeforeParamsYieldsFragment_notSupported() {
        // array[string](description: "d") is NOT a single node in Nuts 1.1.0; the condensed
        // reader must reject it and the writer must always emit the P4a order.
        NElement e = tson("array[string](description: \"d\")");
        assertEquals(NElementType.FRAGMENT, e.type());
    }

    @Test
    public void p8_separatePairsInsideNamedObject() {
        NElement e = tson("wrap { out(200): void\n out(400): ErrorType(json) { description: \"x\" } }");
        NObjectElement o = e.asObject().get();
        assertEquals(2, o.size());
        assertEquals("void", o.getAt(0).get().asPair().get().value().asStringValue().get());
        assertEquals("ErrorType", o.getAt(1).get().asPair().get().value().asObject().get().name().get());
    }

    @Test
    public void p11_namedAndPairFormsAreDistinctButUniformlyAccessible() {
        assertEquals(NElementType.NAMED_OBJECT, tson("k { a: 1 }").type());
        assertEquals(NElementType.PAIR, tson("k: { a: 1 }").type());
        assertEquals("k", tson("k { a: 1 }").asObject().get().name().get());
        assertEquals("k", tson("k: { a: 1 }").asPair().get().key().asStringValue().get());
    }

    @Test
    public void p10_openapiAnnotationDecoratesNextNode() {
        NElement e = tson("@openapi(\"3.0.0\")\ninfo { title: \"t\" }");
        NObjectElement info = e.asObject().get();
        assertEquals("info", info.name().get());
        assertTrue(info.isAnnotated("openapi"));
    }

    @Test
    public void rootMultiElementNormalizesToObject() {
        NElement e = tson("a: 1\nb: 2\nc: 3");
        assertEquals(NElementType.FRAGMENT, e.type());
        NObjectElement o = e.wrapIntoObject();
        assertEquals(3, o.size());
        assertEquals("a", o.getAt(0).get().asPair().get().key().asStringValue().get());
    }

    @Test
    public void nativeDateInsideObject() {
        NElement e = tson("{ date: 2024-07-16, version: \"1\" }");
        NElement date = e.asObject().get().get("date").get();
        assertTrue(date.isAnyDate() || date.type() == NElementType.LOCAL_DATE);
    }

    @Test
    public void multiLineBlockStringStripsCommonIndent() {
        NElement e = tson("wrap { description:\n  \u00b6\u00b6 a\n  \u00b6\u00b6 b\n  next: 1 }");
        NObjectElement o = e.asObject().get();
        assertEquals("a\nb", o.getStringValue("description").get());
        assertEquals(1, o.getIntValue("next").get().intValue());
    }

    @Test
    public void jsonLeniencyTrailingCommaAndComments() {
        assertEquals(2, NElementReader.ofJson().read(new StringReader("{ a: 1, b: 2, }")).asObject().get().size());
        assertEquals(1, NElementReader.ofJson().read(new StringReader("// c\n{ a: 1 /* m */ }")).asObject().get().size());
    }

    @Test
    public void unclosedObjectIsErrorTree() {
        NElement e = tson("{ a: 1");
        assertTrue(e.isErrorTree());
        assertFalse(e.diagnostics().isEmpty());
    }

    @Test
    public void annotationParamsAccessible() {
        NElement e = tson("@summary(\"a\") @description(\"b\")\nGET(\"/x\") { }");
        NObjectElement get = e.asObject().get();
        assertEquals("GET", get.name().get());
        assertEquals(2, get.annotations().size());
        NElementAnnotation summary = get.annotations().get(0);
        assertEquals("summary", summary.name());
        assertEquals("a", summary.param(0).get().asStringValue().get());
        assertEquals("/x", get.param(0).asStringValue().get());
    }

    @Test
    public void writerRoundTripsJsonToTsonToJson() {
        NElement original = NElementReader.ofJson().read(new StringReader("{\"a\":1,\"b\":[\"x\",\"y\"]}"));
        String tsonText = net.thevpc.nuts.elem.NElementWriter.ofTson().formatPlain(original);
        NElement reread = NElementReader.ofTson().read(new StringReader(tsonText));
        assertEquals(original.normalize(net.thevpc.nuts.text.NContentType.JSON).toPrettyString(),
                reread.normalize(net.thevpc.nuts.text.NContentType.JSON).toPrettyString());
        assertNotNull(tsonText);
    }
}
