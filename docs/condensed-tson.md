# Condensed TSON — syntax reference

This document describes the *condensed TSON* dialect accepted and produced by NoAPI. TSON is the
Nuts data format; **condensed TSON** is a NoAPI-specific convention that maps the most frequent
OpenAPI constructs onto TSON *heads* (`GET("/x") { … }`, `out(200): T(json)`, `object(…) { … }`)
instead of plain `key: value` pairs. Plain TSON is always accepted too; the two may be mixed in one
document.

The reader (`CondensedTsonDesugarer`) expands the condensed constructs into a plain OpenAPI tree
(an `NElement`), and the writer (`CondensedTsonWriter`) does the reverse. Anything that is not a
recognised condensed pattern is passed through **verbatim**.

> The mapping below is the one implemented and covered by the test suite
> (`Phase2DesugarerTest`, `Phase3WriterTest`). Where the implementation differs from the original
> specification it is called out under *Deviations* at the end.

---

## 1. Document layout (API document)

Writer order: `@openapi`, `info`, `custom`, `servers`, `paths`, `components`, `tags` (omitted when
empty), `security`.

```tson
@openapi("3.0.0")

info {
  title: "My API ${titleSuffix}"
  version: "1.2.0"
  changes [
    { date: "2024-07-16", version: "1.2.0", title: "Added /x", observations: "…", details: ["ADDED /x"] }
  ]
}

custom { openapi-document-id: my-api }

servers [
  url("https://example.com") { description: "Main server" },
]

security [ JWTAuth, OAuth(read, write) ]
```

- `@openapi("3.0.0")` as the **first element** of the document ≡ `openapi: "3.0.0"`. The reader
  accepts both the annotation form and the plain pair form; the writer emits the annotation form
  when `info` is present.
- `info`, `custom`, `tags`, `x-logo`, `contact` are **verbatim**: no condensation inside. Template
  strings (`${titleSuffix}`, `${!internal}`, `<<server-prod-url>>`, `##your company##`) are opaque
  strings; NoAPI variable resolution runs later on the desugared tree.
- `servers`: `url(<string>) { …other server keys… }` ≡ `{ url: <string>, …keys }`. Plain server
  objects are accepted as well.
- `security`: bare names. `[ JWTAuth ]` ≡ `[{ "JWTAuth": [] }]`; `OAuth(read, write)` ≡
  `{ "OAuth": ["read", "write"] }`.

There is **no** required blank line between sections; the writer puts one between top-level sections
for readability. Top level members may be written either with a head (`paths { … }`) or as pairs.

---

## 2. Paths and operations

`paths { … }` contains **operations**, flattened: one element per `(method, path)`. Plain path items
(`"/x": { get { … } }`) may be mixed in and are read as-is.

```tson
paths {
  @enabled("${!internal}")
  @summary("Returns the deployed API version")
  GET("/version") {
    out(200): VersionResponse(json) {
      description: "Returns the deployed API version information"
      example { version: "1.4" }
    }
  }

  @summary("Log in and obtain a token")
  POST("/login") {
    @required in: LoginModel(json) {
      description: "Login information"
      example { username: "customer", password: "1234" }
    }
    out(200): LoginResponse(json) { description: "Well formed token" }
    out(400): ErrorType(json) { description: "Bad credentials" }
  }

  @summary("Retrieves latest claim details, given a {policyNumber}")
  GET("/policy/{policyNumber}/latest-claim") {
    @path @required policyNumber: string(description: "Policy number", example: "E13508")
    out(200): GetLatestClaimResponse(json) { description: "Claim details" }
  }
}
```

**Head.** `METHOD(<path-string> [, key: value …]) { body }`. `METHOD` ∈
`GET POST PUT DELETE PATCH HEAD OPTIONS` (upper case). The first positional argument is the path;
extra `key: value` pairs in the parentheses are operation-level OpenAPI keys.

**Operation annotations** (each on its own line before the head):

