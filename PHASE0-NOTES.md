# PHASE 0 — Recon and parser probes

This document records the Phase 0 findings for the *JSON / YAML / condensed-TSON input and
bidirectional converter* work. It is normative for the later phases: every deviation from the
prompt is justified here.

Environment:

* Repo: `thevpc/nuts-openapi` (NoAPI), single Maven module `noapi`.
* Java: Corretto JDK 8 (1.8). Maven 3.9.11.
* Nuts: `net.thevpc.nuts:nuts:1.1.0` + `net.thevpc.nuts:nuts-runtime:1.1.0.0`.
* All probes were run against the real Nuts TSON parser (`NElementReader.ofTson()`), executed as
  throwaway programs during recon and then re-committed as JUnit tests
  (`noapi/src/test/java/.../Phase0ProbeTest.java`).

---

## A. Where does the generator read `enabled` and `summary`?

**Answer.** `summary` is read at **operation level with a path-level fallback**;
`enabled` is read **only at path level**.

* `MainMarkdownGenerator._fillApiPaths()` (line ~466) filters the whole path list with
  `rmodel.templater.evalBoolean(x.enabled, true)` — i.e. `MPath.enabled` only.
* `MainMarkdownGenerator._fillApiPathMethod()` (line ~568) computes
  `NStringUtils.firstNonBlank(call.summary, path.summary)` and the same for `description`.
* `SwaggerStore.findPaths()` reads `enabled` / `summary` / `description` / `parameters` from the
  **path item**, never from the operation.

**Decision (prompt default).** The desugarer places `@enabled` and `@summary` at **operation
level** (standard OpenAPI). The generator is extended in a backward-compatible way:

* `MCall` gains an `enabled` field.
* `SwaggerStore` reads operation-level `enabled` into it.
* `MainMarkdownGenerator` filters out calls whose (operation-level) `enabled` is false, in addition
  to the existing path-level filter. Existing JSON/YAML documents (path-level `enabled`) keep
  working unchanged; flattened condensed documents (operation-level `enabled`) work too.

`summary` needed **no** generator change: `firstNonBlank(call.summary, path.summary)` already
prefers the operation.

## B. Parameter `type` at parameter level or `schema.type`?

**Answer / decision.** Both are already supported. `SwaggerStore._parseParamList()` (and
`_parseHeaderList()`) read `type` first and fall back to `schema.type` via
`orElseGetOptionalFrom(...)`. The desugarer therefore emits the modern `schema` form (standard
OpenAPI 3.0) and the generator keeps accepting the Swagger-2 style `type` used by the DigiClaim
sample. No generator change is required.

## C. How are JSON / YAML / TSON read today?

**Answer.**

* JSON: `NElementReader.ofJson().read(...)`. Leniency (trailing commas, `//` and `/* */` comments)
  is a **Nuts `ofJson` feature**, not a custom/Jackson feature — verified by probe
  `JSON_TRAILING_COMMA` / `JSON_COMMENT`. The existing config files with trailing commas keep
  loading.
* YAML: `NElementReader.ofYaml().read(...)`. The Nuts 1.1.0 YAML reader/writer is **incomplete**
  (see the "Known gaps" section); YAML support is best-effort and documented as a TODO.
* TSON: `NElementReader.ofTson()`. Writing: `NElementWriter.ofTson()` / `ofPlainTson()`.
* Format was detected today by a one-line sniff (`t.startsWith("{")` → JSON, else YAML) in
  `SwaggerStore.loadStoreModel()` and `NoApiUtils.loadElement()`.

## D. What does the current "TSON (not yet fully supported)" path do?

**Answer.** `TsonStore` is a **non-functional stub**:

* `loadStoreModel()` reads the file with `NElementReader.ofJson()` (never `ofTson`), and only
  understands `info`-rooted keys; it ignores the NoAPI `custom` section (so `getMultiDocuments()`
  returns empty, `getId()` reads `info.id`).
