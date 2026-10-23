# YAML support and limitations

NoAPI reads and writes YAML through the Nuts YAML reader/writer
(`NElementReader.ofYaml()` / `NElementWriter.ofYaml()`). JSON and TSON are the primary, fully
round-trippable formats; YAML is supported when the underlying Nuts YAML implementation can
parse it.

## Status

| Direction | Status |
|---|---|
| Read simple YAML (scalars, flat maps, simple lists) | works |
| Read YAML produced by the Nuts YAML writer for complex OpenAPI trees | improved in current Nuts versions |
| Write YAML from an OpenAPI model | works |
| JSON → YAML → JSON round trip | depends on the Nuts YAML reader/writer |

## Known considerations

- Some YAML constructs may still be parsed differently depending on the Nuts version.
- If parsing fails, use JSON or TSON as the canonical round-trippable formats.
- The YAML support is provided by the Nuts library; any remaining limitations are tracked upstream.

## What NoAPI does today

- `noapi convert … --to yaml` writes the YAML output and reports it.
- Reading YAML documents is supported when the Nuts YAML reader can parse them.
- The JSON and TSON pipelines are the primary, fully round-trippable paths.