| Annotation | Expansion |
|---|---|
| `@summary("…")` | `summary` |
| `@description("…")` | `description` |
| `@operationId("…")` | `operationId` |
| `@tags("a", "b")` | `tags: ["a", "b"]` |
| `@deprecated` / `@deprecated(false)` | `deprecated: true` / `false` |
| `@enabled("…")` | NoAPI `enabled` extension |
| `@security(Name, Other(scope))` | `security` |

The same keys are also accepted as pairs in the head parentheses or in the body.

**Flattening.** A path with GET and POST is written as two operations. Path-level `summary`,
`description`, `parameters` and `enabled` from a JSON/YAML source are **copied into each operation**
by the canonicalizer (an operation's own value wins). When reading, operations with the same path are
grouped under one path item, in order of appearance.

**Operation body members:**

| Member | Meaning |
|---|---|
| `[@required] in: Type(media…) [{…}]` | request body (the key `in` is reserved for it) |
| `[@loc] [@required] name: Type(attrs)` | parameter named `name` |
| `out(status): Type(media…) [{…}]` | response |
| `out(status): void [{…}]` | response without content |
| anything else | plain OpenAPI key/value of the operation, verbatim |

**Input location annotations.** `@path`, `@query`, `@header`, `@cookie`, `@param`, `@body`. `@param`
means `path` when `{name}` appears in the path template, otherwise `query`. The request body is the
default: the reserved key `in` is the body.

**Responses.** `out(200)`, `out("2XX")`, `out(default)`. The value is `Type(media…)` or `void`; the
optional body holds `description`, `example`, and other response keys.

**Media shorthands** (inside the parentheses of `in`/`out` types): `json`→`application/json`,
`xml`→`application/xml`, `text`→`text/plain`, `form`→`application/x-www-form-urlencoded`,
`multipart`→`multipart/form-data`, `binary`→`application/octet-stream`. A quoted string is a literal
media type. The writer condenses a request/response only when it has exactly one media type;
otherwise it emits the plain `requestBody` / `responses` form.

**Examples.** A body member `example <literal-or-object>` is the media `example`.

---

## 3. Type expressions

```
TypeExpr := primitive [ "(" attrs ")" ]              string(...) number(...) integer(...) boolean(...)
          | SchemaName [ "(" attrs ")" ]             reference
          | "object" [ "(" attrs ")" ] [ "{" props "}" ]
          | "array" [ "(" attrs ")" ] "[" TypeExpr "]"    array(description: "d")[string]
          | "void"                                   responses only
```

- **Arrays** use the TSON "Full Array" order (params before elements):
  `array(description: "d")[string]` ≡ `{ type: array, items: { type: string }, description: "d" }`.
  The bracket content is the *item type expression*; nested arrays nest.
- **attrs** are `key: value` pairs copied into the OpenAPI schema as keywords (`description`,
  `example`, `minLength`, `maxLength`, `enum`, `nullable`, `default`, `pattern`, `minimum`, `title`,
  `additionalProperties`, `x-*` …). Special cases:
  - `format` with a bare name on head `object`: `format: intermediate-structure` ≡
    `"object::intermediate-structure"`. A *quoted* string is always verbatim. On other heads a bare
    name is just the string: `string(format: date)` ≡ `"date"`.
  - `required: ["a"]` in `object(…)` lists *extra* required names that are not declared properties;
    it is merged after the `@required`-marked properties.
- In `in`/`out` position the parentheses hold **media shorthands**, not attrs.
- A **reference** `Token` expands to `{ "$ref": "#/components/schemas/Token" }`.

---

## 4. Schemas (`components.schemas`)

```tson
components {
  schemas {
    @description("Token details")
    Token {
      @required value: string(minLength: 0, maxLength: 512, description: "Authorization Token")
    }

    @description("Login response")
    LoginResponse: object(format: intermediate-structure) {
      @required token: Token
      example { token: { value: "abc" } }
    }

    Status: string(enum: ["NEW", "OPEN", "CLOSED"])

    @raw Raw: { allOf: [ { "$ref": "#/components/schemas/Token" } ] }
  }
}
```

- `Name { props }` ≡ `Name: object { props }` (implicit `object`).
- `Name: <TypeExpr>` with no body declares a non-object schema (`Status` above).
- Schema keywords go in the **parentheses of the head** or in `@description("…")`. The body contains
  only properties and the single reserved member `example`. This removes keyword/property collisions
  (real sources have properties named `title`).
- **Properties.** `[@required] name: TypeExpr(attrs)`.
  - Untyped property: a named object *without a type head* is a verbatim schema:
    `accidentDate { description: "…", format: "date" }` ≡ `accidentDate: { description, format }`
    (no `type` added).
  - Nested inline object: `address: object(description: "…") { street: string, zip: string }`.
  - A property literally named `example` or `properties` is declared in an explicit `properties { … }`
    block.
- **Body `example`** (named object or `example: <literal>`) is the schema-level `example`.
- `@required` ≡ the name is added to the schema's `required` list. `@required(false)` is an explicit
  false. The `required` array order is property order (canonicalization C2).
- **`@raw`** before a schema declaration: its body is a verbatim plain OpenAPI schema, no
  condensation inside. This is the fallback for schemas the writer cannot condense (`allOf`,
  `oneOf`, `anyOf`, `not`, `discriminator`, …).

---

## 5. Other `components` entries

```tson
components {
  securitySchemes {
    JWTAuth: http(bearer) { bearerFormat: JWT, description: "…" }
    api_key: apiKey(header: "X-API-KEY") { description: "…" }
  }

  headers {
    @required(false) X-MYAPP-API-VERSION: string(description: "The request API version")
  }
}
```

- `securitySchemes`: `Name: http(<scheme>)`, `Name: apiKey(header|query|cookie: "<name>")`,
  `Name: openIdConnect(url: "…")`, `Name: oauth2 { flows { … } }`; the body holds the other keys.
  An entry without a head (`Name { type: http, … }`) is read as plain.
- `headers`: `[@required[(bool)]] Name: Type(attrs)` ≡ `{ required, description, schema: { … } }`.
- `components.responses`, `components.parameters` and all other component sections: plain, verbatim.

---

## 6. Annotation catalogue

| Annotation | Applies to | Args | Meaning |
|---|---|---|---|
| `@openapi` | document | version string | `openapi` |
| `@summary` `@description` `@operationId` `@tags` `@deprecated` `@enabled` `@security` | operation | see §2 | operation keys |
| `@description` | schema declaration | string | schema `description` |
| `@required` | property, parameter, `in`, header | optional bool (default `true`) | required flag |
| `@path` `@query` `@header` `@cookie` `@param` `@body` | operation member | none | input location |
| `@raw` | schema declaration | none | verbatim plain schema |

An unknown annotation, or an annotation on a construct that does not accept it, is an error
(`E_UNKNOWN_ANNOTATION`).

---

## 7. Head catalogue

| Head | Context | Arguments |
|---|---|---|
| `GET` … `OPTIONS` | `paths` | path string, then optional pairs |
| `out` | operation body (as pair key) | status int, `"2XX"` or `default` |
| `url` | `servers` | string |
| `http` | `securitySchemes` | scheme name |
| `apiKey` | `securitySchemes` | one pair `header\|query\|cookie: "<name>"` |
| `openIdConnect` | `securitySchemes` | pair `url: "…"` |
| `string number integer boolean object` | schemas, properties, parameters, headers | pairs (attrs) |
| `array` | same | pairs (attrs), then `[ item type ]` |
| `<SchemaName>` | `in`/`out`, properties | media shorthands (`in`/`out`) or pairs (properties) |
| `<SecurityName>` | `security` lists | scope names |

---

## 8. Config documents

```tson
openapi-config: "3.0.0"
target-name: "Assurances BIAT"
target-id: assurances-biat
version: "1.4-20230215"
openapi-document-id: digiclaim-api
variables {
  server-prod-url: "https://example.com/rest-auth/api"
  company-api-key { value: "…", observations: "rotate yearly" }
}
```

- A scalar `name: "v"` under `variables` ≡ `{ value: "v", observations: "" }`. The long form
  `name { value, observations }` is used when `observations` is non-empty or other keys exist.
- Everything else is plain. Trailing commas and comments are allowed in TSON.
- Document kind is detected from the root key: `openapi-config` → config, `openapi` / `@openapi` →
  API document.

---

## 9. Writer emission rules

- 2-space indent; a blank line between top-level sections.
- Operation annotations on their own lines above the head; `@required`, location annotations and
  `@required(false)` stay inline with the member.
- Keys: unquoted if a valid TSON identifier (and not `true`/`false`/`null`), otherwise quoted. Path
  strings are always quoted.
- Strings are produced by the Nuts plain-TSON writer, which round-trips reliably (quotes, backslashes,
  tabs, newlines, `¶`, and the `${…}` / `<<…>>` / `##…##` template markers). See *Deviations*.
- Bare names for `format` (when identifier-safe, with the `object::` prefix stripped on `object`),
  `bearerFormat`, `scheme`, media shorthands, `target-id`, `openapi-document-id`.
- Numbers (lexical form preserved), booleans, `null`.
- Key order is preserved everywhere. Never sorted.
- **Self-verification is mandatory** (see below).

### Self-verification

After producing condensed TSON text, NoAPI re-reads it with the real Nuts parser, desugars it,
canonicalizes both trees and diffs them. On any mismatch (or writer error) it falls back to **plain
TSON**, which is lossless. A file that has not been verified is never written.

---

## 10. Canonicalization (the only allowed differences)

Round trips are identical **except**:

- **C1** empty `tags`, `parameters`, `required` arrays and empty `components.responses` /
  `components.parameters` objects are dropped.
- **C2** `required` is re-ordered to property order; names not in `properties` are kept.
- **C3** path-level `summary`, `description`, `parameters`, `enabled` are pushed into each operation.
- **C4** parameter-level schema keywords (`type`, `format`, …) move into `schema` (applied only to
  actual parameters, i.e. items of a `parameters` array).
- **C5** `format: "object::X"` ↔ bare `X` only on `object`.
- **C6** multi-line strings are compared after trimming trailing whitespace on each line.
- **C7** integer-looking response keys become strings.

Everything else must be identical, including key order of `properties`, `paths`, `schemas`,
`changes`, `responses`, and array order.

---

## Deviations and limitations

- **String escaping.** The original specification asks for literal-first strings with no backslash
  escapes (`¶¶` for newlines, quote-run fences for `"`). The implementation delegates all scalar
  emission to the Nuts plain-TSON writer, which escapes newlines as `\n`, quotes as `\"` and
  backslashes as `\\`, and round-trips them correctly. This keeps the writer simple and provably
  lossless; it is a cosmetic deviation only.
- **Writer granularity of fallback.** When a construct cannot be condensed, the whole document is
  re-emitted as plain TSON (not per-node). The result is always lossless and self-verified, but a
  single unsupported construct causes the whole file to be plain.
- **`changes` entries** are emitted as plain objects rather than a dedicated condensed form. This is
  valid condensed TSON and round-trips.
- **Quoted names are never used as heads.** The Nuts TSON parser cannot parse a quoted name followed
  by a body or parameter list: `"company-api-key" { … }` is read as a bare string plus an unnamed
  object. The writer therefore emits `key: value` (pair form) whenever a name needs quoting, e.g.
  `"company-api-key": { value: "abc" }`, and reserves the head form (`name { … }`) for bare
  identifier names. All names in the samples are either bare or already in pair position, so this is
  invisible for them.
- **Media types other than `application/json`** are emitted with a quoted literal media type when a
  shorthand does not exist.
