# Components Node Types

## Structure
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

## components.responses (revision 4)
```tson
components {
  responses {
    InternalError: ErrorResponse(json, description: "Internal server error", example: { success: false, error: "InternalError" })
    Unauthorized: ErrorResponse(json, description: "Unauthorized")
    NotFound: ErrorResponse(json, description: "Not found")
    BadRequest: ErrorResponse(json, description: "Bad request")
  }
}
```

Usage:
```tson
out(500): InternalError
out(401): Unauthorized
```

## components.parameters
```tson
parameters {
  pageParam: {
    name: "page"
    in: "query"
    schema { type: integer default: 1 }
  }
  limitParam: queryParam(page: integer(default: 10))
}
```

## components.headers
```tson
headers {
  @required X-API-Version: string(description: "API version")
  X-Rate-Limit: integer(description: "Rate limit")
}
```

## components.examples
```tson
examples {
  userExample {
    summary: "Example user"
    value {
      id: "123"
      name: "John"
    }
  }
}
```

## components.requestBodies
```tson
requestBodies {
  UserBody: {
    required: true
    content {
      application/json {
        schema { $ref: "#/components/schemas/User" }
      }
    }
  }
}
```

## components.links
```tson
links {
  GetUserById {
    operationId: getUser
    parameters {
      userId: "$response.body#/id"
    }
  }
}
```

## components.callbacks
```tson
callbacks {
  onEvent {
    '{$request.body#/callbackUrl}' {
      post {
        requestBody { ... }
        responses { ... }
      }
    }
  }
}
```
