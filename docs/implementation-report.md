# Implementation report (Section 10)

This report summarises the deviations, decisions and known gaps of the JSON / YAML / condensed-TSON
input work and the bidirectional converter. It answers section 10 of the task specification.

## 1. Phase 0 answers and probe outcomes

See [../PHASE0-NOTES.md](../PHASE0-NOTES.md) for the full answers (A–E) and probe results. The
probes that behaved differently from the specification's assumptions:

- `isHeaded` cannot be used to detect a head that has **only parameters and no body**: the Nuts
  parser represents `string(description: "x")` as a named **tuple**, not a named object. Detection
  now accepts object, array **and** tuple heads (`CondensedTsonDesugarer.headOf`).
- Invalid dates throw `DateTimeParseException` rather than producing a diagnostic node.
- `a: }` silently yields `null`; unclosed `{` / `(` / `[` set `isErrorTree = true`.
- Diagnostics carry no line/column (the Nuts parser does not expose them for these errors).

## 2. Syntax deviations from the specification

- **String escaping.** The writer delegates scalar emission to the Nuts plain-TSON writer, which
  emits `\n`, `\"` and `\\` escapes instead of the literal-first `¶¶` / quote-run fences requested.
  This is cosmetic: every scalar round-trips exactly (verified by the quoting tests).
- **Root `@openapi`.** Emitted as `@openapi("3.0.0")` before `info` when `info` is present; otherwise
  the plain `openapi: "3.0.0"` pair form is used. Both are read.
- **`out(200):` / `in:` pair-with-head.** Implemented as specified (pair whose key is a head).
- **Array order.** Arrays always use the TSON "Full Array" order (params before elements):
  `array(attrs)[item]`. The alternative `array[item](attrs)` was not needed.
- **`changes[].date`.** Emitted as a plain quoted string (not a native TSON date). It round-trips
  identically; the native-date form is accepted on input.

## 3. Input locations

`in` is the request body (default). Other operation members use `@path` / `@query` / `@header` /
`@cookie` / `@param` / `@body`. The implementation accepts these annotations; the writer emits
parameters as plain OpenAPI parameter objects (see gaps). Confirm with the maintainer whether
unannotated members should default to body.

## 4. Generator changes

- `SwaggerStore` reads operation-level `enabled` and propagates path-level `enabled` to calls.
- `MainMarkdownGenerator._fillApiPaths()` filters calls by `enabled`.
- `NOpenAPIService` gained format detection and `readApi` / `readConfig` helpers.
- These changes are backward compatible: the existing JSON/YAML report generation is unchanged and
  the full existing test suite stays green.

## 5. Known gaps

- **Whole-document fallback.** When a construct cannot be condensed, the *entire* document is
  re-emitted as plain TSON rather than falling back per node. The output is always lossless and
  self-verified, but coarse.
- **Multi-media request/response** bodies are emitted in the plain `requestBody` / `responses` form.
- **Untyped / complex schemas** (`allOf`, `oneOf`, `anyOf`, `not`, `discriminator`,
  `additionalProperties`) are emitted as plain objects or with `@raw`; they are not condensed.
- **`components.responses` / `components.parameters`** are passed through verbatim (empty ones are
  dropped by canonicalization C1).
- **Operations `parameters`** are emitted as plain parameter objects inside `parameters [ … ]`
  (the condensed per-parameter form is accepted on input but not produced).
- **Quoted names cannot be used as heads** (Nuts parser limitation): the writer falls back to the
  pair form for any name that is not a bare identifier.
- **YAML round trips** are not possible on Nuts 1.1.0 (reader limitation); see
  [yaml-limitations.md](yaml-limitations.md).
- No line/column information in diagnostics.

## 6. Tests

- `Phase0ProbeTest` (16): parser behaviour.
- `Phase1FormatsTest` (7): detection, reading, normalization, store hand-off.
- `Phase2DesugarerTest` (7): the golden mapping and validation.
- `Phase3WriterTest` (7): canonicalization, condensed writer, JSON↔TSON round trips, idempotence.
- `Phase3ConvertTest` (5): the `convert` command through the file system.
- `GenerateExamplesTest` (2): regeneration and round trip of the committed `.tson` examples.

YAML round trip tests are intentionally absent and documented as a limitation instead.
