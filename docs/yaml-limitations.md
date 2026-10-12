# YAML support and limitations (TODO: fix in Nuts)

NoAPI reads and writes YAML through the Nuts YAML reader/writer
(`NElementReader.ofYaml()` / `NElementWriter.ofYaml()`). JSON and TSON are the primary, fully
round-trippable formats; YAML is **best effort** on the current Nuts version (`1.1.0`).

## Status

| Direction | Status |
|---|---|
| Read simple YAML (scalars, flat maps, simple lists) | works |
| Read YAML produced by the Nuts YAML writer for complex OpenAPI trees | **fails** |
| Write YAML from an OpenAPI model | works |
| JSON → YAML → JSON round trip | **not possible** on Nuts 1.1.0 |

## Observed problems (Nuts 1.1.0)

1. **Lists of mappings cannot be read back.** The Nuts YAML writer emits a sequence item as

   ```yaml
   changes:
      -
         date: "2010-02-12"
         version: "1.1"
   ```

   and the Nuts YAML reader rejects it with
   `java.lang.IllegalArgumentException: no allowed key as array element`. This affects any array of
   objects, which is pervasive in OpenAPI (`info.changes`, `servers`, `parameters`, `tags`, response
   examples, …).

2. **Empty lists collapse to `null`.** An empty array is written as `details:` (no value) and reads
   back as `null` instead of `[]`.

3. **`tags: []` fails on re-read** (a variant of the two problems above).

Because of (1), a YAML document produced by NoAPI from a real API document cannot be read back by
NoAPI itself. Converting *to* YAML therefore produces output that is only consumable by an external
YAML tool, not by NoAPI.

## What NoAPI does today

- `noapi convert … --to yaml` writes the YAML output (the writer works) and reports it.
- Reading a YAML document that uses only flat maps and scalar lists works and is exercised by the
  existing tests (`NoApiUtils`, config parsing).
- The JSON and TSON pipelines are unaffected.

## TODO (fix in Nuts, then NoAPI)

- [ ] Fix the Nuts YAML reader so a block sequence item that is a mapping is parsed correctly.
- [ ] Fix the Nuts YAML writer so an empty sequence is written as `[]` (and read back as an empty
      array, not `null`).
- [ ] Add an explicit `[]` for empty maps/arrays.
- [ ] Once fixed, re-enable the JSON→YAML→JSON and YAML→TSON→YAML round-trip tests for the sample
      documents.
- [ ] Re-evaluate whether NoAPI should write YAML itself (a small emitter) instead of relying on
      the Nuts writer.
