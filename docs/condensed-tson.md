# OpenAPI (Condensed) TSON — syntax reference (revision 4)

*Condensed TSON* is a NoAPI-specific dialect of TSON (the Nuts data format). It maps the most frequent
OpenAPI constructs onto TSON *heads* (`GET("/x") { … }`, `out(200): T(json)`, `object(…) { … }`)
instead of plain `key: value` pairs. Plain TSON is always accepted too; the two may be mixed in one
document.

**The attributes of a construct live in the parentheses of its head** (revision 4): the description, the
example and the other keywords of a schema, of a request body, of a response, of a response component,
of a security scheme or of a server are written between the parentheses of the head, next to the
arguments of the head:

```tson
LoginRequest(description: "Login information", example: { username: "customer" }) {
  @required username: string(description: "User name")
}
```

The body form (`LoginRequest { … }` with the keywords in the body) is still read, but the writer only
emits the head form.

The reader (`CondensedTsonDesugarer`) expands condensed constructs into a plain OpenAPI tree (an
`NElement`); the writer (`CondensedTsonWriter`) does the reverse. Anything that is not a recognised
condensed pattern is passed through **verbatim**.

Changes against the previous revision are listed in the *Changelog* at the end. Items marked **(new)**
or **(changed)** in the text are part of that list.

---

## 0. Lexical conventions

**Separators.** The reader accepts commas, whitespace/newlines, or a mix, everywhere (members of
`{}`, elements of `[]`, arguments in `()`, trailing separators). The **writer emits whitespace only**:
newline between members of a block, a single space between inline elements and arguments. It never
emits a comma.

**Arguments and attributes of a head (new).** The parentheses of a head hold two kinds of arguments:

| Kind | Written | Meaning |
|---|---|---|
| **argument** (positional) | `json`, `bearer`, `header: X-API-KEY`, `url: "…"`, `url("…")` | the *role* of the head: the media type, the security scheme, the parameter name, the server url |
| **attribute** (named) | `description: "…"`, `example: {…}`, `bearerFormat: JWT` | the *keywords* of the construct: the description, the example, the other OpenAPI keys |

The role of a positional argument is fixed by the head (`out(200)` takes the status, `in`/`out` take
the media types, `apiKey` takes the `header`/`query`/`cookie` pair, `http` takes the scheme, `url`
takes the url). Every **named** argument is an attribute of the construct and is copied into the
OpenAPI object as a key (`description`, `example`, `bearerFormat`, …). The reader accepts them in any
order and the writer always writes the media type or the scheme first.

**Bare tokens.** A *bare token* matches `[A-Za-z_][A-Za-z0-9_.-]*` and is not `true`, `false` or `null`.
Hyphens are allowed (`openapi-document-id`, `X-MYAPP-API-VERSION`). A token starting with a digit or
sign (`1.0.0`, `2XX`) is **never** bare: the writer quotes it.

**Quoting policy of the writer.** A string value is written bare only in the positions listed below,
and only if it is a bare token; otherwise it is quoted.

| Written bare (when a bare token) | Always quoted |
|---|---|
| keys; schema names; security scheme names; scope names | free text: `description`, `summary`, `title`, `observations`, `message`, … |
| enum values | versions (`version`, `openapi`, `openapi-config`, …) |
| `format` (not on `object`), `bearerFormat` | paths and URLs (`{…}` and `//` are not safe in bare tokens) |
| the `scheme` of an `http` security scheme | every string value inside an `example` block |
| the name argument of `apiKey(...)` | any other string value |
| the document id (`info.id`) | |

Inside `example` blocks, string values are always quoted (they are data: a bare `true` or `123` must
keep its meaning); keys follow the normal key rule. Numbers, booleans and `null` are native.

The reader accepts bare or quoted in all positions.

**Line strings and block strings (new).** TSON has two string markers:

| Written | Meaning |
|---|---|
| `¶ text` | a **line string**: the value is everything after `¶` up to the end of the line |
| `¶¶ text` | a **block string**: the value continues on the consecutive `¶¶` lines that follow, joined with a line break |

The whitespace between the marker and the text is trimmed. A block string ends at the first line
that does not start with `¶¶`; writing the marker alone and continuing with plain lines is a syntax
error (the parser reports a fragment). There is **no closing delimiter**, so these strings must be
the last token on
their line: they work for a member on its own line (`description: ¶ Main server`) and cannot be used
inline (`{ description: ¶ x }` would swallow the `}`), in annotation arguments or in parentheses.

