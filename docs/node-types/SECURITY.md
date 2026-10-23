# Security Node Types

## Global Security
```tson
security [
  ApiKeyAuth,
  OAuth2(read write)
]
```

## Operation Security
```tson
POST("/login") {
  @security()  # no auth required
  ...
}

GET("/secure") {
  security [ JWTAuth ]
  ...
}
```

## Security Requirements Syntax
```tson
# Single scheme
[ ApiKeyAuth ]

# Single scheme with scopes
[ OAuth2(read write admin) ]

# AND - both schemes required
[ all(ApiKeyAuth JWTAuth) ]

# AND with scopes
[ all(OAuth2(read) ApiKeyAuth) ]

# OR alternatives
[ all(ApiKeyAuth JWTAuth), OAuth2(read) ]
# means (ApiKeyAuth AND JWTAuth) OR OAuth2 with read scope
```

## Security Schemes

### API Key
```tson
ApiKeyAuth: apiKey(header: X-API-Key, description: "API key")
ApiKeyQuery: apiKey(query: api_key)
ApiKeyCookie: apiKey(cookie: session)
```

### HTTP
```tson
BasicAuth: http(basic)
BearerAuth: http(bearer, bearerFormat: JWT)
BearerAuth: http(bearer, bearerFormat: "JWT", description: "...")
```

### OAuth2
```tson
OAuth2: oauth2 {
  description: "OAuth2 flows"
  flows {
    implicit {
      authorizationUrl: "https://example.com/oauth/authorize"
      refreshUrl: "https://example.com/oauth/refresh"
      scopes {
        read: "Read access"
        write: "Write access"
      }
    }
    password { ... }
    clientCredentials { ... }
    authorizationCode { ... }
  }
}
```

### OpenID Connect
```tson
OpenID: openIdConnect(url: "https://example.com/.well-known/openid-configuration", description: "OpenID Connect")
```