* `findPaths()` returns `Collections.emptyList()` — **no paths are ever generated**.
* `loadConfigFile()` returns `null` and `loadVars()` returns an empty map.
* It is selected for `.tson` files in `NOpenAPIService.run()` but is effectively dead.

**Decision.** `TsonStore` is replaced by: *real* TSON read (`ofTson`) → condensed desugaring →
the **same** `SwaggerStore` used by JSON/YAML. This guarantees the tree handed to the existing
pipeline has exactly the shape JSON/YAML produce today (goal G2).

## E. How is document-vs-config detected and how are config files loaded?

**Answer.**

* Detection is **filename based**: `NoApiUtils.isProjectMainFileName()` (`.json`/`.tson`, excluding
  `.config.*`) and `isProjectConfigFileName()` (`.config.json` / `.config.tson`).
  `.yaml` / `.yml` are **not** recognized today — they are added in Phase 1.
* `NOpenAPIService.searchConfigPaths()` finds config files whose name starts with the main file base
  name (`base.`, `base-`, `base_`).
* Config loading is JSON-only (`SwaggerStore.loadConfigFile` uses `ofJson`). There is a latent bug:
  `c.version = obj.getStringValue("observations")` reads `observations` into `version`; the model
  also has a separate `observations` field. Phase 1 fixes this to `getStringValue("version")` while
  keeping `observations` in its own field.

---

## Probe results (real Nuts TSON parser, 1.1.0)

Notation: `name='x'` = named element, `params[…]`, `annots[…]`. "OK" = behaved as the prompt
expected; otherwise the deviation is recorded.

| # | Snippet | Result |
|---|---|---|
| P1 | `out(200) : Foo(json) { description: "x" }` | **OK.** `PAIR` whose key is `NAMED_TUPLE name='out'` with param `200`, value `FULL_OBJECT name='Foo'` with param `json` and body. |
| P2 | `@required name: string(description: "d")` | **OK.** Annotation attaches to the **pair's key** (`NAME annots=[@required]`), value is `NAMED_TUPLE name='string'`. |
| P3 | `@a @b("x") name: T` | **OK.** Multiple annotations preserved in order on the key. |
| P4a | `array(description: "d")[string]` | **OK.** Single `FULL_ARRAY name='array'` with param `description:"d"` and child `string`. |
| P4b | `array[array[string]]` | **OK.** Single `NAMED_ARRAY name='array'`, nested. |
| P4c | `array[string](description: "d")` | **DEVIATION.** Produces a **`FRAGMENT`** of `NAMED_ARRAY array[string]` + a standalone `TUPLE (description:"d")` — two nodes, not one. **Not supported** (prompt: accept only if single node). Writer always emits the spec order P4a. |
| P4d | `array[string(format: date)]` | **OK.** Item type carries its own attrs. |
| P5 | `format: intermediate-structure`, `format: date` | **OK.** Bare hyphenated name as a value (`NAME`, readable as string). |
| P6 | `{ date: 2024-07-16, version: "1" }` | **OK.** Native `LOCAL_DATE` inside inline objects. |
| P7 | `description:` + `¶¶ a` + `¶¶ b` + next pair | **OK inside a named object** (`¶¶` common indent stripped, next pair not swallowed). A bare `¶¶` at document root yields a `FRAGMENT`. |
| P8 | `out(200): void` then `out(400): ErrorType(json){…}`; `token: Token` then `example { a: 1 }` | **OK.** Separate pairs. (At document root this yields a `FRAGMENT`, normalized in Phase 1.) |
| P9 | `GET("/x", summary: "s") { }` | **OK.** Positional string + pair inside the parentheses of a `FULL_OBJECT`. |
| P10 | `@openapi("3.0.0")` then `info { … }` | **DEVIATION.** The annotation does **not** produce an `openapi` pair: it **decorates the next node (`info`)**. Handled by the desugarer: an `@openapi` attached to the first `info` is lifted to the root `openapi` key. Two stacked annotations before a method head are preserved in order. |
| P11 | `k { … }` vs `k: { … }`; `k […]` vs `k: […]` | **OK.** Distinct node shapes (`NAMED_OBJECT`/`NAMED_ARRAY` vs `PAIR`+`OBJECT`/`ARRAY`); hidden behind one `member(k)` adapter. |
| P12 | trailing/absent commas, `//`/`/* */`, `"""` fences | **OK** (documented via tests). |
| P13 | `N: object(format: x){…}`, `N {…}`, `N: string(enum:["a"])` | **OK.** Declaration forms as specified. |
| P14 | `http(bearer)`, `apiKey(header: "X-API-KEY")` | **OK.** Mixed positional/pair params in a `NAMED_TUPLE`. |
| P15 | What the Nuts TSON writer emits | The writer round-trips JSON→TSON→JSON exactly, but its style is **not canonical** (`wrap{…}`, inline annotations, no section blank lines). Therefore the **condensed writer is a custom text emitter**, verified by re-reading (§7.4). |

