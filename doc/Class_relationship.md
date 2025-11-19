# Data sources for AOT Cache assets relationships

We use different sources to build relationships between assets on the AOT cache map. When loading files on this diagnostics tool, assets of different types will be loaded into memory (Class, Method, CompileTrainingData, ConstantPool, Symbol, Object,...). These assets reflect the same assets that can be found on the AOT Cache.

On the `load` command, the tool will link assets to one another based on the same relationships the assets have at runtime. This way, a dependency graph can be generated and shown using the `tree` command.

No duplicated links are ever created.

## AOT Map

The AOT cache map file contains a list of all the assets included in the AOT cache. It also gives a hint on some relationships between assets.

### Direct Relationships

Training Data (MethodCounters, MethodData, KlassTrainingData, MethodTrainingData, and CompileTrainingData) are defined using the Method/Class they refer to:
> 0x0000000801bc7e40: @@ KlassTrainingData 40 **java.util.logging.LogManager**
> 
> 0x0000000801c4d7a8: @@ MethodTrainingData 96 **void java.util.concurrent.atomic.AtomicLong.lazySet(long)**
>
> 0x0000000801cd54b8: @@ CompileTrainingData 80 4 **void java.lang.ref.Reference.reachabilityFence(java.lang.Object)**

On this case:

1. The Class **java.util.logging.LogManager** will have a link to the KlassTrainingData in address 0x0000000801bc7e40
2. The KlassTrainingData in address 0x0000000801bc7e40 will have a link to the Class **java.util.logging.LogManager**
3. The Method **void java.util.concurrent.atomic.AtomicLong.lazySet(long)** will have a link to the MethodTrainingData in address 0x0000000801c4d7a8
4. The MethodTrainingData in address 0x0000000801c4d7a8 will have a link to the Method **void java.util.concurrent.atomic.AtomicLong.lazySet(long)**
5. The Method **void java.lang.ref.Reference.reachabilityFence(java.lang.Object)** will have a link to the CompileTrainingData in address 0x0000000801cd54b8
6. The CompileTrainingData in address 0x0000000801cd54b8 will have a link to the Method **void java.lang.ref.Reference.reachabilityFence(java.lang.Object)**

As with training data, we create a link to the referring class in the ConstantPool and ConstantPoolCache:

> 0x00000008068dd0b0: @@ ConstantPool      160 **java.lang.constant.Constable**

The ConstantPool in address 0x00000008068dd0b0 will have a link to the class **java.lang.constant.Constable**.

Objects (instances) also contain the class they belong to:

> 0x00000000fff63458: @@ Object (0xfff63458) **java.lang.String$CaseInsensitiveComparator**

The Object in address 0x00000000fff63458 will have a link to the class **java.lang.String$CaseInsensitiveComparator**.

This means, that for this object to be on the AOT Cache, the class **java.lang.String$CaseInsensitiveComparator** needs to be also on the AOT Cache. If, for some reason, the class got excluded from the AOT Cache during the training run, the object must be excluded too.

Symbols are less obvious, but the ones that refer to a single fully qualified name of the class are bi-directionally linked to the corresponding class:

> 0x0000000803afcea0: @@ Symbol            24 **jdk/jfr/EventType**
> 
> 0x0000000803afd208: @@ Symbol            32 **Ljdk/jfr/EventType;**
> 
> 0x0000000803b0f308: @@ Symbol            40 **[Lorg/apache/coyote/ErrorState;**

1. The Symbol **jdk/jfr/EventType** on address 0x0000000803afcea0 will have a bidirectional link to the Class **jdk.jfr.EventType**.
2. The Symbol **Ljdk/jfr/EventType;** on address 0x0000000803afd208 will have a bidirectional link to the Class **jdk.jfr.EventType**.
3. The Symbol **[Lorg/apache/coyote/ErrorState;** on address 0x0000000803b0f308 will have a bidirectional link to the Class **[Lorg/apache/coyote/ErrorState;**.

This helps in building relationships when using Symbols in other assets definitions (see following sections).

Symbols that refer to classes with generic types also create links, but to other Symbols:

> 0x0000000803bd2848: @@ Symbol            64 **Ljava/util/function/Supplier<Ljavax/script/ScriptEngine;>;**

This creates more than one link:
1. The Symbol on address 0x0000000803bd2848 will have a simple link to the Symbol **Ljava/util/function/Supplier;**.
2. The Symbol on address 0x0000000803bd2848 will have a simple link to the Symbol **Ljavax/script/ScriptEngine;**.

Methods create an explicit link between the Class they belong to, the returning Class, and the Classes used by the parameters.

> 0x0000000802cc66f0: @@ Method            88 org.foo.Returning org.baz.OwningClass.funcion(java.lang.String, org.another.Class)

For example, the Method **org.foo.Returning org.baz.OwningClass.funcion(java.lang.String, org.another.Class)** will create:
1. A link between the Method asset and its owning class **org.baz.OwningClass**
2. A link between the Class **org.baz.OwningClass** and the Method
3. A link between the Method asset and the returning class **org.foo.Returning**
4. A link between the Method asset and the first parameter class **java.lang.String**
5. A link between the Method asset and the second parameter class **org.another.Class**

Primitive types are ignored on this process.

This processing of Methods is building a relationship that will help cover very basic dependency among classes. 

For example, considering the return type of this method: **org.baz.OwningClass depends on org.foo.Returning**. Which means:
> org.baz.OwningClass <-> Method funcion -> org.foo.Returning
1. If **org.baz.OwningClass** is stored in the cache, then **org.foo.Returning** must be stored too because its method **funcion** needs it.
2. If **org.foo.Returning** is excluded from the cache, then **org.baz.OwningClass** can't be in the cache and will be automatically excluded too, as it contains a method that uses it.
3. **org.foo.Returning** can exist in the cache without **org.baz.OwningClass** existing in the cache, because the dependency is only one way. **org.baz.OwningClass** can be excluded from the cache without that affecting **org.foo.Returning**.

### Heap Root

Some elements are Heap Roots. We can detect those elements because they are included on the map file as part of an array of elements called `root` like this:

```
[heap               0x00000007ffc00000 - 0x00000007ffd6dcb0   1498288 bytes]
0x00000007ffc00000: Heap roots segment [5582]
roots[   0]: 0x00000007ffc05748 (0xfff80ae9) [Ljava.lang.Integer; length: 256
roots[   1]: 0x00000007ffc05b58 (0xfff80b6b) [Ljava.lang.Long; length: 256
roots[   2]: 0x00000007ffc05f68 (0xfff80bed) [Ljava.lang.Byte; length: 256
roots[   3]: 0x00000007ffc06378 (0xfff80c6f) [Ljava.lang.Short; length: 256
roots[   4]: 0x00000007ffc06788 (0xfff80cf1) [Ljava.lang.Character; length: 128
roots[   5]: 0x00000007ffc06998 (0xfff80d33) java.util.ImmutableCollections$MapN
roots[   6]: 0x00000007ffc070c8 (0xfff80e19) [Lsun.util.locale.BaseLocale; length: 19
roots[   7]: 0x00000007ffc07538 (0xfff80ea7) jdk.internal.module.ArchivedModuleGraph
[...]
```

The tool detects those objects as heap roots.

### Object Assets

On the definition of Objects, we have more information than just the header we have discussed already.

Objects of type java.lang.Class will show also what type of java.lang.Class it refers to:

> 0x00000007ffcfe9e0: @@ Object (0xfff9fd3c) java.lang.Class **Ljava/util/logging/ErrorManager;**
1. A link from the Object in address 0x00000007ffcfe9e0 to the Class **java.lang.Class** 
2. A link from the Object in address 0x00000007ffcfe9e0 to the Symbol **Ljava/util/logging/ErrorManager;**. 

This means that for this object to be in the AOT Cache, both classes are needed:
> Object 0x00000007ffcfe9e0 -> **java.lang.Class**
> 
> Object 0x00000007ffcfe9e0 -> Symbol **Ljava/util/logging/ErrorManager;** <-> Class **java.util.logging.ErrorManager**

Then, below the Object header, we have more details about the Object itself. We have a klass, which should be the same as the one on the `@@ Object` line:

> 0x00000007ffd66608: @@ Object (0xfffaccc1) **[Ljava.lang.invoke.LambdaForm$NamedFunction;** length: 6
> 
> [...]
> 
> - klass: '**java/lang/invoke/LambdaForm$NamedFunction**'[] 0x0000000800a14670

Note that the Symbol of the above class points to the array version of the class, while the `klass` points to the Symbol version of the non-array class. Anyway, both definitions end up creating a dependency to the Class **java.lang.invoke.LambdaForm$NamedFunction**

We also have fields, which create a relationship marking that the class is dependent on the class of the field:

> - private final 'sequence' '**Ljava/util/List**;' @16 **0x00000000ffd07550** (0xffd07550) **java.util.ArrayList**

On this case,we create three relationships:
1. One relationship between the Object and the Class **java.util.List**
2. One relationship between the Object and the Class **java.util.ArrayList** (subclass of java.util.List)
3. One relationship between the Object and the Object that sits on the address **0x00000000ffd07550** (which is of type java.util.ArrayList)

We also take into account the resolved references, which is an array of objects. We create a link to this array, which will lead us to dependencies to more objects and classes when we traverse it:

> - archived_resolved_references: **0x00000007ffd5f860** (0xfffabf0c) [Ljava.lang.Object; length: 22

## Training Log

This log helps us to build the linkage based on Symbols:

> archived klass  CP entry [  2]: **org/infinispan/rest/framework/impl/InvocationImpl** unreg => **java/lang/Object** boot

1. A link will be created from Symbol **org/infinispan/rest/framework/impl/InvocationImpl** to Symbol **java/lang/Object**.

If we haven't loaded already the AOT Cache Map file, two more actions will take place:
1. A link will be created from Symbol **org/infinispan/rest/framework/impl/InvocationImpl** to Class **org.infinispan.rest.framework.impl.InvocationImpl**
2. A link will be created from Symbol **java/lang/Object** to Class **java.lang.Object**

These classes will NOT be marked as stored in the AOT Cache unless another source loaded says otherwise.

This means that there will be a dependency graph of the form:

> [Class] org.infinispan.rest.framework.impl.InvocationImpl <-> [Symbol] org/infinispan/rest/framework/impl/InvocationImpl -> [Symbol] java/lang/Object <-> [Class] java.lang.Object

Links from Classes to their corresponding Symbols are always both ways.

> archived field  CP entry [ 20]: **org/infinispan/rest/framework/impl/InvocationImpl** => **org/infinispan/rest/framework/impl/InvocationImpl**.**action**:**Ljava/lang/String;**

1. A link will NOT be created from symbol **org/infinispan/rest/framework/impl/InvocationImpl** to Symbol **org/infinispan/rest/framework/impl/InvocationImpl** because they are the same. Otherwise, it would be created.
2. A link will be created from Symbol **org/infinispan/rest/framework/impl/InvocationImpl** to Symbol **action**
3. A link will be created from Symbol **org/infinispan/rest/framework/impl/InvocationImpl** to Symbol **Ljava/lang/String;**

Again, if no AOT Cache Map File was previously loaded, we also link to their classes:
1. A link will be created from Symbol **org/infinispan/rest/framework/impl/InvocationImpl** to its corresponding Class **org.infinispan.rest.framework.impl.InvocationImpl**
2. A link will be created from Symbol **Ljava/lang/String;** to its corresponding Class **java.lang.String**

> archived method CP entry [338]: **jdk/jfr/internal/dcmd/DCmdStart** **jdk/jfr/internal/dcmd/Argument**.**<init>**:**(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;Z)** **V** => **jdk/jfr/internal/dcmd/Argument**

1. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdStart** to Symbol **jdk/jfr/internal/dcmd/Argument**
2. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdStart** to Symbol **<init>**
3. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdStart** to Symbol **(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;Z)**
4. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdStart** to Symbol **V**
5. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdStart** to Symbol **jdk/jfr/internal/dcmd/Argument**

And then, the corresponding Classes will be created, if needed:
1. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdStart** to its corresponding Class **jdk.jfr.internal.dcmd.DCmdStart**
2. A link will be created from Symbol **jdk/jfr/internal/dcmd/Argument** to its corresponding Class **jdk.jfr.internal.dcmd.Argument**
3. A link will NOT be created from Symbol **V** to its corresponding Class because there is no corresponding Class.

Note that we have a Symbol that is a method signature, but we will not process it and link to its returning and parameter classes unless an AOT Cache map file gets loaded, which is the file that contains the proper information to do so.

> archived indy   CP entry [294]: **jdk/jfr/internal/dcmd/DCmdDump** (0) => **java/lang/invoke/LambdaMetafactory**.**metafactory**:**(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)** **Ljava/lang/invoke/CallSite;**

1. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdDump** to Symbol **java/lang/invoke/LambdaMetafactory**
2. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdDump** to Symbol **metafactory**
3. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdDump** to Symbol **(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)**
4. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdDump** to Symbol **Ljava/lang/invoke/CallSite;**

And once again, Classes will be created as needed:
1. A link will be created from Symbol **jdk/jfr/internal/dcmd/DCmdDump** to its corresponding Class **jdk.jfr.internal.dcmd.DCmdDump**
2. A link will be created from symbol **java/lang/invoke/LambdaMetafactory** to its corresponding Class **java.lang.invoke.LambdaMetafactory**
3. A link will be created from Symbol **Ljava/lang/invoke/CallSite;** to its corresponding Class **java.lang.invoke.CallSite**

There are similar log entries with reverted state that will generate the same type of linkage, to save the relationship linkage even if some of the related classes are excluded from cache.

## Production Log

We are not extracting any linkage relationship from this log.