# Diagnostics Reference

## Errors (E_*) - Document not usable
| Code | Meaning | Example |
|------|---------|---------|
| `E_SYNTAX` | Invalid OpenAPI TSON syntax | `@example{...}` without parens |
| `E_UNKNOWN_ANNOTATION` | Unknown or misplaced annotation | `@foo` on wrong construct |
| `E_UNKNOWN_HEAD` | Unknown head in position | Invalid construct |
| `E_BAD_ARGS` | Invalid head arguments | Wrong args for head |
| `E_BAD_SECURITY` | Invalid security requirement | `all()` empty |
| `E_APIKEY_ARGS` | Wrong apiKey form | Use `apiKey(header: X)` not `apiKey(name: ..., in: ...)` |
| `E_DUP_KEY` | Duplicate key in construct | Both `in:` and `requestBody:` |
| `E_DUP_OPERATION` | Duplicate method for same path | Two GET /users |
| `E_DUPLICATE_ATTR` | Same keyword twice | In parens and body |
| `E_DUPLICATE_ID` | Both info.id and custom.openapi-document-id | Conflict |
| `E_RESERVED_NAME` | Reserved name used | Schema named `string`, `all` as scheme |
| `E_UNRESOLVED_REF` | Referenced schema not declared | Missing schema |
| `E_MISSING_LOCATION` | Parameter missing location | Can't infer location |
| `E_VOID_OUTSIDE_RESPONSE` | void used outside response | Invalid usage |
| `E_EXAMPLE_MISMATCH` | Example doesn't match schema | Type/constraint violation |

## Warnings (W_*) - Document read with default
| Code | Meaning |
|------|---------|
| `W_DUP_KEY` | Duplicate key, last occurrence kept |
| `W_NO_DESCRIPTION` | Response missing description, reason phrase used |
