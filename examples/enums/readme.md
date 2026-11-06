# Enum schema type example

The same small API is provided in the three supported formats:

* `api.json` — the enum is a plain JSON Schema `enum` member.
* `api.yaml` — the same OpenAPI document in YAML.
* `api.tson` — the condensed TSON `enum(values: [ … ])` sugar.

The `Color` schema is not an object type, but it is still a schema type and is listed under
**SCHEMA TYPES** in the generated document and PDF:

```text
=== Color

The color options available.

TYPE: `enum ALLOWED {'RED', 'GREEN', 'BLUE', 'YELLOW'}`
```

Generate the PDF from any format:

```bash
noapi examples/enums/api.json --keep
noapi examples/enums/api.yaml --keep
noapi examples/enums/api.tson --keep
```

Convert between the formats with:

```bash
noapi convert examples/enums/api.json -o /tmp/api.tson
noapi convert /tmp/api.tson -o /tmp/api.yaml
noapi convert /tmp/api.yaml -o /tmp/api.json
```
