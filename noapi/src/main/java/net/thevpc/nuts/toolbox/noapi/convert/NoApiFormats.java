package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementReader;
import net.thevpc.nuts.elem.NElementType;
import net.thevpc.nuts.elem.NElementWriter;
import net.thevpc.nuts.elem.NObjectElementBuilder;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.text.NContentType;
import net.thevpc.nuts.util.NIllegalArgumentException;
import net.thevpc.nuts.text.NMsg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;

/**
 * Reading and writing of NoAPI documents (JSON / YAML / TSON) through the Nuts {@code NElement}
 * API. All formats are read into a single {@link NElement} pivot; multi-element documents
 * (TSON fragments) are normalized into an object.
 */
public final class NoApiFormats {

    private NoApiFormats() {
    }

    public static NoApiFormat byExtension(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase();
        if (n.endsWith(".json")) {
            return NoApiFormat.JSON;
        }
        if (n.endsWith(".yaml") || n.endsWith(".yml")) {
            return NoApiFormat.YAML;
        }
        if (n.endsWith(".tson")) {
            return NoApiFormat.TSON;
        }
        return null;
    }

    /**
     * Detects the format using the extension first, then a content sniff. An explicit (non
     * {@link NoApiFormat#AUTO}) override always wins.
     */
    public static NoApiFormat detect(NPath path, NoApiFormat explicit) {
        if (explicit != null && explicit != NoApiFormat.AUTO) {
            return explicit;
        }
        NoApiFormat byExt = byExtension(path.name());
        if (byExt != null) {
            return byExt;
        }
        return sniff(path);
    }

    public static NoApiFormat sniff(NPath path) {
        String head = firstNonBlankChunk(path);
        if (head.startsWith("{") || head.startsWith("[")) {
            return NoApiFormat.JSON;
        }
        if (head.contains("\u00b6\u00b6") || head.startsWith("@")) {
            return NoApiFormat.TSON;
        }
        // YAML and TSON are both 'key: value' style; YAML is the safe default for unknown
        // extensions and matches the historical behaviour.
        return NoApiFormat.YAML;
    }

    private static String firstNonBlankChunk(NPath path) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(path.inputStream(), "UTF-8"))) {
            String t;
            int lines = 0;
            while ((t = r.readLine()) != null && lines < 20) {
                String tt = t.trim();
                if (tt.length() > 0 && !tt.startsWith("//") && !tt.startsWith("#")) {
                    if (sb.length() == 0) {
                        sb.append(tt);
                    } else {
                        sb.append("\n").append(tt);
                    }
                    lines++;
                    if (sb.length() > 256) {
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return sb.toString();
    }

    public static NElement read(NPath path, NoApiFormat format) {
        return normalize(readRaw(path, format));
    }

    public static NElement readString(String text, NoApiFormat format) {
        return normalize(readRawString(text, format));
    }

    private static NElement readRaw(NPath path, NoApiFormat format) {
        switch (format) {
            case JSON:
                return NElementReader.ofJson().read(path, NElement.class);
            case YAML:
                return NElementReader.ofYaml().read(path);
            case TSON:
                return NElementReader.ofTson().read(path);
            default:
                throw new NIllegalArgumentException(NMsg.ofC("cannot read format %s", format));
        }
    }

    private static NElement readRawString(String text, NoApiFormat format) {
        switch (format) {
            case JSON:
                return NElementReader.ofJson().read(new StringReader(text));
            case YAML:
                return NElementReader.ofYaml().read(new StringReader(text));
            case TSON:
                return NElementReader.ofTson().read(new StringReader(text));
            default:
                throw new NIllegalArgumentException(NMsg.ofC("cannot read format %s", format));
        }
    }

    /**
     * Normalizes a parsed TSON document: a multi-element top level becomes a fragment, which is
     * wrapped into a single object so it has the same shape as a JSON/YAML document.
     */
    public static NElement normalize(NElement e) {
        if (e == null) {
            return e;
        }
        if (e.type() == NElementType.FRAGMENT) {
            return e.wrapIntoObject();
        }
        // A top-level named container (`paths { … }`, `components { … }`, `security [ … ]`) is a
        // single document member, not the document itself. Wrap it so it has the same shape as a
        // JSON/YAML document (a plain object whose keys are the document members).
        if (e.isNamed() && !e.isPair() && (e.asObject().isPresent() || e.asArray().isPresent())) {
            NObjectElementBuilder b = NObjectElementBuilder.of();
            b.add(Tson.nameOf(e), Tson.unnamed(e));
            return b.build();
        }
        return e;
    }

    public static String write(NElement root, NoApiFormat format) {
        return write(root, format, false);
    }

    public static String write(NElement root, NoApiFormat format, boolean compact) {
        switch (format) {
            case JSON:
                return compact
                        ? NElementWriter.ofJson().compact(true).formatPlain(root)
                        : root.normalize(NContentType.JSON).toPrettyString();
            case YAML:
                return compact
                        ? NElementWriter.ofPlainYaml().compact(true).formatPlain(root)
                        : NElementWriter.ofYaml().formatPlain(root);
            case TSON:
                return compact
                        ? NElementWriter.ofPlainTson().compact(true).formatPlain(root)
                        : NElementWriter.ofPlainTson().formatPlain(root);
            default:
                throw new NIllegalArgumentException(NMsg.ofC("cannot write format %s", format));
        }
    }
}
