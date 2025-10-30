# Java AOT Cache Diagnostics Tool

This is an interactive console to help debug what is happening within and with the AOT Cache.

This is a work-in-progress and there is no stable interface, commands change as we evolve and use it. Use the `help` command to guide you, don't trust this README blindly.

## Running the application

You can find some already-packaged jar files on the [releases page](https://github.com/Delawen/leyden-analyzer/releases) that you can download and execute with a simple `java -jar leyden-analyzer-*-runner.jar`.

Or if you clone this source code, just `mvn package` and then `java -jar target/quarkus-app/quarkus-run.jar` to run it.

Or if you have [JBang](https://jbang.dev) installed, just run:

```bash
jbang analyzer@delawen/leyden-analyzer
```

NB: The analyzer is not published officially, so JBang might not always detect that a new version is available.
In that case run JBang with `--fresh` to force it to download the latest version: `jbang --fresh analyzer@delawen/leyden-analyzer`.
You might need to be patient because it uses [JitPack](https://jitpack.io) to build the tool on demand.

## How to use it

There is a `help` command that is very self-explanatory. Please, use it. 

You can jump to the **[Examples](#examples-of-usage)** if you are in a hurry to quickly answer common questions:
* [Why is this class in my AOT cache?](#why-is-this-class-in-my-aot-cache)
* [Which classes do this class drag into the cache?](#which-classes-do-this-class-drag-into-the-cache)
* [Why is this class NOT in my AOT cache?](#why-is-this-class-not-in-my-aot-cache)
* [Why is this method not properly trained?](#why-is-this-method-not-properly-trained)

But they don't contain all the possibilities this tool offers. 

Really, use the `help` command. Documentation matters.

### Colors

This tool uses a lot of colors to make reading and understanding of the content easier. As a general guide, this is their meaning:

* Green: Good, as expected, you can ignore it.
* Red: Warning, bad, note this.
* Yellow: Type of asset. Typically, it can be Method, Class, TrainingData,...
* Cyan: Identifier for a Class, Method, Warning,...

### Load some information

You should start by using the `load` command to load information from different sources.

Loading an AOT Cache Map File gives a better overview on what the cache contains.

Loading log files gives a better overview of why things are (or are not) in the cache and detect potential errors.

> **Do not mix logs and caches from different runs.*
> That will lead to inconsistent and wrong analysis.

You may mix logs and aot map files from the same training or production run. Then, the information will complement each other. It is recommended to load first the AOT cache map so when processing the log we already have the details about the elements inside the cache.

There is a status bar on the bottom of the interactive console showing the current elements loaded in our playground:
```
Our Playground contains: 9014 elements | 768 packages | 2 element types | 42 warnings  
```

#### Load an AOT Map File

This is usually the first step. We can add an AOT map file, which are generated with the argument `-Xlog:aot+map=trace:file=aot.
map:none:filesize=0` when executing a training run.

```bash
> load aotCache aot.map
Adding aot.map to our analysis...
This is a big file. The size of this file is 331 MB. This may take a while.
Consider using the `--background` option to load this file.
File aot.map added in 5478ms.
```

After loading the AOT Map File, we can explore the elements that have been saved in the AOT Cache.

#### Load logs

We can load logs for the training or the production run.

```bash
> load productionLog production.log
```
```
Adding production.log to our analysis...
File production.log added in 280ms.
```

```bash
> load trainingLog training.log
```
```
Adding training.log to our analysis...
File training.log added in 2925ms.
```

After loading some information, we can start the analysis.

### Show summarized information

The `info` command is very useful to get a general idea of what is happening in your application:

```bash
> info
```
```
RUN SUMMARY: 
Classes loaded: 
  -> Cached:8.802 (82,87 %)
  -> Not Cached:1.819 (17,13 %)
Lambda Methods loaded: 
  -> Cached:197 (11,28 %)
  -> Not Cached:1.550 (88,72 %)
  -> Portion of methods that are lambda: 1.747 (1,44 %)
Code Entries: 493
  -> Adapters: 493 (100,00 %)
  -> Shared Blobs: 0 (0,00 %)
  -> C1 Blobs: 0 (0,00 %)
  -> C2 Blobs: 0 (0,00 %)
AOT code cache size: 598432 bytes
AOT CACHE SUMMARY: 
Classes in AOT Cache: 8.802
  -> KlassTrainingData: 1.015 (11,53 %)
Objects in AOT Cache: 50.718
Methods in AOT Cache: 121.392
  -> MethodCounters: 7.014 (5,78 %)
  -> MethodData: 4.566 (3,76 %)
  -> MethodTrainingData: 5.189 (4,27 %)
  -> CompileTrainingData: 
      -> Level 1: 510 (0,42 %)
      -> Level 3: 3.121 (2,57 %)
      -> Level 4: 671 (0,55 %)

```

### Listing assets

We have the `ls` command to list what we know is on the cache. Most options in all the commands are autocompletable, so you can use `tab` to understand what to fill in there.

```bash
> ls 
[....]
[Symbol] (Ljava/lang/classfile/ClassFileElement;)Ljava/lang/classfile/ClassFileBuilder;
[Symbol] (Ljava/lang/classfile/ClassFileElement;)V
[Symbol] (Ljava/lang/classfile/ClassFileTransform;Ljava/lang/classfile/ClassFileBuilder;)Ljdk/internal/classfile/impl/
TransformImpl$ResolvedTransform;
[ConstMethod] void org.infinispan.remoting.transport.jgroups.JGroupsMetricsManagerImpl.lambda$stop$1(org.infinispan.re
moting.transport.jgroups.JGroupsMetricsManagerImpl$ClusterMetrics)
[Untrained][Method] void org.infinispan.remoting.transport.jgroups.JGroupsMetricsManagerImpl.lambda$stop$1(org.infinis
pan.remoting.transport.jgroups.JGroupsMetricsManagerImpl$ClusterMetrics)
[ConstMethod] void org.infinispan.remoting.transport.jgroups.JGroupsMetricsManagerImpl.onChannelConnected(org.jgroups.
JChannel, boolean)
[Untrained][Method] void org.infinispan.remoting.transport.jgroups.JGroupsMetricsManagerImpl.onChannelConnected(org.jg
roups.JChannel, boolean)
Found 685694 elements.
```

We can filter by type of element and package (the parameters are auto-completable with suggestions):
```bash
> ls -t=ConstantPool -pn=sun.util.locale
[...]
[ConstantPool] sun.util.locale.provider.NumberFormatProviderImpl
[ConstantPool] sun.util.locale.provider.LocaleProviderAdapter$Type
[ConstantPool] sun.util.locale.provider.LocaleProviderAdapter$$Lambda/0x800000099
[ConstantPool] sun.util.locale.provider.LocaleResources$ResourceReference
Found 32 elements.
```

### Search for warnings

We can also explore the potential errors/warnings/incidents. They may have been loaded from a log file, or they can be auto-detected.

```bash
> warning
000 [Unknown] Preload Warning: Verification failed for org.infinispan.remoting.transport.jgroups.JGroupsRaftManager
001 [Unknown] Preload Warning: Verification failed for org.apache.logging.log4j.core.async.AsyncLoggerContext
002 [StoringIntoAOTCache] Element 'org.apache.logging.log4j.core.async.AsyncLoggerContext' of type 'Class' couldn't be
 stored into the AOTcache because: Failed verification
003 [StoringIntoAOTCache] Element 'org.infinispan.remoting.transport.jgroups.JGroupsRaftManager' of type 'Class' could
n't be stored into the AOTcache because: Failed verification
Found 4 warnings.
```

When listing, the search can be limited for each type of warning with `warning <n>`, which will show the `n`-most relevant warnings per type.

If you want to auto-detect issues, you can run the command `warning check`.

```bash
```
```
> warning check 3
Trying to detect problems...
000 [Unknown] Preload Warning: Verification failed for org.infinispan.remoting.transport.jgroups.JGroupsRaftManager
001 [Unknown] Preload Warning: Verification failed for org.apache.logging.log4j.core.async.AsyncLoggerContext
002 [StoringIntoAOTCache] Element 'org.apache.logging.log4j.core.async.AsyncLoggerContext' of type 'Class' couldn't be
stored into the AOTcache because: Failed verification
003 [StoringIntoAOTCache] Element 'org.infinispan.remoting.transport.jgroups.JGroupsRaftManager' of type 'Class' could
n't be stored into the AOTcache because: Failed verification
024 [Training] Package 'org.apache.logging' contains 763 classes loaded and not cached.
025 [Training] Package 'io.reactivex.rxjava3' contains 724 classes loaded and not cached.
026 [Training] Package 'org.infinispan.server' contains 528 classes loaded and not cached.
027 [Training] Package 'org.infinispan.protostream' contains 42 methods that were called during training run but lack
full training (don't have some of the TrainingData objects associated to them).
028 [Training] Package 'io.reactivex.rxjava3' contains 30 methods that were called during training run but lack full t
raining (don't have some of the TrainingData objects associated to them).
029 [Training] Package 'org.apache.logging' contains 20 methods that were called during training run but lack full tra
ining (don't have some of the TrainingData objects associated to them).
Found 10 warnings.
The auto-detected issues may or may not be problematic.
It is up to the developer to decide that.
```

> **The auto-detected issues may or may not be problematic. It is up to the developer to decide that.**

You can clean up the list by using the `warning rm <id>` command. 

### Looking for details

To explore a bit more about what is on stored on the cache, we can use the command `describe`. 

Depending on if it was loaded from one type of file or another, the details may vary:

```bash
> describe -i=java.util.stream.Collectors -t=Class
```
```
-----
|  Class java.util.stream.Collectors on address 0x00000008008366e0 with size 528.
|  This information comes from: 
|    > Production log
|    > AOT Map
|  This class is included in the AOT cache.
|  This class has 141 Methods, of which 6 have been run and 6 have been trained.
|  It has a KlassTrainingData associated to it.
-----
```

It has a verbose option to show a bit more info:

```bash
> describe -i=org.infinispan.server.loader.Loader -t=Class -v
```
```
-----
|  Class org.infinispan.server.loader.Loader on address 0x0000000800a595a0 with size 528.
|  This information comes from:
|    > Production log
|    > AOT Map
|  This class is included in the AOT cache.
|  This class has 5 Methods, of which 1 have been run and 1 have been trained.
|  It has a KlassTrainingData associated to it.
|  There are no elements referenced from this element.
|  Elements that refer to this element:
|    _____
|    | [KlassTrainingData] org.infinispan.server.loader.Loader
|    | [Untrained][Method] void org.infinispan.server.loader.Loader.main(java.lang.String[])
|    | [Untrained][Method] void org.infinispan.server.loader.Loader.<init>()
|    | [Untrained][Method] void org.infinispan.server.loader.Loader.run(java.lang.String[], java.util.Properties)
|    | [Untrained][Method] java.lang.ClassLoader org.infinispan.server.loader.Loader.classLoaderFromPath(java.nio.file
.Path, java.lang.ClassLoader)
|    | [Trained][Method] java.lang.String org.infinispan.server.loader.Loader.extractArtifactName(java.lang.String)
|    | [Symbol] org/infinispan/server/loader/Loader
|    _____
-----

```

#### Tree information

The `tree` command shows related classes (although you can tweak the parameters to show more than classes). It can be used with the `describe` command to check details on elements inside the AOT Cache.

The basic tree command shows the graph dependency of what classes are used by the root class. This is useful to understand what classes does the root class trigger to be inside the cache. 

**This graph is strongly based on a training log, so you must load it before getting the right information.**

```bash
> tree -i=java.util.List  -max=5
```
```
Calculating dependency graph... 
+ [Trained][Class] java.util.List
 \
  + [Untrained][Class] java.util.RandomAccess
  |
  + [Trained][Class] java.util.AbstractList$RandomAccessSpliterator
   \
    + [Trained][Class] java.lang.Object
     \
      + [Object] (0xffe820c0) java.lang.Object
       \
        - [Trained][Class] java.lang.Object
      |
      + [Object] (0xffe820e0) java.lang.Object
```

There is also a `reverse` argument to show which classes use the root class. This is useful to understand why this class was loaded into the cache, as it shows who triggered its allocation in memory.

```bash
> tree -i=java.util.List  -max=5 --reverse
```
```
Calculating dependency graph... 
+ [Trained][Class] java.util.List
 \
  + [Untrained][Class] io.netty.buffer.PooledByteBufAllocator
   \
    + [Untrained][Class] io.netty.buffer.PoolArena
     \
      + [Untrained][Class] io.netty.buffer.PoolChunkList
       \
        - [Untrained][Class] io.netty.buffer.PoolArena
      |
      + [Untrained][Class] io.netty.buffer.PoolArena$DirectArena
      |
      + [Untrained][Class] io.netty.buffer.PoolArena$HeapArena
```

To avoid infinite loops and circular references, each element will be iterated over on the tree only once. Elements that have already appeared on the tree will be colored blue and will not have children.

### Cleanup

We can clean the loaded files and start from scratch

```bash
> clean
Cleaned the elements. Load again files to start a new analysis.
> ls
Found 0 elements.
```

### Exiting

Just `exit`.

## Examples of Usage

The following section contains examples on how to use this tool to improve your training runs and get better performance thanks to the AOT Cache in Java.

### Why is this class in my AOT cache? 

We start by loading an aot cache map file to the tool:

```bash
load aotCache aot.map
```
```
Adding aot.map to our analysis...
This is a big file. The size of this file is 395 MB. This may take a while.
Consider using the `--background` option to load this file.
File aot.map added in 10108ms.
```
And then we load a training log run to generate the dependency graph of classes.

```bash
load trainingLog training.log
```
```
Adding training.log to our analysis...
File training.log added in 2095ms.
> File aot.map added in 16466ms.
```

Now we just simply run the `tree --reverse` command to see which classes use my root class. Note that this can be a very long graph, you can use `n`, `-epn`, `-max`, and `--level` arguments to filter them out.
```bash
> tree -i=org.infinispan.configuration.cache.Configuration -pn=org.infinispan --reverse
```
```
Calculating dependency graph... 
+ [Untrained][Class] org.infinispan.configuration.cache.Configuration
 \
  + [Untrained][Class] org.infinispan.eviction.impl.ActivationManagerImpl
  |
  + [Untrained][Class] org.infinispan.interceptors.impl.JmxStatsCommandInterceptor
   \
    + [Untrained][Class] org.infinispan.interceptors.impl.CacheLoaderInterceptor
     \
      + [Untrained][Class] org.infinispan.interceptors.impl.PassivationCacheLoaderInterceptor
      |
      + [Untrained][Class] org.infinispan.interceptors.impl.ClusteredCacheLoaderInterceptor
    |
    + [Untrained][Class] org.infinispan.interceptors.impl.CacheMgmtInterceptor
     \
      + [Untrained][Class] org.infinispan.factories.InternalCacheFactory$StatsCache
      |
      + [Untrained][Class] org.infinispan.eviction.impl.EvictionManagerImpl
    |
    + [Untrained][Class] org.infinispan.interceptors.impl.CacheWriterInterceptor
     \
      + [Untrained][Class] org.infinispan.interceptors.impl.DistCacheWriterInterceptor

[...]
```

### Which classes do this class drag into the cache?

We start by loading an aot cache map file to the tool:

```bash
load aotCache aot.map
```
```
Adding aot.map to our analysis...
This is a big file. The size of this file is 395 MB. This may take a while.
Consider using the `--background` option to load this file.
File aot.map added in 10108ms.
```
And then we load a training log run to generate the dependency graph of classes.

```bash
load trainingLog training.log
```
```
Adding training.log to our analysis...
File training.log added in 2095ms.
```

Now we just simply run the `tree` command to see which classes are used by my root class. Note that this can be a very long graph, you can use `n`, `-epn`, `-max`, and `--level` arguments to filter them out.

```bash
> tree -i=org.infinispan.configuration.cache.Configuration -pn=org.infinispan
```
```
Calculating dependency graph...
+ [Untrained][Class] org.infinispan.configuration.cache.Configuration
  \
    + [Untrained][Class] org.infinispan.configuration.cache.UnsafeConfiguration
      \
        + [Untrained][Class] org.infinispan.commons.configuration.attributes.ConfigurationElement
          \
            + [Untrained][Class] [Lorg.infinispan.commons.configuration.attributes.ConfigurationElement;
              |
            + [Untrained][Class] org.infinispan.commons.configuration.attributes.AttributeSet
              |
        + [Untrained][Class] org.infinispan.commons.configuration.attributes.Attribute
          \
            + [Untrained][Class] org.infinispan.commons.configuration.attributes.AttributeDefinition
              \
                + [Untrained][Class] org.infinispan.commons.configuration.attributes.AttributeCopier
                  |
                + [Untrained][Class] org.infinispan.commons.configuration.attributes.AttributeInitializer
                  |
                + [Untrained][Class] org.infinispan.commons.configuration.attributes.AttributeMatcher
[...]
```

### Why is this class NOT in my AOT cache?

We load a production log run to add information on which classes were used during production and where they were loaded from. 

```bash
load productionLog production.log
```
```
Adding production.log to our analysis...
File production.log added in 558ms.
```
We can now list and `describe` classes that are outside the AOT cache using the `--use` parameter.

```bash
ls -t=Class --use=notCached
```
```
[...]
[Untrained][Class] org.infinispan.notifications.impl.AbstractListenerImpl$ListenerInvocationImpl$$Lambda/0x00000000500be230
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500cf6a0
[Untrained][Class] org.infinispan.objectfilter.impl.syntax.parser.ProtobufPropertyHelper$$Lambda/0x000000005012d8f0
[Untrained][Class] org.infinispan.persistence.file.SingleFileStore$$Lambda/0x00000000500fd000
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500cf8e8
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500cfb30
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500cfd78
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500d0000
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500d0248
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500d0490
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500d06d8
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500d0920
[Untrained][Class] org.infinispan.remoting.transport.jgroups.JGroupsMetricsMetadata$$Lambda/0x00000000500d0b68
Found 1819 elements.
```

```bash
describe -i=org.infinispan.remoting.transport.jgroups.JGroupsRaftManager --use=both -v
```
```
-----
|  Class org.infinispan.remoting.transport.jgroups.JGroupsRaftManager.
|  This information comes from: 
|    > Production log
|  This class is NOT included in the AOT cache.
|  This class doesn't seem to have training data. If you think this class and its methods should be part of the training, make sure your training run use them.
|  There are no elements referenced from this element.
|  There are no other elements of the cache that refer to this element.
-----
```

There are multiple reasons why this happened and we can't cover all. But we can load the training run log and see if we can find any warning about it:

```bash
load trainingLog training.log
```
```
Adding training.log to our analysis...
File training.log added in 1278ms.
```

```bash
warning
```
```
000 [Unknown] Preload Warning: Verification failed for org.infinispan.remoting.transport.jgroups.JGroupsRaftManager
001 [Unknown] Preload Warning: Verification failed for org.apache.logging.log4j.core.async.AsyncLoggerContext
002 [CacheCreation] Element 'org.infinispan.remoting.transport.jgroups.JGroupsRaftManager' of type 'Class' couldn't be
 stored into the AOTcache because: Failed verification
003 [CacheCreation] Element 'org.apache.logging.log4j.core.async.AsyncLoggerContext' of type 'Class' couldn't be store
d into the AOTcache because: Failed verification
Found 4 warnings.
> 
```

We find there two warnings associated to this element: `000` and `002`. Now we have something to investigate.

### Why is this method not properly trained?

We start by loading an aot cache map file to the tool:

```bash
load aotCache aot.map
```
```
Adding aot.map to our analysis...
This is a big file. The size of this file is 395 MB. This may take a while.
Consider using the `--background` option to load this file.
File aot.map added in 10108ms.
```

Note that as I haven't loaded any log file, we only work with information coming from the AOT cache, which is limited.

#### Asking the tool to find potential improvement areas

Then I ask for automatic warning checks:

```bash 
warning check
```
```
Trying to detect problems...
000 [Training] Package 'org.infinispan.protostream' contains 42 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
001 [Training] Package 'io.reactivex.rxjava3' contains 31 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
002 [Training] Package 'org.apache.logging' contains 20 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
003 [Training] Package 'org.infinispan.factories' contains 15 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
004 [Training] Package 'org.infinispan.configuration' contains 11 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
005 [Training] Package 'org.infinispan.commons' contains 11 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
006 [Training] Package 'io.micrometer.core' contains 5 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
007 [Training] Package 'org.infinispan.xsite' contains 5 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
008 [Training] Package 'org.infinispan.metrics' contains 5 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
009 [Training] Package 'org.jboss.logging' contains 5 methods that were called during training run but lack full training (don't have  some of the TrainingData objects associated to them).
Found 10 warnings.
The auto-detected issues may or may not be problematic.
It is up to the developer to decide that.
```

So I take for example warning `007`: the `org.infinispan.xsite` package contains methods that were run but not trained. 

#### Investigating the warning

Now I can list all methods in that package that were run.

```bash
ls --run -pn=org.infinispan.xsite
```
```
[Trained][Method] void org.infinispan.xsite.ClusteredCacheBackupReceiver.<init>()
[Trained][Method] org.infinispan.xsite.status.NoOpTakeOfflineManager org.infinispan.xsite.status.NoOpTakeOfflineManager.getInstance()
[Trained][Method] org.infinispan.xsite.metrics.NoOpXSiteMetricsCollector org.infinispan.xsite.metrics.NoOpXSiteMetricsCollector.getInstance()
[Trained][Method] org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()
[Trained][Method] void org.infinispan.xsite.statetransfer.NoOpXSiteStateTransferManager.<init>()
Found 5 elements.
```

And I found five elements. I can take any of them and see what is happening to it:

```bash
 describe -t=Method -i="org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()"
 ```
```
-----
|  Method org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance() on address 0x00000008017116c0 with size 88.
|  This information comes from: 
|    > AOT Map
|  This element refers to 1 other elements.
|  Belongs to the class org.infinispan.xsite.NoOpBackupSender
|  It has a MethodCounters associated to it, which means it was called at least once during training run.
|  It has no CompileTrainingData associated to it.
|  It has a MethodData associated to it.
|  It has a MethodTrainingData associated to it.
-----
```

So I discover there is no CompileTrainingData associated to it. 

This may mean that this method was not executed enough times during the training run to be worth recompiled and have CompileTrainingData associated to it. Or it may be that the associated data was rejected for some reason. But now I have something to investigate further.

#### Looking for extra information

I can also check what is happening on the main class for that method:

```bash
 describe -t=Class -i=org.infinispan.xsite.NoOpBackupSender
 ```
```
-----
|  Class org.infinispan.xsite.NoOpBackupSender on address 0x00000008017114c0 with size 624.
|  This information comes from: 
|    > AOT Map
|  This class is included in the AOT cache.
|  This class has 9 Methods, of which 1 have been run and 1 have been trained.
|  This class doesn't seem to have training data. 
-----
```