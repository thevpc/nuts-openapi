# Schema Node Types

## Schema Declaration

### Object Schema (declaration form - revision 4)
```tson
SchemaName(description: "Description", example: {...}) {
  @required field1: string
  field2: integer
  field3: SchemaName2
}
```
- No `object` keyword required
- Name is head
- Attributes in parentheses
- Properties in body

### Object Schema (alternative form)
```tson
SchemaName: object(description: "Description") {
  @required field1: string
}
```
- `object` keyword optional

### Non-Object Schema
```tson
Status: string(enum: ["A", "B"])
Count: integer(minimum: 0)
Id: string(format: uuid)
CreatedAt: timestamp
```

## Property Declaration
```tson
[@required] propertyName: TypeExpr [attributes]
[@required] propertyName: TypeExpr(attributes) { body }
```

## Special Property Cases
```tson
# Untyped object (verbatim)
data {
  description: "..."
  type: object
}

# Property named 'example' or 'properties' - need explicit block
Example {
  properties {
    example: string  # actual property named example
    properties: object {
      nested: string
    }
  }
}

# Nested inline object
User {
  address: object(description: "Address") {
    street: string
    city: string
  }
}
```