The reader accepts them wherever a string is expected. The **writer never emits them** (it emits
quoted strings, see *Deviations*); they are the recommended form for free text in hand-written
documents because they need no quoting or escaping.

**Temporal literals (new).** TSON knows temporal literals, which are written without quotes:

| Written | Kind | Example |
|---|---|---|
| `2024-07-16` | date | `date: 2024-07-16` |
| `2024-07-16T10:15:00` | local date-time | `since: 2024-07-16T10:15:00` |
| `2024-07-16T10:15:00Z` | instant | `at: 2024-07-16T10:15:00Z` |
| `10:15:00` | time | `at: 10:15:00` |

They are read as their textual value and serialized as strings. The writer writes a date, a
date-time and an instant as a bare literal and a **time as a quoted string** (a bare time cannot be
parsed, because of the colon). See *Deviations* for the loss of the seconds.

**Duplicate keys (new).** TSON allows the same key twice in one block; JSON does not. The reader
keeps the **last** occurrence, drops the earlier ones and reports one `W_DUP_KEY` warning per
duplicated key. This makes documents that repeat `examples` (or any other key) readable instead of
fatal.

---

## 1. Document layout (API document)

Writer order: `@openapi`, `info`, `custom`, `servers`, `paths`, `components`, `tags` (omitted when
empty), `security`.

```tson
@openapi("3.0.0")

info {
  id: my-api
  title: "My API ${titleSuffix}"
  version: "1.2.0"
  changes [
    { date: "2024-07-16" version: "1.2.0" title: "Added /x" observations: "…" details: ["ADDED /x"] }
  ]
}

servers [
  url("https://example.com", description: "Main server")
]

security [ JWTAuth OAuth(read write) ]
```

- `@openapi("3.0.0")` as the **first element** of the document ≡ `openapi: "3.0.0"`. The reader
  accepts both the annotation form and the plain pair form; the writer emits the annotation form
  when `info` is present.
- **`info.id` (new).** A bare token identifying this API document; config documents refer to it through
  their `openapi-document-id`. In the desugared tree it is stored as `custom.openapi-document-id` and the
  `id` key is removed from `info`; the writer does the reverse. `info.id` together with an explicit
  `custom.openapi-document-id` is an error (`E_DUPLICATE_ID`). In the condensed TSON dialect, `info.id`
  is the canonical form and the `custom` section is not written by the writer; a `custom.description`
  that merely repeats `info.description` is redundant and should not be written.
- **The `custom` section (new).** It is always placed **right after** `info`, whichever position it
  had in the source document, so that the member order of a document does not depend on where the
  section was written. In the condensed TSON dialect the writer does **not** emit a `custom` section
  (the `openapi-document-id` is written as `info.id`); the reader accepts and normalizes it.
- `info` (apart from `id`), `custom`, `tags`, `x-logo`, `contact` are **verbatim**: no condensation inside. Template
  strings (`${titleSuffix}`, `${!internal}`, `<<server-prod-url>>`, `##your company##`) are opaque
  strings; NoAPI variable resolution runs later on the desugared tree.
- `servers`: `url(<string> [attr: value …])` ≡ `{ url: <string>, …attrs }`. The attributes of a
  server (its `description`, …) go in the parentheses of the head; the object members that are not
  scalars (the `variables`) stay in a body. Plain server objects are accepted as well.
- `security`: see §1.1.

There is **no** required blank line between sections; the writer puts one between top-level sections
for readability. Top-level members may be written either with a head (`paths { … }`) or as pairs.

### 1.1 Security requirements

A `security` list is a list of *requirements*. Following OpenAPI semantics, the list elements are
**alternatives (OR)**, while the schemes combined inside one requirement are **all required (AND)**.

| Written | Meaning | Expansion |
|---|---|---|
| `JWTAuth` | one scheme | `{ "JWTAuth": [] }` |
| `OAuth(read write)` | one scheme with scopes | `{ "OAuth": ["read", "write"] }` |
| `all(ApiKeyAuth JWTAuth)` | **both** schemes in one requirement | `{ "ApiKeyAuth": [], "JWTAuth": [] }` |
| `all(ApiKeyAuth OAuth(read))` | AND with scopes | `{ "ApiKeyAuth": [], "OAuth": ["read"] }` |

```tson
security [ JWTAuth OAuth(read write) ]              # JWT  OR  OAuth
security [ all(ApiKeyAuth JWTAuth) ]                # ApiKey AND JWT
security [ all(ApiKeyAuth OAuth(read)) JWTAuth ]    # (ApiKey AND OAuth) OR JWT
security []                                         # no authentication (overrides the global list)
```

