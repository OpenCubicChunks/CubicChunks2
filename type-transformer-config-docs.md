# Type Transformer Config

The type transformer is the system responsible for converting positions stored as `long` internally into triplets of `int`.
However, the system is generalizable to any kind of similar transform and so the `long -> 3 int` transform needs to be specified in the config file (stored in `type-transform.json`).
This json document contains six top-level fields:
- `types`
- `methods`
- `classes`
- `invokers`
- `suffixed_methods`
- `type_meta_info`


## `types`
This field tells the transformer exactly what kind of types we want to transform into what other types. For example,
we can tell it that we want any `long` that stores a block position to be turned into 3 `int`s. We must specify every type
that we will want to transform. For example, we also need to specify transforming a `LongSet` into a set for triplets of `int`s.
Same thing for a map or a list.

The field is a list of objects, each of which can have the following fields:

### `id`
This is simply a string containing a name for the kind of type we are transforming. This is not too important, but is used for
providing debug information and for specifying information in `methods`.

### `original`
This is a string specifying the type we are transforming from. This is given as a java descriptor. Examples are:
- `J`
- `Lit/unimi/dsi/fastutil/longs/LongSet;`

### `transformed`
This is a list of strings, each of which is a java descriptor. It specifies which types the original type will get expanded into.
For example, for our `long -> 3 int` transform it is `["I", "I", "I"]`.

### `from_original` (optional)
A list of methods which specifies how to turn an instance of the original type into an instance of the resulting types.
The list must have the same length as `transformed`. Each element is specified as a method id (see lower) and the `i`th method must accept
the original type as the only argument and return the `i`th transformed type.

### `to_original` (optional)
A single method id which specifies how to convert the transformed types back into the original type. The method must accept exactly the types specified in `transformed` and return the 
original type.

### `constant_replacements` (optional)
Constant replacements tell the transformer how to deal with literal constants which are of the original type and need to be transformed.
If the transformer encounters such a case and either `constant_replacements` is not supplied or does not include that specified value, an error will be thrown.
`constant_replacements` is simply a list of objects. These objects have the field `from` which gives the original literal value. 
They also have the field `to` which gives the transformed value. The transformed value must be a list of the same length as `transformed` and each element must be a literal of the corresponding type.

### `original_predicate` (optional)
Should provide the descriptor of an interface for a predicate which acts on the original type.

### `transformed_predicate` (optional)
Should provide the descriptor of an interface for a predicate which acts on the transformed types.

### `original_consumer` (optional)
Should provide the descriptor of an interface for a consumer which acts on the original type.

### `transformed_consumer` (optional)
Should provide the descriptor of an interface for a consumer which acts on the transformed types.

All of the above `predicate` and `consumer` fields are used to transform lambdas and method references.

## `methods`
The type transformer does still need to detect if a certain value is of a type we desire to transform. We can infer these by specifying some methods
which always accept as an argument a specific kind of value or maybe return a specific kind of value. For example, if the transform knows that `BlockPos.asLong()` returns
a long containing a block position, then in the code below, it is able to detect that `foo` is such a block position and then propagate this information.
```java
long foo = pos.toLong();
```

Note: The type transformer will automatically know about methods specified in `to_original` and `from_original` in the `types` field so they should not be specified here.

On top of that, this can also specify how the type transformer should transform a method call.

The `methods` field is a list of objects, each of which must have the following fields:

### `method`
A method id which specifies the method we are providing information about.

### `possibilities`
A list of objects which each specify a "possibility" for this method. A possibility just specifies exactly what kind of values the method is accepting.
A possibility contains the following fields:

#### `parameters`
A list of strings (or nulls) which specify exactly what this possibility expects the method to be called with. Each element in the list is a string
giving the id of a type transform (specified in `types`) or null if that parameter doesn't need to be transformed. Note that if the target method is not static,
the first element in the list specifies information about the `this`.

#### `return_type` (optional)
A string (or null, by default) which specifies what the method returns. This string is exactly the same as what is described above for `parameters`.

#### `minimumConditions` (optional)
The `mininums` field specifies the minimumConditions conditions that must be met to be sure that this possibility is what is actually being used.
If this field is omitted then this possibility will always be accepted. This field is a list of objects each with a `parameters` and (optionally)
a `return` field. These fields are similar to the `parameters` and `return_type` fields above. The difference is that the `parameters` and `return` fields can have
more nulls. A minimumConditions is "accepted" if every non-null value in `parameters` and `return_type` match the known situation when inferring transform types. 
A possibility is accepted if any of its minimumConditions are accepted.

#### `replacement` (optional)
This field allows you to override how the type transformer will transform a method call matching this possibility. This field is **required** if in the current possibility
the method returns a transform type which expands to more than one type. This is because Java does not support returning multiples values from a method in an efficient manner.

Typically, this field is an array whose length is equal to the number of types the method returns. Each element in the array is an array which gives the java bytecode
to replace the call with. By default, each of these bytecode snippets will be run with all the transformed method arguments on the stack in order. However, if 
the method parameters has types which expand to exactly the same number of types as the method returns, then the stack for the `i`th bytecode snippet will be run with only the 
`i`th transformed element (as well as the rest of the method arguments).

This method of putting the arguments onto the stack can be overriden by turning replacement into an object which contains an `expansion` field. This field contains
the array specified above. As well, it should contain an `indices` field. This field should be an array of equal length to `expansion`.

Each element specifies what parameters should be loaded onto the stack for the corresponding bytecode snippet. The element should be an array of length equal
to the number of parameters the original method takes. Each of these elements in the subarrays specify which transformed parameters should be pushed onto the stack for the snippet.
This is specified by an array of integers each integer being the index of the target type of the parameter to push onto the stack. If the parameter does not have a transform type, then
the only accepted value is 0. For conciseness, if these arrays contain only a single element, then the array can be replaced with just that element.

The bytecode snippets are specified as a list of bytecode instructions. Simple instructions such as `IADD` or `POP` are specified as a string with
the instruction name (in all caps).

Method calls are represented by the following object:
```json
{
  "type": "INVOKEVIRTUAL" | "INVOKESTATIC" | "INVOKESPECIAL" | "INVOKEINTERFACE",
  "method": <method id>
}
```

Constant loads are represented by the following object:
```json
{
  "type": "LDC",
  "constant_type": "string" | "int" | "long" | "float" | "double",
  "value": <value>
}
```

Instructions based on a type are represented by the following object:
```json
{
  "type": "NEW" | "ANEWARRAY" | "CHECKCAST" | "INSTANCEOF",
  "class": "class/name/Here"
}
```

Note: Bytecode snippets are checked at config load time to ensure type safety, that they do not underflow the stack, and that they return the expected value.

### 'finalizer' (optional)
This field is a bytecode snippet as specified above. It will be run after everything in the expansion.

### 'finalizer_indices' (optional)
Parameter indices similar to those specified above. 

NOTE: The discrepancy between how indices are specified for the expansion and the finalizer isn't great.

## `classes`
This field provides extra information on how specific classes should be transformed.
It is an array of objects each of which specify information for one class. 

Each object must have a `class` field which specifies the class this is for.

It can also have the following fields:
### `type_hints` (optional)
