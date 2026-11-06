# Deprecated feature example

The same small API is provided in the three supported formats:

* `api.json` — OpenAPI JSON, using the plain `deprecated: true` member.
* `api.yaml` — OpenAPI YAML, using the plain `deprecated: true` member.
* `api.tson` — condensed TSON, using the `@deprecated` annotation (with an explicit
  `@deprecated(false)` where relevant).

They all describe:

* the deprecated `DELETE /users/{id}` operation;
* the `/legacy/status` path item marked deprecated, which NoAPI pushes down to both `GET` and
  `POST` (canonicalization C3);
* the deprecated `legacy` query parameter;
* the deprecated `X-Legacy-Token` header;
* the deprecated `LegacyUser` schema and its `email` property;
* the deprecated `oldEmail` property of `User`.

Generate the PDF from any of them:

```bash
noapi examples/deprecated/api.json --keep
noapi examples/deprecated/api.yaml --keep
noapi examples/deprecated/api.tson --keep
```

The generated document and PDF show `[DEPRECATED]` explicitly next to the operation title,
parameter name, header name, schema type title and property name, for example:

```text
DELETE /users/{id} [DEPRECATED]
legacy [DEPRECATED]
X-Legacy-Token [DEPRECATED]
LegacyUser [DEPRECATED]
oldEmail [DEPRECATED]
```

Convert between the three formats with:

```bash
noapi convert examples/deprecated/api.json -o /tmp/api.tson
noapi convert /tmp/api.tson -o /tmp/api.yaml
noapi convert /tmp/api.yaml -o /tmp/api.json
```
