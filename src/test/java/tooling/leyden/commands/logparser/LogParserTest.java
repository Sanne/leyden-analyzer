package tooling.leyden.commands.logparser;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tooling.leyden.aotcache.ClassObject;
import tooling.leyden.aotcache.ConstantPoolObject;
import tooling.leyden.aotcache.Element;
import tooling.leyden.aotcache.ReferencingElement;
import tooling.leyden.commands.DefaultTest;
import tooling.leyden.commands.LoadFileCommand;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LogParserTest extends DefaultTest {

	@ParameterizedTest
	@ValueSource(strings = {"aot.log", "aot.log.0", "aot.log.1"})
	void addElements(String file) throws Exception {
		File f = new File(getClass().getResource(file).getPath());
		getSystemRegistry().execute("load productionLog " + f.getAbsolutePath());
		final var aotCache = getDefaultCommand().getInformation();

		//Now check individual values
		for (Element e : aotCache.getAll()) {
			assertNull(e.getAddress()); //Log doesn't provide this
			assertNotNull(e.getKey());
			assertNull(e.getSize()); //Log doesn't provide this
			assertNotNull(e.getType());
			assertEquals(1, e.getWhereDoesItComeFrom().size());
			//Sometimes due to the order of the log,
			//we will have more than one source here
			assertTrue(e.getSources().size() > 0);
		}
	}

	@Test
	void addCreationWarnings() throws Exception {
		File f = new File(getClass().getResource("aot.log.0").getPath());
		File f2 = new File(getClass().getResource("aot.log.1").getPath());
		getSystemRegistry().execute("load trainingLog " + f.getAbsolutePath() + " " + f2.getAbsolutePath());
		final var aotCache = getDefaultCommand().getInformation();
		assertFalse(aotCache.getWarnings().isEmpty());
	}

	@Test
	void addLoadingLog() throws Exception {
		File f = new File(getClass().getResource("aot.log.loading").getPath());
		getSystemRegistry().execute("load productionLog " + f.getAbsolutePath());

		final var aotCache = getDefaultCommand().getInformation();

		assertFalse(aotCache.getStatistics().getKeys().isEmpty());
		assertTrue(aotCache.getWarnings().isEmpty());
		assertFalse(aotCache.getAll().isEmpty());
		assertFalse(aotCache.getExternalElements().isEmpty());

		final int extClasses = Integer.valueOf(aotCache.getStatistics().getValue("[LOG] Classes not loaded from AOT Cache").toString());
		final int extLambdas = Integer.valueOf(aotCache.getStatistics().getValue("[LOG] Lambda Methods not loaded from AOT Cache").toString());
		final int classes = Integer.valueOf(aotCache.getStatistics().getValue("[LOG] Classes loaded from AOT Cache").toString());
		final int lambdas = Integer.valueOf(aotCache.getStatistics().getValue("[LOG] Lambda Methods loaded from AOT Cache").toString());

		assertEquals(1827, extClasses);
		assertEquals(1556, extLambdas);
		assertEquals(8799, classes);
		assertEquals(197, lambdas);

		assertEquals(aotCache.getExternalElements().size(), extClasses);
		assertEquals(aotCache.getElements(null, null, null, true, false, "Class").count(), classes);
		assertEquals(aotCache.getElements(null, null, null, true, true, "Class").count(), extClasses + classes);
	}


	@Test
	void acceptAOTResolve() throws Exception {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var information = loadFile.getParent().getInformation();
		final var parser = new TrainingLogParser(loadFile);
		final var aotParser = new AOTMapParser(loadFile);

		parser.accept("[info ][aot,resolve              ] Archiving CP entries for org/infinispan/rest/framework/impl/InvocationImpl");
		parser.accept("[trace][aot,resolve              ] archived klass  CP entry [  2]: org/infinispan/rest/framework/impl/InvocationImpl unreg => java/lang/Object boot");
		parser.accept("[trace][aot,resolve              ] archived klass  CP entry [  8]: org/infinispan/rest/framework/impl/InvocationImpl unreg => org/infinispan/rest/framework/impl/InvocationImpl unreg");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [  7]: org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl.methods:Ljava/util/Set;");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 13]: org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl.paths:Ljava/util/Set;");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 16]: org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl.handler:Ljava/util/function/Function;");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 20]: " +
				"org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl.action:Ljava/lang/String;");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 24]: " +
				"org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl.name:Ljava/lang/String;");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 27]: " +
				"org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl.anonymous:Z");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 31]: " +
				"org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl" +
				".permission:Lorg/infinispan/security/AuthorizationPermission;");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 35]: " +
				"org/infinispan/rest/framework/impl/InvocationImpl => " +
				"org/infinispan/rest/framework/impl/InvocationImpl.deprecated:Z");

		//Make sure we reuse classes from aot map file
		aotParser.accept("0x00000008007cd538: @@ Symbol             14 org/infinispan/security/AuditContext");
		parser.accept("[trace][aot,resolve              ] archived field  CP entry [ 38]: " +
				"org/infinispan/rest/framework/impl/InvocationImpl => " +
				"org/infinispan/rest/framework/impl/InvocationImpl.auditContext:Lorg/infinispan/security/AuditContext;");
		aotParser.accept("0x00000008007cd538: @@ Symbol             44 io/reactivex/rxjava3/internal/subscribers/InnerQueuedSubscriber");
		aotParser.accept("0x00000008007cd538: @@ Symbol             48 java/util/concurrent/atomic/AtomicReference");
		parser.accept("[trace][aot,resolve              ] archived klass  CP entry [ 23]: " +
				"io/reactivex/rxjava3/internal/subscribers/InnerQueuedSubscriber unreg => java/util/concurrent/atomic/AtomicReference boot");

		var parentSymbol = (ReferencingElement) information.getElements("org/infinispan/rest/framework/impl/InvocationImpl",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(16, parentSymbol.getReferences().size());
		assertEquals(1,
				((ReferencingElement) information.getElements("io/reactivex/rxjava3/internal/subscribers/InnerQueuedSubscriber",
						null, null, true, true, "Symbol").findAny().get())
						.getReferences().size());

		//Now try adding methods
		parser.accept("[trace][aot,resolve              ] archived method CP entry [194]: " +
				"jdk/jfr/internal/jfc/model/XmlSelection jdk/jfr/internal/jfc/model/XmlSelection.getDefault:()Ljava/lang/String; => jdk/jfr/internal/jfc/model/XmlSelection");
		parentSymbol = (ReferencingElement) information.getElements("jdk/jfr/internal/jfc/model/XmlSelection",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(2, parentSymbol.getReferences().size());

		parser.accept("[trace][aot,resolve              ] archived method CP entry [  8]: " +
				"jdk/jfr/internal/dcmd/DCmdStart$$Lambda+0x80000010e java/lang/Object.<init>:()V => java/lang/Object");
		parentSymbol = (ReferencingElement) information.getElements("jdk/jfr/internal/dcmd/DCmdStart$$Lambda+0x80000010e",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(3, parentSymbol.getReferences().size());

		parser.accept("aot.log.2:[trace][aot,resolve              ] archived method CP entry [338]: " +
				"jdk/jfr/internal/dcmd/DCmdStart jdk/jfr/internal/dcmd/Argument.<init>" +
				":(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;Z)V => jdk/jfr/internal/dcmd/Argument");
		parentSymbol = (ReferencingElement) information.getElements("jdk/jfr/internal/dcmd/DCmdStart",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(3, parentSymbol.getReferences().size());
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("jdk/jfr/internal/dcmd/Argument")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("<init>")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey()
				.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;Z)V")));

		//interface method
		parser.accept("[trace][aot,resolve              ] archived interface method CP entry [ 18]: " +
				"jdk/internal/module/ModuleBootstrap$$Lambda+0x80000000c java/util/Collection.stream:()Ljava/util/stream/Stream; => java/util/Collection");
		parentSymbol = (ReferencingElement) information.getElements("jdk/internal/module/ModuleBootstrap$$Lambda+0x80000000c",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(3, parentSymbol.getReferences().size());

		//With Symbols from the AOT Map file
		aotParser.accept("0x00000008007cd538: @@ Symbol             14 jdk/jfr/internal/jfc/model/XmlNot");
		aotParser.accept("0x00000008007cd538: @@ Symbol             14 size");
		aotParser.accept("0x00000008007cd538: @@ Symbol             14 java/util/List");
		aotParser.accept("0x00000008007cd538: @@ Symbol             14 ()I");
		parser.accept("[trace][aot,resolve              ] archived interface method CP entry [ 13]: " +
				"jdk/jfr/internal/jfc/model/XmlNot java/util/List.size:()I => java/util/List");
		parentSymbol = (ReferencingElement) information.getElements("jdk/jfr/internal/jfc/model/XmlNot",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(3, parentSymbol.getReferences().size());
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("java/util/List")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("size")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("()I")));

		//Add indy archive
		parser.accept("[trace][aot,resolve              ] archived indy   CP entry [294]: " +
				"jdk/jfr/internal/dcmd/DCmdDump (0) => java/lang/invoke/LambdaMetafactory.metafactory:" +
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
		parentSymbol = (ReferencingElement) information.getElements("jdk/jfr/internal/dcmd/DCmdDump",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(3, parentSymbol.getReferences().size());

		final var signature = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
		parser.accept("[trace][aot,resolve              ] archived indy   CP entry [263]: " +
				"jdk/jfr/internal/dcmd/DCmdCheck (1) => java/lang/invoke/LambdaMetafactory.metafactory:" +
				signature);
		parentSymbol = (ReferencingElement) information.getElements("jdk/jfr/internal/dcmd/DCmdCheck",
				null, null, true, true, "Symbol").findAny().get();
		assertEquals(3, parentSymbol.getReferences().size());
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("java/lang/invoke/LambdaMetafactory")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("metafactory")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals(signature)));

		assertTrue(information.getAll().parallelStream().allMatch(e -> e.getType().equals("Symbol")));

		//If a class exists already, the Symbol must be linked there:
		aotParser.accept("0x0000000800a8efe8: @@ Class             536 sun.util.locale.BaseLocale");
		aotParser.accept("0x0000000800a8efe8: @@ Class             536 sun.util.locale.LocaleUtils");
		aotParser.accept("0x0000000800a92ef0: @@ ConstantPoolCache 64 sun.util.locale.BaseLocale");
		aotParser.accept("0x0000000802f8c710: @@ ConstantPool      2456 sun.util.locale.BaseLocale");
		parser.accept("[trace][aot,resolve              ] archived klass  CP entry [  2]: sun/util/locale/BaseLocale boot => java/lang/Object boot");
		parser.accept("[trace][aot,resolve              ] archived klass  CP entry [  8]: sun/util/locale/BaseLocale boot => sun/util/locale/BaseLocale boot");
		parser.accept("[trace][aot,resolve              ] archived klass  CP entry [ 28]: sun/util/locale/BaseLocale boot => sun/util/locale/LocaleUtils boot (not supertype)");

		var classObj = (ClassObject) information.getElements("sun.util.locale.BaseLocale",
				null, null, true, true, "Class").findAny().get();
		assertEquals(1, classObj.getSymbols().size());
		parentSymbol = classObj.getSymbols().getFirst();
		assertEquals(classObj.getKey(), parentSymbol.getKey().replaceAll("/", "."));
		assertEquals(3, parentSymbol.getReferences().size());
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("java/lang/Object")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getKey().equals("sun/util/locale/LocaleUtils")));
		assertTrue(parentSymbol.getReferences().stream().anyMatch(symbol -> symbol.getType().equals("Class")));

		var cp = (ConstantPoolObject) information.getElements("sun.util.locale.BaseLocale",
				null, null, true, true, "ConstantPool").findAny().get();
		assertNotNull(cp.getConstantPoolCacheAddress());
		assertEquals(cp.getPoolHolder(), classObj);

		classObj = (ClassObject) information.getElements("sun.util.locale.LocaleUtils",
				null, null, true, true, "Class").findAny().get();
		assertEquals(1, classObj.getSymbols().size());
		parentSymbol = classObj.getSymbols().getFirst();
		assertEquals(classObj.getKey(), parentSymbol.getKey().replaceAll("/", "."));
	}
}