package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.io.NPath;

/**
 * Implementation of the {@code noapi convert} command: reads a document (JSON / YAML / TSON,
 * condensed or plain) into the OpenAPI model and writes it in another format. TSON output is
 * condensed and self-verified by default.
 *
 * <p>The class is intentionally independent from the CLI plumbing so it can be tested directly.</p>
 */
public final class NoapiConvertCommand {

    private NoapiConvertCommand() {
    }

    public static class Options {
        private String input;
        private String output;
        private NoApiFormat from = NoApiFormat.AUTO;
        private NoApiFormat to = NoApiFormat.AUTO;
        private NoApiFileKind kind = NoApiFileKind.AUTO;
        private boolean condensed = true;
        private boolean force = false;

        public String getInput() {
            return input;
        }

        public Options setInput(String input) {
            this.input = input;
            return this;
        }

        public String getOutput() {
            return output;
        }

        public Options setOutput(String output) {
            this.output = output;
            return this;
        }

        public NoApiFormat getFrom() {
            return from;
        }

        public Options setFrom(NoApiFormat from) {
            this.from = from;
            return this;
        }

        public NoApiFormat getTo() {
            return to;
        }

        public Options setTo(NoApiFormat to) {
            this.to = to;
            return this;
        }

        public NoApiFileKind getKind() {
            return kind;
        }

        public Options setKind(NoApiFileKind kind) {
            this.kind = kind;
            return this;
        }

        public boolean isCondensed() {
            return condensed;
        }

        public Options setCondensed(boolean condensed) {
            this.condensed = condensed;
            return this;
        }

        public boolean isForce() {
            return force;
        }

        public Options setForce(boolean force) {
            this.force = force;
            return this;
        }
    }

    public static class Result {
        private final NPath output;
        private final boolean condensed;
        private final String note;

        Result(NPath output, boolean condensed, String note) {
            this.output = output;
            this.condensed = condensed;
            this.note = note;
        }

        public NPath output() {
            return output;
        }

        public boolean condensed() {
            return condensed;
        }

        public String note() {
            return note;
        }
    }

    public static Result run(Options o) {
        NPath input = NPath.of(o.input);
        if (!input.exists()) {
            throw new IllegalArgumentException("input not found: " + o.input);
        }
        NoApiFormat from = NoApiFormats.detect(input, o.from);
        NElement raw = NoApiFormats.read(input, from);
        NoApiFileKind kind = o.kind;
        if (kind == null || kind == NoApiFileKind.AUTO) {
            kind = NoApiFileKind.detect(input.name(), raw);
            if (kind == NoApiFileKind.AUTO) {
                kind = NoApiFileKind.API;
            }
        }
        NoApiConversion.Kind convKind = kind == NoApiFileKind.CONFIG
                ? NoApiConversion.Kind.CONFIG
                : NoApiConversion.Kind.API;

        NElement model;
        if (from == NoApiFormat.TSON) {
            NoApiDiagnostics diagnostics = new NoApiDiagnostics(input.name());
            model = convKind == NoApiConversion.Kind.CONFIG
                    ? new CondensedTsonDesugarer(diagnostics).desugarConfig(raw)
                    : new CondensedTsonDesugarer(diagnostics).desugar(raw);
        } else {
            model = raw;
        }
        model = NoApiCanonicalizer.canonicalize(model);

        // target format: explicit --to, else inferred from the output extension, else TSON
        NPath output = o.output != null && !o.output.isEmpty() ? NPath.of(o.output) : null;
        NoApiFormat to = o.to;
        if (to == null || to == NoApiFormat.AUTO) {
            NoApiFormat byExt = output == null ? null : NoApiFormats.byExtension(output.name());
            to = byExt != null ? byExt : NoApiFormat.TSON;
        }

        NoApiConversion.Result converted = NoApiConversion.convert(model, convKind, to, o.condensed);

        if (output == null) {
            output = defaultOutput(input, to);
        }
        if (output.exists() && !o.force) {
            throw new IllegalStateException("output already exists (use --force to overwrite): " + output);
        }
        output.mkParentDirs();
        output.writeString(converted.text());

        return new Result(output, converted.condensed(), converted.note());
    }

    private static NPath defaultOutput(NPath input, NoApiFormat to) {
        String name = input.name();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = to == NoApiFormat.JSON ? ".json" : to == NoApiFormat.YAML ? ".yaml" : ".tson";
        return input.resolveSibling(base + ext);
    }
}
