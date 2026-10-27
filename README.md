# Nuts Open API
```NOAPI``` is a simple yet powerful commandline tool for generating professional OpenApi Pdf file based on OpenApi 3.0 Format.

## Supported features are
* OpenAPI 3.0 compatible
* Supported Yaml, JSON and TSON formats (TSON support is described below; JSON and TSON are fully
  round-trippable, YAML is best-effort — see [docs/yaml-limitations.md](docs/yaml-limitations.md))
* Supports custom text
* Supports conditional generation
* Supports change list generation
* Marks deprecated operations, parameters and properties (`deprecated` in JSON/YAML, `@deprecated`
  in TSON) and shows them in the generated document
* Conversion between JSON / YAML / TSON (`noapi convert`)

## Input formats

An API document or a NoAPI configuration document is detected from its extension first
(`.json`, `.yaml` / `.yml`, `.tson`), then from a content sniff. An explicit `--from` / `--json` /
`--yaml` / `--tson` override always wins.

* **JSON** — read with the lenient Nuts JSON reader (comments and trailing commas allowed).
* **YAML** — read with the Nuts YAML reader (best effort; see the limitations document).
* **TSON** — read with the Nuts TSON reader, then passed through the *condensed* desugarer. Both the
  condensed dialect and plain TSON (including this repo's historical plain-TSON output) are accepted.

The condensed TSON dialect is fully documented in [docs/condensed-tson.md](docs/condensed-tson.md).

## Converting documents

```bash
noapi convert <input> [<input> ...] [-o <output>] \
      [--from auto|json|yaml|tson] [--to auto|json|yaml|tson] \
      [--kind auto|api|config] [--style condensed|plain] [--force] [--strict]
```

* `--to` defaults to the output file extension, otherwise to `tson`.
* `-o` defaults to the input name with the target extension.
* `--style condensed` (default) emits condensed TSON; `plain` emits plain TSON.
* `--kind` selects API vs configuration document; `auto` detects it.
* `--force` is required to overwrite an existing output file.
* `--strict` turns example/schema validation mismatches into fatal errors (see below).

Examples:

```bash
# JSON API document -> condensed TSON
noapi convert api.json -o api.tson

# condensed TSON -> JSON (format inferred from the .json extension)
noapi convert api.tson -o api.json

# API -> YAML (best effort)
noapi convert api.json --to yaml -o api.yaml

# configuration document
noapi convert config.json --kind config -o config.tson
```

`noapi convert` (the command word) and `--convert` are equivalent. The global `--json` / `--yaml` /
`--tson` flags select the target format as well.

### Example / schema checking

Every example of an API document is checked against the constraints of the schema it documents
(`E_EXAMPLE_MISMATCH`). By default a mismatch is only printed as a warning on the standard error
and the run continues. With `--strict` (accepted by `convert` and by the pdf generation) the first
mismatching document aborts the run.

### Deprecated operations and properties

`deprecated` is the OpenAPI 3.0 flag (Operation Object and Schema Object). NoAPI also accepts it on a
path item and pushes it down into each operation (canonicalization C3), so the whole path can be
retired at once. In condensed TSON the flag is written as the `@deprecated` annotation — on an
operation head, on a path item, on a schema declaration or on a property — and
`@deprecated(false)` writes an explicit false; the writer keeps that annotation form. In the
generated document the flag is shown as `[DEPRECATED]` next to the operation title and to the field
name. In JSON/YAML only the plain `deprecated: true` pair form is read.

### Self-verification

The condensed writer never emits unverified text. After writing condensed TSON, NoAPI re-reads it
with the real parser, desugars it, canonicalizes both trees and diffs them. On any mismatch (or
writer error) it falls back to plain TSON, which is lossless. The `convert` command prints a note
when this happens.

### Canonicalization

Round trips are identical except for the following canonicalizations (applied to both sides before
comparison):

* **C1** empty `tags`, `parameters`, `required` arrays and empty `components.responses` /
  `components.parameters` objects are dropped.
* **C2** `required` is re-ordered to property order; names not in `properties` are kept.
* **C3** path-level `summary`, `description`, `parameters`, `enabled`, `deprecated` are pushed into
  each operation.
* **C4** parameter-level schema keywords (`type`, `format`, …) move into `schema`.
* **C5** `format: "object::X"` ↔ bare `X` only on `object`.
* **C6** multi-line strings are compared after trimming trailing whitespace on each line.
* **C7** integer-looking response keys become strings.

Everything else must be identical, including key order and array order.
