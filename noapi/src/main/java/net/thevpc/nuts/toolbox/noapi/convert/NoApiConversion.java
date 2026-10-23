package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.NElement;

/**
 * High level conversion of an OpenAPI/config {@link NElement} model between formats, including the
 * mandatory self-verification of the condensed TSON writer (prompt 7.4): the freshly written TSON
 * is parsed back, desugared and canonicalized, then compared to the canonicalized source. On any
 * mismatch (or writer error) the converter falls back to plain (non condensed) TSON, which is
 * lossless, so an unverified condensed text is never emitted.
 */
public final class NoApiConversion {

    private NoApiConversion() {
    }

    public static class Result {
        private final String text;
        private final boolean condensed;
        private final String note;

        Result(String text, boolean condensed, String note) {
            this.text = text;
            this.condensed = condensed;
            this.note = note;
        }

        public String text() {
            return text;
        }

        public boolean condensed() {
            return condensed;
        }

        public String note() {
            return note;
        }
    }

    public enum Kind {
        API,
        CONFIG
    }

    /**
     * Converts a model to TSON text. When {@code condensedPreferred} the condensed writer is tried
     * first and self-verified; on failure the plain (lossless) writer is used.
     */
    public static Result toTson(NElement model, Kind kind, boolean condensedPreferred) {
        NElement canon = NoApiCanonicalizer.canonicalize(model);
        if (condensedPreferred) {
            try {
                String text = kind == Kind.CONFIG
                        ? CondensedTsonWriter.writeConfig(canon)
                        : CondensedTsonWriter.writeDocument(canon);
                NElement parsed = NoApiFormats.readString(text, NoApiFormat.TSON);
                NoApiDiagnostics diagnostics = new NoApiDiagnostics("self-verify");
                NElement desugared = kind == Kind.CONFIG
                        ? new CondensedTsonDesugarer(diagnostics).desugarConfig(parsed)
                        : new CondensedTsonDesugarer(diagnostics).desugar(parsed);
                NElement canon2 = NoApiCanonicalizer.canonicalize(desugared);
                String diff = NoApiTreeComparator.diff(canon, canon2, "$");
                if (diff == null) {
                    return new Result(text, true, null);
                }
                return new Result(NoApiFormats.write(canon, NoApiFormat.TSON, false), false,
                        "condensed self-verification failed (" + diff + "); fell back to plain TSON");
            } catch (RuntimeException ex) {
                return new Result(NoApiFormats.write(canon, NoApiFormat.TSON, false), false,
                        "condensed writer failed (" + ex + "); fell back to plain TSON");
            }
        }
        return new Result(NoApiFormats.write(canon, NoApiFormat.TSON, false), false, null);
    }

    /**
     * Converts a model to the given output format text. TSON output honours the condensed
     * preference and self-verification; JSON/YAML use the Nuts writers on the canonical model.
     */
    public static Result convert(NElement model, Kind kind, NoApiFormat target, boolean condensedPreferred) {
        if (target == NoApiFormat.TSON) {
            return toTson(model, kind, condensedPreferred);
        }
        NElement canon = NoApiCanonicalizer.canonicalize(model);
        return new Result(NoApiFormats.write(canon, target, false), false, null);
    }

    /**
     * Reads a document (desugaring TSON input), canonicalizes it and returns the model. This is the
     * single entry point used by the {@code convert} command and the round-trip tests.
     */
    public static NElement readModel(net.thevpc.nuts.io.NPath path, NoApiFormat format, Kind kind) {
        NElement element = NoApiFormats.read(path, format);
        if (format == NoApiFormat.TSON) {
            NoApiDiagnostics diagnostics = new NoApiDiagnostics(path.name());
            element = kind == Kind.CONFIG
                    ? new CondensedTsonDesugarer(diagnostics).desugarConfig(element)
                    : new CondensedTsonDesugarer(diagnostics).desugar(element);
        }
        NElement model = NoApiCanonicalizer.canonicalize(element);
        validateExamples(model, path.name());
        return model;
    }

    public static NElement readModelString(String text, NoApiFormat format, Kind kind) {
        NElement element = NoApiFormats.readString(text, format);
        if (format == NoApiFormat.TSON) {
            NoApiDiagnostics diagnostics = new NoApiDiagnostics("string");
            element = kind == Kind.CONFIG
                    ? new CondensedTsonDesugarer(diagnostics).desugarConfig(element)
                    : new CondensedTsonDesugarer(diagnostics).desugar(element);
        }
        NElement model = NoApiCanonicalizer.canonicalize(element);
        validateExamples(model, "string");
        return model;
    }

    /**
     * Checks that the examples of the document follow the constraints of their schema. The
     * violations are reported as {@code E_EXAMPLE_MISMATCH} diagnostics.
     */
    public static void validateExamples(NElement model, String file) {
        NoApiDiagnostics diagnostics = new NoApiDiagnostics(file);
        SchemaExampleValidator.validateDocument(model, diagnostics);
        diagnostics.throwIfErrors();
    }
}
