# Document Structure Node Types

## Root Elements

### @openapi Annotation
```tson
@openapi("3.0.0")
```
- Must be first element of API document
- Expands to `openapi: "3.0.0"`
- Also accepted as plain pair

### info
```tson
info {
  id: my-api
  title: "My Application API"
  version: "1.0.0"
  description: "API description"
  termsOfService: "https://example.com/terms"
  contact {
    name: "Support"
    email: "support@example.com"
    url: "https://example.com"
  }
  license {
    name: "Apache 2.0"
    url: "https://www.apache.org/licenses/LICENSE-2.0.html"
  }
  changes [
    {
      date: "2024-01-01"
      version: "1.0.0"
      title: "Initial release"
      observations: "..."
      details: ["ADDED: /users"]
    }
  ]
}
```

### custom
```tson
custom {
  openapi-document-id: my-api  # set automatically from info.id
  config: { ... }
}
```
- Always placed right after info
- Omitted when empty
- `custom.description` redundant if same as info.description

### servers
See [Servers](README.md#servers)

### tags
```tson
tags [
  { name: "Users", description: "User operations" }
  { name: "Orders", description: "Order operations" }
]
```

### paths
See [Paths and Operations](README.md#paths-and-operations)

### components
See [Components](README.md#components)

### security
See [Security](README.md#security)