### Additional probes (edge cases)

| Probe | Result |
|---|---|
| `GET("/x") { }` with `@enabled("${!internal}")` | Annotation attaches to the `FULL_OBJECT` head. |
| Document root with several top-level pairs | Parses to `FRAGMENT`; `wrapIntoObject()` normalizes to a plain object (order preserved). |
| `NElementReader.ofTson().read(s, NObjectElement.class)` | Returns an object whose *internal* content is still a fragment; `normalize(JSON)` throws `unsupported ensureJson for fragment`. **Always read as `NElement` and normalize via `wrapIntoObject()`.** |
| Unclosed object/paren/bracket | `isErrorTree()==true` with diagnostics (`missing rbrace`, …). **Token has no line/column.** |
| `2025-02-30` | `DateTimeParseException` from the parser (invalid date thrown, not a diagnostic). |
| `@` alone | Type `EMPTY`, `isErrorTree()==false`, `toString()` throws NPE. |
| `a: }` | Silently `a: null`, `isErrorTree()==false` (parser is *fail-never*). |
| `NElementDiagnostic` | Exposes only `message()` and `token()`; `token()` is `null` in practice, `NElementToken` has only `image()`/`typeName()` — **no line/column is available from the parser** (see G5 below). |

## G5 (diagnostics) — limitation

The prompt requires **file, line and column** for diagnostics. Nuts 1.1.0 does **not** expose
line/column through `NElementDiagnostic`/`NElementToken`. The validation pass therefore reports
`file` + `code` + `message` and marks the path through the tree (node path) for semantic errors,
and surfaces the parser's own diagnostics for syntax errors. Line/column becomes available only
when a future Nuts version exposes it; this is documented rather than faked. (A best-effort
line/column is attached when a snippet can be located in the source text; otherwise `-`.)

## Known Nuts 1.1.0 gaps (YAML, dates, diagnostics)

* **YAML writer/reader is incomplete**:
  * `tags: []` (empty array) → `NullPointerException` when re-reading the Nuts-written YAML.
  * lists of objects (`- {…}`, e.g. `changes`, `servers`) → `IllegalArgumentException: no allowed
    key as array element` on read.
  * simple objects/lists round-trip correctly.
  * **Decision:** JSON and TSON are first-class; YAML is best-effort. `--to yaml` may be
    lossy/rejected for documents containing arrays of objects or empty arrays. Tracked in
    `docs/yaml-limitations.md`.
* Native TSON dates reject impossible dates by throwing (not by a diagnostic).
* The parser is *fail-never* (`a: }` is accepted), so all semantic checks live in the Phase 2
  validation pass.

## Files

* Probe sources (recon): `/tmp/opencode/probe/Probe*.java`.
* Committed probe tests: `noapi/src/test/java/net/thevpc/nuts/toolbox/noapi/convert/Phase0ProbeTest.java`.
