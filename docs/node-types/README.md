# OpenAPI TSON Node Type Reference

This document provides a complete reference for all node types in the NoAPI TSON dialect. Each node type is documented with its syntax, semantics, and examples.

## Table of Contents

- [Document Structure](#document-structure)
- [Info Object](#info-object)
- [Servers](#servers)
- [Tags](#tags)
- [Paths and Operations](#paths-and-operations)
- [Parameters](#parameters)
- [Request Body](#request-body)
- [Responses](#responses)
- [Components](#components)
- [Schemas](#schemas)
- [Type Expressions](#type-expressions)
- [Security](#security)
- [Annotations](#annotations)
- [Common Constructs](#common-constructs)

## Document Structure

### API Document
```tson
@openapi("3.0.0")

info { ... }
custom { ... }
servers [ ... ]
paths { ... }
components { ... }
tags [ ... ]
security [ ... ]
```

- `@openapi("3.0.0")` - OpenAPI version (must be first element)
- Document kind detected from root key: `@openapi` or `openapi` → API document; `openapi-config` → config document

## Info Object

```tson
info {
  id: my-api                    # becomes custom.openapi-document-id
  title: "My API"
  version: "1.0.0"
  description: "..."
  contact { ... }
  license { ... }
  termsOfService: "..."
  changes [ ... ]
}
```

**Notes:**
- `id` is moved to `custom.openapi-document-id` (cannot coexist with explicit `custom.openapi-document-id`) and is the canonical form in condensed TSON (the `custom` section is not written by the writer)
- `contact`, `license`, `changes` passed through verbatim
- Template strings `${...}`, `<<...>>`, `##...##` are opaque

## Servers

**Syntax (revision 4):**
```tson
servers [
  url("https://example.com", description: "Main server")
  url("https://api.example.com") {
    description: "API server"
    variables {
      version {
        default: "v1"
        enum: ["v1", "v2"]
      }
    }
  }
]
```

**Rules:**
- URL is positional argument: `url("...")`
- Attributes go in parentheses: `description`, etc.
- Non-scalar members (`variables`) go in body
- Plain server objects also accepted

## Paths and Operations

**Syntax:**
```tson
paths {
  @summary("...")
  @description("...")
  @operationId("...")
  @tags("tag1" "tag2")
  @deprecated
  @enabled("${condition}")
  @security(JWTAuth)
  GET("/users/{id}", parameters: [...]) {
    ...
  }
  
  POST("/users/{userId}") {
    @path @required userId: string
    @query @required limit: integer(default: 10)
    @header X-Request-ID: string
    @required in: UserRequest(json)
    out(200): User(json, description: "OK")
    out(404): ErrorResponse(json)
    out(default): ErrorResponse
  }
}
```

**HTTP Methods:** `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`, `TRACE`

**Annotations:**
| Annotation | Meaning |
|------------|---------|
| `@summary` | Operation summary |
| `@description` | Operation description |
| `@operationId` | Operation ID |
| `@tags` | Tags array |
| `@deprecated` / `@deprecated(false)` | Deprecated flag |
| `@enabled` | NoAPI enabled extension |
| `@security` | Security requirements |

**Location Annotations:**
| Annotation | Meaning |
|------------|---------|
| `@path` | Path parameter |
| `@query` | Query parameter |
| `@header` | Header parameter |
| `@cookie` | Cookie parameter |
| `@param` | Auto-detect (path if in template, else query) |
| `@body` | Body parameter (default) |

**Path-level inheritance:** `summary`, `description`, `parameters`, `enabled` copied to operations by canonicalizer (operation values win)

## Request Body

**Syntax (revision 4):**
```tson
# Using 'in' key (reserved)
@required in: UserRequest(json, description: "User data", example: { name: "John" })

# Using 'requestBody' key
requestBody: @required UserRequest(json, description: "User data")

# With extra members
in: UserRequest(json, description: "Upload") {
  required: true
  headers {
    X-File-Name: string
  }
}
```

**Rules:**
- `in` is reserved for request body
- `requestBody` accepted as alias
- Cannot use both `in` and `requestBody` in same operation
- Attributes in head: `description`, `example`, `examples`
- Media types as positional args: `json`, `xml`, `form`, etc.
- `@required` annotation or `required: true`

## Responses

**Syntax (revision 4):**
```tson
# Response with content
out(200): User(json, description: "OK", example: { id: "123" })
out(201): User(json, description: "Created")
out(400): ErrorResponse(json, description: "Bad Request")
out(204): void(description: "No content")

# Response reference to components.responses
out(401): Unauthorized
out(500): InternalError
out(429): TooManyRequests

# With extra members
out(200): User(json, description: "OK") {
  headers {
    X-Rate-Limit: integer
  }
  links {
    next: { operationId: "listUsers" }
  }
}

# Wildcard status
out("2XX"): User(json)
out(default): ErrorResponse(json)
```

**Rules:**
- Status: number, `"2XX"`, or `default`
- Response reference: `out(500): ResponseName` where `ResponseName` declared in `components.responses`
- Attributes in head: `description`, `example`, `examples`
- Extra members (`headers`, `links`) go in body
- If no description, writer uses reason phrase and adds `W_NO_DESCRIPTION`

## Components

```tson
components {
  schemas { ... }
  responses { ... }
  parameters { ... }
  examples { ... }
  requestBodies { ... }
  headers { ... }
  securitySchemes { ... }
  links { ... }
  callbacks { ... }
}
```

### components.schemas
See [Schemas](#schemas) section.

### components.responses (revision 4)
```tson
components {
  responses {
    InternalError: ErrorResponse(json, description: "Unexpected server error", example: {...})
    Unauthorized: ErrorResponse(json, description: "Unauthorized")
    TooManyRequests: ErrorResponse(json, description: "Too many requests")
  }
}
```

### components.headers
```tson
headers {
  @required X-API-Key: string(description: "API key")
  @required(false) X-Request-ID: string(description: "Request ID")
}
```

### components.securitySchemes (revision 4)
```tson
securitySchemes {
  ApiKeyAuth: apiKey(header: X-API-Key, description: "API key in header")
  ApiKeyQuery: apiKey(query: api_key, description: "API key in query")
  JWTAuth: http(bearer, bearerFormat: JWT, description: "JWT token")
  BasicAuth: http(basic)
  OAuth2: oauth2 {
    flows {
      implicit {
        authorizationUrl: "https://..."
        scopes {
          read: "Read access"
        }
      }
    }
  }
  OpenID: openIdConnect(url: "https://example.com/.well-known/openid-configuration", description: "OIDC")
}
```

## Schemas

**Object schema declaration (revision 4):**
```tson
# New form (no object keyword)
User(description: "User object", example: { id: "123", name: "John" }) {
  @required id: string(description: "User ID")
  @required name: string(description: "Full name")
  email: string(format: email, description: "Email address")
  age: integer(minimum: 0, maximum: 120)
  role: Role
  tags: array[string]
  address: Address
}

# Old form still accepted
User: object(description: "User object") {
  ...
}
```

**Non-object schema:**
```tson
Status: string(enum: ["ACTIVE", "INACTIVE", "PENDING"])
UserId: string(format: uuid)
Timestamp: timestamp(description: "ISO datetime")
CreatedAt: datetime
```

**Properties:**
```tson
User {
  @required id: string
  name: string(description: "Full name")
  status: Status
  metadata: object {
    key: string
  }
  items: array(Item)
  # Property named like keyword needs explicit block
  properties {
    properties: string  # actual property
  }
}
```

**Annotations:**
- `@required` / `@required(true)` - add to required array
- `@required(false)` - explicit false
- `@description("...")` - schema/property description
- `@example({...})` - schema/property example
- `@metadata` - mark member as schema keyword in object body

**Schema metadata in object body:**
```tson
User: object {
  @metadata description: "User object"
  @metadata example: { id: "123" }
  @metadata additionalProperties: false
  @required id: string
}
```

## Type Expressions

### Primitives
```tson
string
string(description: "Name", minLength: 1, maxLength: 100, pattern: "^[a-z]+$")
number
number(minimum: 0, maximum: 100)
integer
integer(format: int64)
boolean
```

### Enum (sugar)
```tson
enum(values: [RED GREEN BLUE], description: "Color")
enum(values: ["active", "inactive"])
Status: string(enum: ["NEW", "OPEN", "CLOSED"])  # plain form also works
```

### Timestamp/DateTime (sugar)
```tson
timestamp(description: "Created at")
datetime
createdAt: timestamp(example: "2024-01-01T00:00:00Z")
```

### Array
```tson
array[string]
array(description: "List of users")[User]
array(minItems: 1, maxItems: 10)[string]
```

### Object
```tson
object(description: "Inline object") {
  name: string
  value: integer
}

object(format: intermediate-structure) {
  ...
}
```

### Reference
```tson
User
User(description: "Referenced user")
User(description: "User data", example: {...})
```

### Void (responses only)
```tson
out(204): void
out(204): void(description: "No content")
```

### Attributes
- Keywords in head parens: `string(description: "...", minLength: 5)`
- For non-object types can also use body: `string { description: "..." }`
- Same keyword in both places = error
- `format: intermediate-structure` on object head = `object::intermediate-structure`
- `required: ["extra"]` on object lists extra required names

## Security

### Security Requirements
```tson
security [
  ApiKeyAuth                    # OR - one scheme
  OAuth(read write)             # one scheme with scopes
  all(ApiKeyAuth JWTAuth)       # AND - both required
  all(ApiKeyAuth OAuth(admin))  # AND with scopes
  JWTAuth                       # another alternative
]

security []  # no auth
```

**Rules:**
- List elements are alternatives (OR)
- Schemes in `all()` required together (AND)
- `all(X)` with single member read as `X`, writer emits bare form
- `all()` empty = error
- `all` reserved name

### Operation-level security
```tson
POST("/public") {
  @security()  # no auth
  ...
}

POST("/secure") {
  security [ all(ApiKeyAuth JWTAuth) ]
  ...
}
```

Absent = inherit global; `[]` or `@security()` = no auth

## Annotations

| Annotation | Applies To | Args | Meaning |
|------------|------------|------|---------|
| `@openapi` | document | version string | OpenAPI version |
| `@summary` | operation | string | Summary |
| `@description` | operation, schema, property | string | Description |
| `@operationId` | operation | string | Operation ID |
| `@tags` | operation | strings... | Tags array |
| `@deprecated` | operation | optional bool | Deprecated flag |
| `@enabled` | operation, path | string/expression | NoAPI enabled |
| `@security` | operation | security requirements | Security requirements |
| `@example` | schema, property | object | Example value |
| `@metadata` | object schema member | none | Member is schema keyword |
| `@required` | property, parameter, in, header | optional bool (default true) | Required flag |
| `@path` | parameter | none | Path location |
| `@query` | parameter | none | Query location |
| `@header` | parameter | none | Header location |
| `@cookie` | parameter | none | Cookie location |
| `@param` | parameter | none | Auto-detect location |
| `@body` | parameter | none | Body location |
| `@raw` | schema declaration | none | Verbatim schema (no condensation, equivalent to plain json/yaml) |

Unknown annotations = error `E_UNKNOWN_ANNOTATION`

## Common Constructs

### Media Type Shorthands
| Shorthand | Media Type |
|-----------|------------|
| `json` | `application/json` |
| `xml` | `application/xml` |
| `text` | `text/plain` |
| `yaml`/`yml` | `application/yaml` |
| `form` | `application/x-www-form-urlencoded` |
| `multipart` | `multipart/form-data` |
| `csv` | `text/csv` |
| `html` | `text/html` |
| `pdf` | `application/pdf` |
| `binary` | `application/octet-stream` |

### String Literals
- Line strings: `¶ text` (up to end of line)
- Block strings: `¶¶ text` on consecutive lines
- Double-quoted strings: `"text with ""quotes"" "`
- Bare tokens (when allowed): `my-api`, `X-API-Key`

### Temporal Literals
```tson
date: 2024-07-16
datetime: 2024-07-16T10:15:00Z
local: 2024-07-16T10:15:00
time: "10:15:00"  # quoted (parser limitation)
```

### Examples
```tson
example: { name: "John" age: 30 }  // inline
example {
  name: "John"
  age: 30
}
examples {
  example1 {
    summary: "First example"
    value { name: "John" }
  }
}
```
