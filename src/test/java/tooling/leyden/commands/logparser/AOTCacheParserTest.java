package tooling.leyden.commands.logparser;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import tooling.leyden.aotcache.ClassObject;
import tooling.leyden.aotcache.ConstantPoolObject;
import tooling.leyden.aotcache.Element;
import tooling.leyden.aotcache.MethodObject;
import tooling.leyden.aotcache.ReferencingElement;
import tooling.leyden.commands.DefaultTest;
import tooling.leyden.commands.LoadFileCommand;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AOTCacheParserTest extends DefaultTest {

	@Test
	void accept() throws Exception {
		File file = new File(getClass().getResource("aot.map").getPath());
		final var aotCache = getDefaultCommand().getInformation();
		getSystemRegistry().execute("load aotCache " + file.getAbsolutePath());
		assertTrue(aotCache.getAll().size() > 0);
		assertEquals(0, aotCache.getWarnings().size());

		//Now check individual values
		//Skip classes because they may have been indirectly generated
		aotCache.getAll().parallelStream().filter(e -> !e.getType().equalsIgnoreCase("Class")).forEach(e -> {
			assertNotNull(e.getAddress(), "Address of " + e + " shouldn't be null.");
			assertNotNull(e.getKey(), "Key of " + e + " shouldn't be null.");
			assertNotNull(e.getSize(), "Size of " + e + " shouldn't be null.");
			assertNotNull(e.getType(), "Type of " + e + " shouldn't be null.");
			assertEquals(1, e.getSources().size(), "We shouldn't have more than one source here " + e.getSources().stream().reduce((s, s2) -> s + ", " + s2));
		});

		assertEquals(655, aotCache.getElements(null, null, null, true, false, "Symbol").count());
		assertEquals(114, aotCache.getElements(null, null, null, true, false, "ConstantPool").count());
		assertEquals(494 + 5, aotCache.getElements(null, null, null, true, true, "Class").count());
		assertEquals(5927, aotCache.getElements(null, null, null, true, false, "Method").count());
		assertEquals(1385, aotCache.getElements(null, null, null, true, false, "ConstMethod").count());
	}

	@Test
	void acceptObjectsWithReferences() throws Exception {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var aotCache = loadFile.getParent().getInformation();
		AOTMapParser aotCacheParser = new AOTMapParser(loadFile);

		var classObject = new ClassObject("java.lang.Float");
		aotCache.addAOTCacheElement(classObject, "test");

		classObject = new ClassObject("java.lang.String$CaseInsensitiveComparator");
		aotCache.addAOTCacheElement(classObject, "test");

		aotCacheParser.accept("0x00000000fff63458: @@ Object (0xfff63458) java.lang.String$CaseInsensitiveComparator");
		aotCacheParser.accept("0x00000000fff632f0: @@ Object (0xfff632f0) [I length: 0");
		aotCacheParser.accept("0x00000000fff62900: @@ Object (0xfff62900) java.lang.Float");

		assertEquals(5, aotCache.getAll().size());
		final var objects = aotCache.getElements(null, null, null, true, false, "Object").toList();
		assertEquals(3, aotCache.getElements(null, null, null, true, false, "Object").count());
		for (Element e : objects) {
			assertTrue(e instanceof ReferencingElement);
			ReferencingElement re = (ReferencingElement) e;
			if (!re.getKey().equals("(0xfff632f0) [I length: 0")) {
				assertTrue(!re.getReferences().isEmpty());
				assertTrue(re.getKey().contains(re.getReferences().getFirst().getKey()));
			}
		}

	}

	@Test
	void acceptSymbol() throws Exception {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var aotCache = loadFile.getParent().getInformation();
		AOTMapParser aotCacheParser = new AOTMapParser(loadFile);

		aotCacheParser.accept("0x0000000800dae648: @@ Class             616 java.security.InvalidAlgorithmParameterException");
		aotCacheParser.accept("0x00000008020acbe8: @@ Symbol            56 java/security/InvalidAlgorithmParameterException");
		aotCacheParser.accept("0x000000080225a980: @@ Symbol            56 Ljava/security/InvalidAlgorithmParameterException;");

		assertEquals(3, aotCache.getAll().size());
		assertEquals(2, aotCache.getElements(null, null, null, true, false, "Symbol").count());
		assertEquals(1, aotCache.getElements(null, null, null, true, false, "Class").count());
		for (Element e : aotCache.getElements(null, null, null, true, false, "Symbol").toList()) {
			assertTrue(e instanceof ReferencingElement);
			assertEquals(0, ((ReferencingElement)e).getReferences().size());
		}
		ClassObject classObject =  (ClassObject) aotCache.getElements(null, null, null, true, false, "Class").findAny().get();
		assertEquals(2, classObject.getSymbols().size());

	}


	@Test
	void acceptObjectsWithExplicitReference() throws Exception {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var aotCache = loadFile.getParent().getInformation();
		AOTMapParser aotCacheParser = new AOTMapParser(loadFile);

		var classObject = new ClassObject("java.lang.String");
		aotCache.addAOTCacheElement(classObject, "test");

		aotCacheParser.accept("0x0000000801de8110: @@ Symbol            24 java/lang/String");

		aotCacheParser.accept("0x0000000800a8efe8: @@ Class             536 sun.util.locale.BaseLocale");
		aotCacheParser.accept("0x0000000800a8f258: @@ ConstantPoolCache 64 sun.util.locale.BaseLocale");

		aotCacheParser.accept("0x0000000800a8f3c8: @@ Class             512 [Lsun.util.locale.BaseLocale;");
		aotCacheParser.accept("0x0000000800a98270: @@ Class             568 sun.util.locale.BaseLocale$1");

		aotCacheParser.accept("0x0000000800a98500: @@ ConstantPoolCache 64 sun.util.locale.BaseLocale$1");

		aotCacheParser.accept("0x0000000801f74b70: @@ Symbol            32 sun/util/locale/BaseLocale");
		aotCacheParser.accept("0x0000000801f74bb0: @@ Symbol            40 [Lsun/util/locale/BaseLocale;");
		aotCacheParser.accept("0x0000000801f74bd8: @@ Symbol            72 (Lsun/util/locale/BaseLocale;Lsun/util/locale/LocaleExtensions;)V");
		aotCacheParser.accept("0x0000000801f74c78: @@ Symbol            40 Lsun/util/locale/BaseLocale;");
		aotCacheParser.accept("0x0000000801f74cf8: @@ Symbol            112 (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lsun/util/locale/BaseLocale;");
		aotCacheParser.accept("0x0000000801f74e88: @@ Symbol            88 (Lsun/util/locale/BaseLocale;Lsun/util/locale/LocaleExtensions;)Ljava/util/Locale;");
		aotCacheParser.accept("0x0000000801f754d0: @@ Symbol            104 (Lsun/util/locale/BaseLocale;Lsun/util/locale/LocaleExtensions;)Lsun/util/locale/LanguageTag;");
		aotCacheParser.accept("0x0000000801f75708: @@ Symbol            40 ()Lsun/util/locale/BaseLocale;");
		aotCacheParser.accept("0x0000000801f76418: @@ Symbol            72 Ljava/util/Map<Lsun/util/locale/BaseLocale;Ljava/util/Locale;>;");
		aotCacheParser.accept("0x0000000801f77410: @@ Symbol            72 (Ljava/lang/String;Ljava/lang/String;)Lsun/util/locale/BaseLocale;");
		aotCacheParser.accept("0x0000000801f774b0: @@ Symbol            40 sun/util/locale/BaseLocale$1");
		aotCacheParser.accept("0x0000000801f77580: @@ Symbol            104 Ljava/util/function/Supplier<Ljdk/internal/util/ReferencedKeySet<Lsun/util/locale/BaseLocale;>;>;");
		aotCacheParser.accept("0x0000000801f77658: @@ Symbol            40 Lsun/util/locale/BaseLocale$1;");
		aotCacheParser.accept("0x0000000801f77680: @@ Symbol            80 ()Ljdk/internal/util/ReferencedKeySet<Lsun/util/locale/BaseLocale;>;");
		aotCacheParser.accept("0x0000000801f776d0: @@ Symbol            128 Ljava/lang/Object;Ljava/util/function/Supplier<Ljdk/internal/util/ReferencedKeySet<Lsun/util/locale/BaseLocale;>;>;");
		aotCacheParser.accept("0x000000080208db10: @@ Symbol            112 (Lsun/util/locale/BaseLocale;Lsun/util/locale/LocaleExtensions;)Lsun/util/locale/InternalLocaleBuilder;");
		aotCacheParser.accept("0x0000000802093c48: @@ Symbol            112 Ljdk/internal/util/ReferencedKeyMap<Lsun/util/locale/BaseLocale;Ljava/util/List<Ljava/util/Locale;>;>;");
		aotCacheParser.accept("0x0000000802093d78: @@ Symbol            56 (Lsun/util/locale/BaseLocale;)Ljava/util/List;");
		aotCacheParser.accept("0x0000000802093e38: @@ Symbol            72 (Lsun/util/locale/BaseLocale;)Ljava/util/List<Ljava/util/Locale;>;");

		aotCacheParser.accept("0x0000000802ff83e8: @@ ConstantPool      2456 sun.util.locale.BaseLocale");
		aotCacheParser.accept("0x0000000802f88a70: @@ ConstantPool      400 sun.util.locale.BaseLocale$1");

		aotCacheParser.accept("0x00000000ffe06dc0: @@ Object (0xffe06dc0) [Lsun.util.locale.BaseLocale; length: 19");
		aotCacheParser.accept("0x00000000ffe06e20: @@ Object (0xffe06e20) sun.util.locale.BaseLocale");
		aotCacheParser.accept("0x00000000ffe06e58: @@ Object (0xffe06e58) sun.util.locale.BaseLocale");
		aotCacheParser.accept("0x00000000ffe06e90: @@ Object (0xffe06e90) sun.util.locale.BaseLocale");
		aotCacheParser.accept("0x00000000ffe94558: @@ Object (0xffe94558) java.lang.String \"sun.util.locale.BaseLocale\"");
		aotCacheParser.accept("0x00000000ffef4720: @@ Object (0xffef4720) java.lang.Class Lsun/util/locale/BaseLocale$1;");
		aotCacheParser.accept("0x00000000ffefd1e8: @@ Object (0xffefd1e8) java.lang.Class Lsun/util/locale/BaseLocale;");
		aotCacheParser.accept("0x00000000ffefd288: @@ Object (0xffefd288) java.lang.Class [Lsun/util/locale/BaseLocale;");

		assertEquals(2, aotCache.getElements(null, null, null, true, false, "ConstantPool").count());
		assertTrue(aotCache.getElements(null, null, null, true, false, "ConstantPool")
				.allMatch(cp -> ((ConstantPoolObject)cp).getConstantPoolCacheAddress() != null));

		assertEquals(20, aotCache.getElements(null, null, null, true, false, "Symbol").count());
		assertEquals(8, aotCache.getElements(null, null, null, true, false, "Object").count());

		for (Element e : aotCache.getElements(null, null, null, true, false,
				"Object").toList()) {
			assertTrue(e instanceof ReferencingElement);
			assertTrue(((ReferencingElement) e).getReferences().size() > 0, e + " should have at least a reference");
		}

		assertEquals(3 + 1, aotCache.getElements(null, null, null, true, false, "Class").count());
		aotCache.getElements(null, null, null, true, false, "Class")
				.allMatch(c -> ((ClassObject)c).getSymbols().size() > 0);

		//Make sure we didn'0t create unexpected assets in the cache:
		assertEquals(2 + 20 + 8 + 3 + 1, aotCache.getAll().size());

	}


	@Test
	void acceptMethodDataAndMethodCounters() throws Exception {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var aotCache = loadFile.getParent().getInformation();
		AOTMapParser aotCacheParser = new AOTMapParser(loadFile);

		aotCacheParser.accept("0x0000000800772d58: @@ Class             528 java.lang.Object");
		aotCacheParser.accept("0x0000000800799620: @@ Class             528 jdk.internal.misc.CDS");
		aotCacheParser.accept("0x0000000801d92878: @@ Method            88 void jdk.internal.misc.CDS.keepAlive(java.lang.Object)");
		aotCacheParser.accept("0x0000000801d928d0: @@ MethodData        568 void jdk.internal.misc.CDS.keepAlive(java.lang.Object)");
		aotCacheParser.accept("0x0000000801d92b08: @@ MethodCounters    64 void jdk.internal.misc.CDS.keepAlive(java.lang.Object)");
		aotCacheParser.accept("0x00000008048c5c60: @@ ConstMethod       152 void jdk.internal.misc.CDS.keepAlive(java.lang.Object)");

		aotCacheParser.accept("0x0000000801f5fb68: @@ MethodData        296 void java.lang.Long.<init>(long)");
		aotCacheParser.accept("0x0000000801f5fd90: @@ MethodData        288 void java.lang.Short.<init>(short)");
		aotCacheParser.accept("0x0000000801f6a670: @@ MethodData        672 int jdk.internal.util.ArraysSupport.hugeLength(int, int)");
		aotCacheParser.accept("0x0000000801f6a968: @@ MethodData        408 int jdk.internal.util.ArraysSupport.utf16hashCode(int, byte[], int, int)");
		aotCacheParser.accept("0x0000000801f6b188: @@ MethodData        328 int jdk.internal.util.ArraysSupport.hashCode(int, int[], int, int)");
		aotCacheParser.accept("0x0000000801f6b328: @@ MethodData        328 int jdk.internal.util.ArraysSupport.hashCode(int, short[], int, int)");
		aotCacheParser.accept("0x0000000801f6b4c8: @@ MethodData        328 int jdk.internal.util.ArraysSupport.hashCode(int, char[], int, int)");
		aotCacheParser.accept("0x0000000801f6f848: @@ MethodData        584 void java.util.ImmutableCollections$Set12.<init>(java.lang.Object, java.lang.Object)");
		aotCacheParser.accept("0x0000000801f895e0: @@ MethodCounters    64 java.util.Optional java.lang.VersionProps.optional()");
		aotCacheParser.accept("0x0000000801f89678: @@ MethodCounters    64 java.util.Optional java.lang.VersionProps.build()");
		aotCacheParser.accept("0x0000000801f89710: @@ MethodCounters    64 java.util.Optional java.lang.VersionProps.pre()");
		aotCacheParser.accept("0x0000000801f897a8: @@ MethodCounters    64 java.util.List java.lang.VersionProps.versionNumbers()");
		aotCacheParser.accept("0x0000000801f89840: @@ MethodCounters    64 java.util.Optional java.lang.VersionProps.optionalOf(java.lang.String)");
		aotCacheParser.accept("0x0000000801f898d8: @@ MethodCounters    64 java.util.List java.lang.VersionProps.parseVersionNumbers(java.lang.String)");

		var elements = aotCache.getElements(null, null, null, true, false, "MethodData").toList();
		assertEquals(9, elements.size());
		for (Element e : elements) {
			assertTrue(((ReferencingElement) e).getReferences().size() > 0);
		}

		elements = aotCache.getElements(null, null, null, true, false, "MethodCounters").toList();
		assertEquals(7, elements.size());
		for (Element e : elements) {
			assertTrue(((ReferencingElement) e).getReferences().size() > 0);
		}

		elements = aotCache.getElements("void jdk.internal.misc.CDS.keepAlive(java.lang.Object)",
				null, null, true, false, "Method").toList();
		var method = elements.getFirst();
		assertNotNull(method.getClass());
		assertEquals("jdk.internal.misc.CDS", ((MethodObject) method).getClassObject().getKey());
		elements = aotCache.getElements("void jdk.internal.misc.CDS.keepAlive(java.lang.Object)",
				null, null, true, false, "ConstMethod", "MethodData", "MethodCounters").toList();

		assertEquals(3, elements.size());
		for (Element e : elements) {
			if (e instanceof ReferencingElement re) {
				assertTrue(re.getReferences().contains(method));
			}
		}
	}

	@Test
	void acceptLambda() {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var aotCache = loadFile.getParent().getInformation();
		AOTMapParser aotCacheParser = new AOTMapParser(loadFile);

		aotCacheParser.accept("0x0000000800ede8d0: @@ Class             1248 sun.security.pkcs11.SunPKCS11");
		aotCacheParser.accept("0x00000008019c1890: @@ Class             584 sun.security.pkcs11.SunPKCS11$$Lambda/0x8000000cf");
		aotCacheParser.accept("0x00000008019c1b48: @@ Method            88 void sun.security.pkcs11.SunPKCS11$$Lambda/0x8000000cf.<init>()");
		aotCacheParser.accept("0x00000008019c1be0: @@ Method            88 java.lang.Object sun.security.pkcs11.SunPKCS11$$Lambda/0x8000000cf.apply(java.lang.Object)");

		var sunPKCS = aotCache.getElements("sun.security.pkcs11.SunPKCS11", null, null, false, false, "Class").toList();
		var lambdaClass = aotCache.getElements("sun.security.pkcs11.SunPKCS11$$Lambda/0x8000000cf", null, null, false, false, "Class").toList();
		assertFalse(lambdaClass.isEmpty());
		ReferencingElement lambda = (ReferencingElement) lambdaClass.getFirst();
		assertEquals(1, lambda.getReferences().size());
		assertEquals(lambda.getReferences().getFirst(), sunPKCS.getFirst());
		var methods = aotCache.getElements(null, null, null, false, false, "Method").toList();
		for (Element method : methods) {
			assertEquals(lambda, ((MethodObject) method).getClassObject());
		}
	}

	@Test
	void acceptTrainingData() {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var aotCache = loadFile.getParent().getInformation();
		AOTMapParser aotCacheParser = new AOTMapParser(loadFile);

		aotCacheParser.accept("0x0000000800ede8d0: @@ Class             1248 java.util.logging.LogManager");
		aotCacheParser.accept("0x0000000801bc7e40: @@ KlassTrainingData 40 java.util.logging.LogManager");
		aotCacheParser.accept("0x0000000801bcb1a8: @@ KlassTrainingData 40 java.lang.classfile.AttributeMapper$AttributeStability");
		aotCacheParser.accept("0x00000008019c1b48: @@ Class            88 java.lang.classfile.AttributeMapper$AttributeStability");
		aotCacheParser.accept("0x0000000800b5b3a8: @@ Method            88 org.apache.logging.log4j.spi.LoggerContext org.apache.logging.log4j.LogManager.getContext(boolean)");
		aotCacheParser.accept("0x0000000801a23fc8: @@ MethodTrainingData 96 org.apache.logging.log4j.spi.LoggerContext org.apache.logging.log4j.LogManager.getContext(boolean)");

		var klassTrainingData = aotCache.getElements(null, null, null, false, false, "KlassTrainingData").toList();
		assertEquals(2, klassTrainingData.size());

		for (Element e : klassTrainingData) {
			ReferencingElement re = (ReferencingElement) e;
			assertEquals(1, re.getReferences().size());
			var classObj = re.getReferences().getFirst();
			assertInstanceOf(ClassObject.class, classObj);
			assertEquals(classObj.getKey(), re.getKey());
		}

		var methodTrainingData = aotCache.getElements(null, null, null, false, false, "MethodTrainingData").toList();
		assertEquals(1, methodTrainingData.size());

		for (Element e : methodTrainingData) {
			ReferencingElement re = (ReferencingElement) e;
			assertEquals(1, re.getReferences().size());
			assertEquals(re.getReferences().getFirst().getKey(), re.getKey());
		}

		//Now check we don't break on empty class name
		aotCache.clear();

		aotCacheParser.accept("0x0000000801d14768: @@ KlassTrainingData 40");
		aotCacheParser.accept("0x0000000801cd0518: @@ MethodTrainingData 96");

		var trainingData = aotCache.getElements(null, null, null, false, false, "MethodTrainingData",
				"KlassTrainingData").toList();
		assertEquals(2, trainingData.size());

		for (Element e : trainingData) {
			ReferencingElement re = (ReferencingElement) e;
			assertEquals(0, re.getReferences().size());
		}
	}


	@Test
	void acceptCompileTrainingData() {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		final var aotCache = loadFile.getParent().getInformation();
		AOTMapParser aotCacheParser = new AOTMapParser(loadFile);

		aotCacheParser.accept("0x00000008007cd538: @@ Class             1448 java.util.concurrent.ConcurrentHashMap");
		aotCacheParser.accept("0x00000008019d8e00: @@ MethodTrainingData 96 int java.util.concurrent.ConcurrentHashMap.spread(int)");
		aotCacheParser.accept("0x0000000801cb2218: @@ MethodCounters    64 int java.util.concurrent.ConcurrentHashMap.spread(int)");
		aotCacheParser.accept("0x0000000801cb2258: @@ Method            88 int java.util.concurrent.ConcurrentHashMap.spread(int)");
		aotCacheParser.accept("0x0000000801cb22b0: @@ MethodData        248 int java.util.concurrent.ConcurrentHashMap.spread(int)");
		aotCacheParser.accept("0x0000000801cb23a8: @@ MethodCounters    64 int java.util.concurrent.ConcurrentHashMap.spread(int)");
		aotCacheParser.accept("0x0000000801cb23e8: @@ CompileTrainingData 80 4 int java.util.concurrent.ConcurrentHashMap.spread(int)");
		aotCacheParser.accept("0x0000000801cb2438: @@ CompileTrainingData 80 3 int java.util.concurrent.ConcurrentHashMap.spread(int)");
		aotCacheParser.accept("0x000000080471b9c8: @@ ConstMethod       88 int java.util.concurrent.ConcurrentHashMap.spread(int)");

		var compileTrainingData = aotCache.getElements(null, null, null, false, false, "CompileTrainingData").toList();
		assertEquals(2, compileTrainingData.size());

		MethodObject method =
				(MethodObject) aotCache.getElements("int java.util.concurrent.ConcurrentHashMap.spread(int)",
								null, null, false, false, "Method")
				.findAny().get();
		assertEquals(2, method.getCompileTrainingData().size());
		assertNotNull(method.getCompileTrainingData().get(3));
		assertNotNull(method.getCompileTrainingData().get(4));

		for (Element e : compileTrainingData) {
			ReferencingElement re = (ReferencingElement) e;
			assertEquals(1, re.getReferences().size());
			assertEquals(method, re.getReferences().getFirst());
		}

		//Now check we don't break on empty class name
		aotCache.clear();

		aotCacheParser.accept("0x0000000801cb2438: @@ CompileTrainingData 80");

		var trainingData = aotCache.getElements(null, null, null, true, true, "CompileTrainingData").toList();
		assertEquals(1, trainingData.size());

		for (Element e : trainingData) {
			ReferencingElement re = (ReferencingElement) e;
			assertEquals(0, re.getReferences().size());
		}
	}

}