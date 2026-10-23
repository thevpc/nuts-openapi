# Operation Node Types

## Operation Declaration
```tson
METHOD("/path/template") {
  ...
}
```
Methods: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE

## Operation Head Attributes
```tson
GET("/users", operationId: "listUsers", tags: ["Users"]) {
  ...
}
```
Or use annotations before head.

## Operation Body Members
```tson
GET("/example") {
  # Parameters
  @path @required id: string
  @query limit: integer(default: 10)
  @header Authorization: string
  @cookie session: string
  
  # Request body
  @required in: RequestData(json, description: "...", example: {...})
  # or
  requestBody: @required RequestData(json)
  
  # Responses
  out(200): ResponseData(json, description: "OK")
  out(400): ErrorResponse(json)
  out(404): NotFound
  out(500): InternalError
  out(default): ErrorResponse
  
  # Other operation fields
  summary: "..."
  description: "..."
  operationId: "..."
  tags: ["..."]
  deprecated: true
  security [ ... ]
  parameters [ ... ]
  responses { ... }  # verbatim
  callbacks { ... }
  servers [ ... ]
  externalDocs { ... }
}
```

## Path Parameters in Template
```tson
GET("/users/{userId}/posts/{postId}") {
  @path @required userId: string
  @path @required postId: string
  out(200): PostList
}
```

## Security Override
```tson
GET("/public/health") {
  @security()
  out(200): HealthStatus
}

GET("/admin/users") {
  security [ all(ApiKeyAuth AdminRole) ]
  out(200): UserList
}
```
