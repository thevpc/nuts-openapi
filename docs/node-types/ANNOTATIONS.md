# Annotations Reference

## Document/Operation Annotations
| Annotation | Usage | Example |
|------------|-------|---------|
| `@openapi` | Document first element | `@openapi("3.0.0")` |
| `@summary` | Operation | `@summary("Get user by ID")` |
| `@description` | Operation, schema, property | `@description("User details")` |
| `@operationId` | Operation | `@operationId("getUser")` |
| `@tags` | Operation | `@tags("Users" "Admin")` |
| `@deprecated` | Operation | `@deprecated` or `@deprecated(false)` |
| `@enabled` | Operation/path | `@enabled("${feature.enabled}")` |
| `@security` | Operation | `@security(JWTAuth)` or `@security()` |

## Schema/Property Annotations
| Annotation | Usage | Example |
|------------|-------|---------|
| `@description` | Schema declaration, property | `@description("...")` |
| `@example` | Schema declaration, property | `@example({ id: "123" })` |
| `@metadata` | Object body member | `@metadata example: {...}` |
| `@required` | Property, parameter, in, header | `@required` or `@required(false)` |
| `@raw` | Schema declaration | `@raw` before schema |

## Parameter Location Annotations
| Annotation | Usage | Example |
|------------|-------|---------|
| `@path` | Parameter | `@path @required id: string` |
| `@query` | Parameter | `@query limit: integer` |
| `@header` | Parameter | `@header Authorization: string` |
| `@cookie` | Parameter | `@cookie session: string` |
| `@param` | Parameter (auto-detect) | `@param id: string` |
| `@body` | Parameter (default) | `@body data: object` |

## Annotation Rules
- Unknown annotation → `E_UNKNOWN_ANNOTATION`
- Annotation on wrong construct → `E_UNKNOWN_ANNOTATION`
- `@example` must have parentheses: `@example({...})` not `@example{...}`
- `@description` and attribute `description:` are equivalent
- `@required` inline stays inline; operation keys as annotations written on own lines