- `all` is reserved: a security scheme cannot be named `all` (`E_RESERVED_NAME`). `all()` with no
  member is an error (`E_BAD_SECURITY`). `all(X)` with a single member is read as `X`; the writer
  emits the bare form.
- Operation level: the `@security(...)` annotation or a `security [ … ]` member of the body, same
  grammar. **Absent = inherits the global list. `[]` (or `@security()`) = no authentication.**
- Writer: a requirement object with one key → bare form; with several keys → `all(...)`; an empty
  object `{}` (optional authentication) is emitted as a verbatim `{}` element.

---

## 2. Paths and operations

`paths { … }` contains **operations**, flattened: one element per `(method, path)`. Plain path items
(`"/x": { get { … } }`) may be mixed in and are read as-is.

```tson
paths {
  @enabled("${!internal}")
  @summary("Returns the deployed API version")
  GET("/version") {
    out(200): VersionResponse(json, description: "Returns the deployed API version information", example: { version: "1.4" })
  }

  @summary("Log in and obtain a token")
  POST("/login") {
    @required in: LoginModel(json, description: "Login information", example: { username: "customer" password: "1234" })
    out(200): LoginResponse(json, description: "Well formed token")
    out(400): ErrorType(json, description: "Bad credentials")
    out(429): TooManyRequests
  }

  @summary("Retrieves latest claim details, given a {policyNumber}")
  GET("/policy/{policyNumber}/latest-claim") {
    @path @required policyNumber: string(description: "Policy number" example: "E13508")
    out(200): GetLatestClaimResponse(json, description: "Claim details")
  }
}
```

**Head.** `METHOD(<path-string> [key: value …]) { body }`. `METHOD` ∈
`GET POST PUT DELETE PATCH HEAD OPTIONS` (upper case). The first positional argument is the path;
extra `key: value` pairs in the parentheses are operation-level OpenAPI keys.

**Operation annotations** (each on its own line before the head):

| Annotation | Expansion |
|---|---|
| `@summary("…")` | `summary` |
| `@description("…")` | `description` |
| `@operationId("…")` | `operationId` |
| `@tags("a" "b")` | `tags: ["a", "b"]` |
| `@deprecated` / `@deprecated(false)` | `deprecated: true` / `false` |
| `@enabled("…")` | NoAPI `enabled` extension |
| `@security(…)` | `security` (see §1.1) |

The same keys are also accepted as pairs in the head parentheses or in the body.

