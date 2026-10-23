# Type Expression Node Types

## Primitive Types
```tson
string
string(description: "text", minLength: 1, maxLength: 50)
number
number(minimum: 0, maximum: 100, multipleOf: 0.5)
integer
integer(minimum: 1)
boolean
```

## Enum
```tson
enum(values: [OPTION1 OPTION2 OPTION3])
enum(values: ["option1", "option2"], description: "Options")
Status: enum(values: [ACTIVE INACTIVE])
```
Sugar: expands to `string(enum: [...])`

## Timestamp/DateTime
```tson
timestamp
timestamp(description: "UTC datetime")
datetime  # alias of timestamp
```
Sugar: expands to `string(format: date-time)`

## Array
```tson
array[string]
array[User]
array(description: "Users")[User]
array(minItems: 1)[string]
array[object { name: string }]
array[array[string]]  # nested
```
Full array order: `array(attrs)[itemType]`

## Object
```tson
object {
  field: string
}
object(description: "Data") {
  id: string
  value: number
}
object(format: intermediate-structure) {
  ...
}
```
Body contains properties, not attributes. Attributes in parens or via `@metadata`.

## Reference
```tson
UserSchema
UserSchema(description: "A user")
ErrorResponse(example: {...})
```
Expands to `{"$ref": "#/components/schemas/UserSchema", ...}`

## Void
```tson
out(204): void
out(204): void(description: "No content")
```
Only valid in response context.

## Format Special Cases
```tson
object(format: myformat)  # -> format: "object::myformat"
string(format: date)      # -> format: "date" (bare)
string(format: "custom")  # quoted string kept as-is
```