**Flattening.** A path with GET and POST is written as two operations. Path-level `summary`,
`description`, `parameters` and `enabled` from a JSON/YAML source are **copied into each operation**
by the canonicalizer (an operation's own value wins). When reading, operations with the same path are
grouped under one path item, in order of appearance.

**Operation body members (changed).** The body accepts the **whole Operation Object**:

| Member | Meaning |
|---|---|
| `[@required] in: Type(media… [, attrs])` | request body (the key `in` is reserved for it) |
| `requestBody: Type(media… [, attrs])` | request body, condensed form (see below) |
| `requestBody: { … }` | request body, plain OpenAPI form (verbatim) |
| `[@loc] [@required] name: Type(attrs)` | parameter named `name` |
| `out(status): Type(media… [, attrs])` | response |
| `out(status): void [, attrs]` | response without content |
| `out(status): ResponseName` | response, a reference to `components.responses` (see below) |
| `summary`, `description`, `operationId`, `tags`, `deprecated` | operation keys, verbatim |
| `security [ … ]` | the security requirements of the operation (§1.1) |
| `parameters [ … ]` | the parameters of the operation; the items are copied verbatim |
| `responses { … }` | the responses of the operation; the items are copied verbatim |
| `callbacks`, `servers`, `externalDocs` | operation keys, verbatim |
| anything else | **error** (`E_UNKNOWN_HEAD`): the body accepts only the Operation Object members listed above |

**Request body (changed).** The condensed request body is written `in`. The key `requestBody` is the
plain OpenAPI key, but it also accepts a condensed value (`requestBody: @required LoginModel(json)`);
both forms produce the same tree. Writing **both** `in:` and `requestBody:` in the same operation is
an error (`E_DUP_KEY`).

**The attributes of a request body or of a response (new).** They go in the parentheses of the head,
next to the media type: `in: LoginModel(json, description: "…", example: {…})`. The body form
(`in: LoginModel(json) { description: "…" }`) is still read and gives the same tree; the same
attribute in both places is an error (`E_DUPLICATE_ATTR`). The attributes are the `description`, the
`example`, the `examples` and the other keys of the Request Body / Response Object (`headers`,
`links`, …), which are passed through verbatim.

**Input location annotations.** `@path`, `@query`, `@header`, `@cookie`, `@param`, `@body`. `@param`
means `path` when `{name}` appears in the path template, otherwise `query`. The request body is the
default: the reserved key `in` is the body.

**Responses.** `out(200)`, `out("2XX")`, `out(default)`. The value is `Type(media…)` or `void`, with
the attributes of the response in the parentheses of the head (§2, *the attributes of a request body
or of a response*).

**Response references (new).** `out(500): InternalError` is a reference to the response
`InternalError` of `components.responses` (§5); it expands to
`{ "$ref": "#/components/responses/InternalError" }`. The name must be declared in
`components.responses`; a name that is not declared there is read as a schema reference (the usual
`out(500): ErrorResponse` behaviour). The writer emits the reference form when the response is a
`$ref` to `components.responses`.

**Media shorthands** (inside the parentheses of `in`/`out` types): `json`→`application/json`,
`xml`→`application/xml`, `text`→`text/plain`, `tson`→`application/tson`, `yaml`/`yml`→
`application/yaml`, `csv`→`text/csv`, `html`→`text/html`, `pdf`→`application/pdf`, `xls`,
`xlsx`, `doc`, `docx`, `zip`, `form`→`application/x-www-form-urlencoded`, `multipart`→
`multipart/form-data`, `binary`→`application/octet-stream`. A quoted string is a literal media type.
The writer condenses a request/response only when it has exactly one media type; otherwise it emits
the plain `requestBody` / `responses` form.

**Examples.** A body member `example <literal-or-object>` (written `example { … }` or
`example: <literal>`, both read identically) is the media `example`.

**Several examples (new).** A body member `examples { name { summary: … value { … } } }` is the media
`examples` map: each entry is an OpenAPI example object passed through verbatim (`summary`, `description`,
`value`, `externalValue`); `value` is data, so its strings are quoted. `example` and `examples` in
the same body are an error (`E_DUP_KEY`). The same applies to `in`.

**Other body keys (new).** The keys of an `out(...)` body other than `description`, `example` and
`examples` (for instance `headers`, `links`) are response-level keys and pass through verbatim; the
same holds for the request body (`headers`, …).

---

## 3. Type expressions

```
TypeExpr := primitive [ "(" attrs ")" ] [ "{" attrs "}" ]     string number integer boolean
          | "enum" "(" "values" ":" "[" value+ "]" [attrs] ")" [ "{" attrs "}" ]
          | "timestamp" [ "(" attrs ")" ] [ "{" attrs "}" ]
          | "datetime" [ "(" attrs ")" ] [ "{" attrs "}" ]
          | SchemaName [ "(" attrs ")" ]                     reference
          | "object" [ "(" attrs ")" ] [ "{" props "}" ]
          | "array" [ "(" attrs ")" ] "[" TypeExpr "]"       array(description: "d")[string]
          | "void"                                           responses only
```

- **Sugar.** `enum(values: [A B C])` ≡ `string(enum: ["A","B","C"])` (type `string`; values
  bare or quoted; at least one value). `timestamp` ≡ `string(format: date-time)`, and **`datetime` is an
  accepted alias of `timestamp`**. The writer emits the sugar when the schema is exactly
  `type: string` + `enum` (resp. `format: date-time`) plus other keywords, and plain `string(…)`
  otherwise. `enum`, `timestamp` and `datetime` are reserved names, like the primitives
  (`E_RESERVED_NAME` when declared as a schema name).
- **Attributes: the parentheses (changed).** The keywords of a type go in the **parentheses of its
  head**: `string(description: "…", minLength: 0)`, `enum(values: [A B], description: "…")`,
  `object(description: "…") { … }`. For every non-object type (primitives, `enum`, `timestamp`,
  `array`) they may also be written in a trailing `{ }` body, or in both: the two sets are merged and
  the same keyword in both is an error (`E_DUPLICATE_ATTR`). The body form exists for readability
  with long descriptions. For `object` the body is **not** keywords: it holds the properties (§4).
- **Arrays** use the TSON "Full Array" order (params before elements):
  `array(description: "d")[string]` ≡ `{ type: array, items: { type: string }, description: "d" }`.
  The bracket content is the *item type expression*; nested arrays nest.
- **attrs** are `key: value` pairs copied into the OpenAPI schema as keywords (`description`,
  `example`, `minLength`, `maxLength`, `enum`, `nullable`, `default`, `pattern`, `minimum`, `title`,
  `additionalProperties`, `x-*` …). Special cases:
  - `description` as an attribute ≡ the `@description("…")` annotation of the same construct.
  - `format` with a bare name on head `object`: `format: intermediate-structure` ≡
    `"object::intermediate-structure"`. A *quoted* string is always verbatim. On other heads a bare
    name is just the string: `string(format: date)` ≡ `"date"`.
  - `required: ["a"]` in `object(…)` lists *extra* required names that are not declared properties;
    it is merged after the `@required`-marked properties.
- In `in`/`out` position the parentheses hold **media shorthands** (the arguments of the head) and
  the attributes of the request body or of the response (§2).
- A **reference** `Token` expands to `{ "$ref": "#/components/schemas/Token" }`. A reference may carry
  attributes: `Token(description: "the tokens")` expands to
  `{ "$ref": "#/components/schemas/Token", "description": "the tokens" }` — they document the
  reference (the `description` and the `example` may also be given as annotations).

---

## 4. Schemas (`components.schemas`)

```tson
components {
  schemas {
    Token(description: "Token details") {
      @required value: string(minLength: 0 maxLength: 512 description: "Authorization Token")
    }

    LoginResponse(description: "Login response", example: { token: { value: "abc" } }) {
      @required token: Token
    }

    LoginModel(format: intermediate-structure, description: "Login information") {
      @required username: string(description: "User name")
    }

    Status: string(enum: ["NEW" "OPEN" "CLOSED"])

    @required(false) code: enum(values: [A B]) { description: "long text goes in the body" }

    @raw Raw: { allOf: [ { "$ref": "#/components/schemas/Token" } ] }
  }
}
```

- `Name { props }` ≡ `Name: object { props }` (implicit `object`): the **declaration** of an object
  schema has no `object` keyword, the name is the head.
- `Name: <TypeExpr>` with no object body declares a non-object schema (`Status` above).
- Schema keywords go in the **parentheses of the head** (`Name(description: "…") { … }`), or in the
  `@description("…")` annotation. The body of an object contains only properties and the members
  flagged `@metadata` (§4.1). This removes keyword/property collisions (real sources have properties
  named `title`).
- **Properties.** `[@required] name: TypeExpr(attrs)` (non-object property types may also carry a
  `{ }` body of keywords, §3).
  - Untyped property: a named object *without a type head* is a verbatim schema:
    `accidentDate { description: "…" format: "date" }` ≡ `accidentDate: { description, format }`
    (no `type` added).
  - Nested inline object: `address: object(description: "…") { street: string zip: string }`.
  - **Explicit `properties { … }` block.** A property literally named `example` or `properties` is
    declared inside an explicit `properties { … }` block. The block may also be used whenever the
    author wants to separate properties from the schema-level `example`; its content is exactly the
    property list. The writer emits it only when a property needs it. (The name `definition`, used in
    some hand-written drafts, is **not** part of the dialect.)
- **Body `example`** (named object or `example: <literal>`) is the schema-level `example`.
- `@required` ≡ the name is added to the schema's `required` list. `@required(false)` is an explicit
  false. The `required` array order is property order (canonicalization C2).
- **`@raw`** before a schema declaration: its body is a verbatim plain OpenAPI schema, no
  condensation inside. This is the fallback for schemas the writer cannot condense (`allOf`,
  `oneOf`, `anyOf`, `not`, `discriminator`, …).
- The declaration of an object schema may also be written with the `object` keyword
  (`LoginResponse: object(description: "…") { … }`); it is read identically.

### 4.1 Schema metadata (`@metadata`, new)

The body of an **object** schema holds its *properties*. A member that is a schema keyword rather
than a property is flagged `@metadata`:

```tson
LoginResponse: object {
  @metadata description: "response for login"
  @metadata example: {
    "success": true
  }
  @required success: boolean
}
```

`@metadata description`, `@metadata example` (and any other keyword) become schema keywords:
`description` and `example` have their fixed place in the schema, the other keywords keep the order
in which they are written. A member of an object body that is **not** flagged is always a property,
so a property named `description` or `example` needs the explicit `properties { … }` block (§4). In
a **non-object** schema the body holds keywords directly (§3), so `@metadata` is not needed there.

On a schema *declaration*, the annotations `@description("…")` and `@example(…)` set the schema
`description` and the schema `example`; the same annotations work on a property. The example is
usually an object: `@example({…})`. The form `@example{…}` (without parentheses) is **not** valid
condensed TSON — it is read as an unnamed member and reported as `E_SYNTAX`.

---

## 5. Other `components` entries

```tson
components {
  securitySchemes {
    JWTAuth: http(bearer, bearerFormat: JWT, description: "…")
    api_key: apiKey(header: X-API-KEY, description: "…")
  }

  headers {
    @required(false) X-MYAPP-API-VERSION: string(description: "The request API version")
  }

  responses {
    InternalError: ErrorResponse(json, description: "Unexpected server error", example: { success: false error: "InternalError" })
    TooManyRequests: ErrorResponse(json, description: "Too many requests")
  }
}
```

- `securitySchemes`: `Name: http(<scheme> [, attrs])`, `Name: apiKey(header|query|cookie: <name> [, attrs])`,
  `Name: openIdConnect(url: "…" [, attrs])`, `Name: oauth2 { flows { … } }`; the attributes of the
  scheme (`description`, `bearerFormat`, …) go in the parentheses of the head, the members that are
  not scalars (the `flows`) stay in a body. An entry without a head (`Name { type: http … }`) is read
  as plain.
- **`apiKey` arguments.** Exactly one pair whose key is `header`, `query` or `cookie` and
  whose value is the parameter name. The form `apiKey(name: … in: …)` is plain OpenAPI vocabulary and
  is rejected in condensed position (`E_APIKEY_ARGS`, with a hint); write the plain form
  `Name { type: apiKey name: … in: header }` if needed.
- `headers`: `[@required[(bool)]] Name: Type(attrs)` ≡ `{ required, description, schema: { … } }`.
- **`components.responses` (new).** Each member is a Response Object, written like the response of
  an operation: `Name: Type(media… [, attrs])`. The names declared here are the ones that an
  operation may reference with `out(status): Name` (§2, *response references*). A member that is a
  plain Response Object (`Name { description: "…" content: { … } }`) is read verbatim.
- `components.parameters` and all other component sections: plain, verbatim.

---

## 6. Annotation catalogue

| Annotation | Applies to | Args | Meaning |
|---|---|---|---|
| `@openapi` | document | version string | `openapi` |
| `@summary` `@description` `@operationId` `@tags` `@deprecated` `@enabled` `@security` | operation | see §2 | operation keys |
| `@description` | schema declaration, property | string | schema / property `description` |
| `@example` | schema declaration, property | one value (an object) | schema / property `example` |
| `@metadata` | member of an object body | none | the member is a schema keyword, not a property (§4.1) |
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
| `url` | `servers` | string, then optional attributes |
| `http` | `securitySchemes` | scheme name, then optional attributes |
| `apiKey` | `securitySchemes` | one pair `header\|query\|cookie: <name>`, then optional attributes |
| `openIdConnect` | `securitySchemes` | pair `url: "…"`, then optional attributes |
| `string number integer boolean object timestamp datetime` | schemas, properties, parameters, headers | pairs (attrs) |
| `enum` | same | pair `values: [...]` plus attrs |
| `array` | same | pairs (attrs), then `[ item type ]` |
| `all` | inside a `security` list | one or more security elements |
| `<SchemaName>` | `in`/`out`, properties | media shorthands (`in`/`out`) or pairs (properties) |
| `<SchemaName>` | `components.schemas` | the declaration of an object schema: the attributes in the parentheses, the properties in the body |
| `<ResponseName>` | `components.responses` | the media types and the attributes of the response |
| `<SecurityName>` | `security` lists | scope names |

Reserved names (cannot be used as schema names): the primitives, `object`, `array`, `void`, `enum`,
`timestamp`, `datetime`. `all` is reserved for security scheme names.

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
  company-api-key { value: "…" observations: "rotate yearly" }
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
- Whitespace separators only (§0); quoting policy of §0.
- **The attributes of a construct go in the parentheses of its head** (revision 4): the description,
  the example and the other keywords of a schema, of a request body, of a response, of a response
  component, of a security scheme and of a server. The body is only used for the members that cannot
  be written inline (the properties of an object, the `flows` of an `oauth2` scheme, the `variables`
  of a server).
- **An example is written on a single line** (`example: { success: true apiVersion: "1.0.0" }`): the
  members of an object and the items of an array are separated by a space, and the strings stay
  quoted.
- Operation annotations on their own lines above the head; `@required`, location annotations and
  `@required(false)` stay inline with the member. An operation key is written as an annotation **only
  when it precedes all the body members**, so that the order of the members of the operation is
  preserved.
- Keys: unquoted if a bare token (§0), otherwise quoted. Paths are always quoted.
- Strings are written as double quoted TSON strings: a `"` is written **twice** (`""`) and a line
  break is kept as it is (TSON strings have no backslash escape). This round-trips reliably,
  including the `¶`, `${…}` / `<<…>>` / `##…##` template markers.
- Numbers (lexical form preserved), booleans, `null`. A temporal literal is written as a literal,
  except a time, which is quoted (§0).
- An empty section is not written: an empty `custom` section, an empty `responses` object (written
  `responses: {}`) and the empty body of a request body are omitted.
- **Long descriptions.** When the description of a primitive, an `enum` or a `timestamp` is longer
  than 60 characters, the description (and the attributes that follow it) move to the body of the
  head, so that the JSON key order is preserved:
  `code: enum(values: [A B]) { description: "a long description…" }`. The description of an **object**
  always stays in the parentheses of its head, because the body of an object holds its properties.
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
- **C8** the document id is stored in `custom.openapi-document-id`: an `info.id` member is moved there
  (and removed from the info), and the `custom` section is placed right after the `info`.
- **C9** the members of an `apiKey` security scheme are ordered `type`, `name`, `in` (the other
  members keep their order).

Everything else must be identical, including key order of `properties`, `paths`, `schemas`,
`changes`, `responses`, and array order. The sugar of §3 (`enum`, `timestamp`) and the security
groups of §1.1 expand to the same trees as their plain forms, so they need no canonicalization rule.

---

## 11. Validation of the examples

An example is part of the contract of an API: a customer copies it and expects the server to accept
it. NoAPI therefore checks **every example of a document against the schema it documents**, when the
document is read (the `convert` command, and the generation of the documentation). **(new)**

The following constraints are checked:

| Constraint | Checked on |
|---|---|
| `type` | every example |
| `enum` | every example |
| `minLength`, `maxLength` | the string examples |
| `pattern` | the string examples (the pattern is not anchored, as in JSON Schema) |
| `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf` | the numeric examples |
| `required` | the object examples (every required property must be present) |
| `nullable` | every example (a `null` example needs `nullable: true`) |
| `format: date`, `format: date-time` | the string examples |
| the properties and the items | recursively, on the values of the example |
| `allOf`, `anyOf`, `oneOf` | the example must match every `allOf` branch, at least one `anyOf` branch, and exactly one `oneOf` branch |

The other `format` values are not checked: in OpenAPI 3.0 they are annotations, not constraints.

A violation is reported as one `E_EXAMPLE_MISMATCH` diagnostic per example, whose message says what
was found and what the schema requires:

```
error E_EXAMPLE_MISMATCH at $.components.schemas.Thing.properties.name.example:
the example of $.components.schemas.Thing.properties.name.example is a number (12),
but the schema requires a string
```

The pass is read-only: it never modifies the document. It applies to the JSON and the YAML
documents as well as to the condensed and the plain TSON ones, and it is not applied to the
document that the writer produces (the writer does not change any example).

---

## 12. Diagnostics

The reader and the writer report problems with a code and a message that says what was found and
what to write instead. `E_` codes are errors (the document is not usable), `W_` codes are warnings
(the document is read with a default).

| Code | Meaning |
|---|---|
| `E_SYNTAX` | the text is not valid condensed TSON (for instance `@example{…}` without parentheses, or an unnamed member) |
| `E_UNKNOWN_ANNOTATION` | the annotation is not known, or not allowed on this construct |
| `E_UNKNOWN_HEAD` | the head is not known in this position |
| `E_BAD_ARGS` | the arguments of a head are invalid |
| `E_BAD_SECURITY` | an invalid security requirement (for instance an empty `all()`) |
| `E_APIKEY_ARGS` | `apiKey` does not take exactly one `header\|query\|cookie: <name>` argument |
| `E_DUP_KEY` | the same key is used twice in one construct (`in` and `requestBody`, `example` and `examples`, …) |
| `E_DUP_OPERATION` | the same method is declared twice for one path |
| `E_DUPLICATE_ATTR` | the same schema keyword is given twice (in the parentheses and in the body) |
| `E_DUPLICATE_ID` | `info.id` and `custom.openapi-document-id` are both present |
| `E_RESERVED_NAME` | a schema or security scheme uses a reserved name (§7) |
| `E_UNRESOLVED_REF` | the referenced schema is not declared in `components.schemas` |
| `E_MISSING_LOCATION` | a parameter has no location (`@path`, `@query`, …) and none can be inferred |
| `E_VOID_OUTSIDE_RESPONSE` | `void` is used outside a response |
| `E_EXAMPLE_MISMATCH` | an example does not follow the constraints of its schema (§11) |
| `W_DUP_KEY` | a key appears twice; the last occurrence is kept |
| `W_NO_DESCRIPTION` | a response has no description; the reason phrase of the status is used |

---

## Deviations and limitations

- **String escaping.** The original specification asks for literal-first strings with no backslash
  escapes (`¶¶` for newlines, quote-run fences for `"`). The writer emits double quoted strings
  instead: a `"` is written twice (`""`) and a line break is kept literally. This round-trips
  correctly and is a cosmetic deviation only; the reader accepts the `¶` and `¶¶` literals.
- **Temporal literals lose their seconds.** *Known issue of the Nuts parser, to be fixed upstream:
  it reads `10:15:00` as `10:15` and `2024-07-16T10:15:00` as `2024-07-16T10:15`.* A time is
  therefore written quoted by the writer (a bare time cannot be parsed at all, because of the colon);
  a date-time keeps the value it was read with.
- **Writer granularity of fallback.** When a construct cannot be condensed, the whole document is
  re-emitted as plain TSON (not per-node). The result is always lossless and self-verified, but a
  single unsupported construct causes the whole file to be plain.
- **`changes` entries** are emitted as plain objects rather than a dedicated condensed form. This is
  valid condensed TSON and round-trips.
- **Quoted names are never used as heads.** The Nuts TSON parser cannot parse a quoted name followed
  by a body or parameter list: `"company-api-key" { … }` is read as a bare string plus an unnamed
  object. The writer therefore emits `key: value` (pair form) whenever a name needs quoting, e.g.
  `"company-api-key": { value: "abc" }`, and reserves the head form (`name { … }`) for bare
  identifier names.
- **Media types other than `application/json`** are emitted with a quoted literal media type when a
  shorthand does not exist.
- **`$ref` siblings.** OpenAPI 3.0 ignores keywords next to `$ref`; the attributes of a reference are
  kept next to it (they are read and written back) but have no effect for 3.0 consumers.

---

## Changelog

### Revision 3 → 4

1. §0: the arguments of a head are split into the **arguments** (positional: the media type, the
   security scheme, the parameter name, the server url) and the **attributes** (named: the
   description, the example, the other OpenAPI keys).
2. §1: the attributes of a server go in the parentheses of `url(…)`.
3. §2: the attributes of a request body and of a response go in the parentheses of the head
   (`in: T(json, description: "…")`); the body form is still read and gives the same tree.
4. §2: the **response references** `out(status): ResponseName`, pointing to a response of
   `components.responses`.
5. §3: the attributes of a type go in the parentheses of the head; the body form of the non-object
   types is still read.
6. §4: the declaration of an object schema keeps its name as the head
   (`LoginRequest(description: "…") { … }`); the `object` keyword is optional and no longer written.
7. §5: the attributes of a security scheme go in the parentheses of its head; the section
   `components.responses` is written in the condensed form.
8. §9: the writer emits the attributes in the parentheses of the heads and writes the examples on a
   single line.

### Revision 2 → 3

1. §11 added: the validation of the examples — every example of a document is checked against the
   schema it documents, and each violation is reported as `E_EXAMPLE_MISMATCH`.
2. §12: the new diagnostic code added to the table.

### Revision 1 → 2

1. §0 added: the line string `¶` and the block string `¶¶`, the temporal literals and the
   duplicate-key rule (last occurrence wins, `W_DUP_KEY`).
2. §0 corrected: the writer quotes the strings itself (a `"` is doubled, a line break is kept
   literal) instead of delegating to the plain-TSON writer.
3. §1: `info.id` ≡ `custom.openapi-document-id`; the `custom` section is always placed right after
   the `info`; `custom.description` documented as redundant.
4. §1.1: the security groups `all(...)`, the empty list = no auth, absent = inherit; `all` is
   reserved.
5. §2: the operation body accepts the whole Operation Object; the condensed `requestBody` is
   accepted (only `in` **and** `requestBody` together is an error); the `examples` map and the
   other response/request-body keys (`headers`, `links`, …) are supported.
6. §3: the `enum(values: [...])` and `timestamp` sugar, `datetime` as an alias of `timestamp`, and
   the keywords of the non-object types accepted in the parentheses or in the body.
7. §4: the explicit `properties { }` block; the `@metadata` members of an object body; the
   `@description` and `@example` annotations of a schema declaration.
8. §5: `apiKey` takes only `header|query|cookie: <name>`; the `name:`/`in:` form is rejected with a
   hint.
9. §7: the head catalogue and the reserved-name list updated (`datetime`).
10. §9: the writer rules completed (the long descriptions, the empty sections, the annotation order).
11. §10: C8 (the document id) and C9 (the order of the members of an `apiKey` scheme) added.
12. §11 added: the table of the diagnostic codes.
