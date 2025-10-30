package tooling.leyden.commands;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tooling.leyden.aotcache.Warning;
import tooling.leyden.commands.logparser.AOTMapParser;
import tooling.leyden.commands.logparser.TrainingLogParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WarningCommandTest extends DefaultTest {

	private static WarningCommand warningCommand;
	private static AOTMapParser aotCacheParser;
	private static TrainingLogParser trainingParser;

	@BeforeAll
	static void init() {
		final var loadFile = new LoadFileCommand();
		loadFile.setParent(getDefaultCommand());
		trainingParser = new TrainingLogParser(loadFile);
		aotCacheParser = new AOTMapParser(loadFile);
		warningCommand = new WarningCommand();
		warningCommand.parent = getDefaultCommand();
	}

	@Test
	void checkUsedAndNotTrained() {
		aotCacheParser.accept("0x0000000801711128: @@ Class             624 org.infinispan.xsite.NoOpBackupSender");

		//Method not called
		aotCacheParser.accept("0x00000008017116c0: @@ Method            88 org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()");

		//Method with no compiledTrainingData
		aotCacheParser.accept("0x0000000801711610: @@ Method            88 void org.infinispan.xsite.NoOpBackupSender.<init>()");
		aotCacheParser.accept("0x0000000801b3d568: @@ MethodTrainingData 96 org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()");
		aotCacheParser.accept("0x0000000801b3d5f0: @@ MethodData        240 org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()");
		aotCacheParser.accept("0x0000000801b3d6e0: @@ MethodCounters    64 org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()");
		aotCacheParser.accept("0x000000080429e780: @@ ConstMethod       64 org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()");

		List<Warning> warningList = warningCommand.getTopPackagesUsedAndNotTrained();
		assertEquals(1, warningList.size());

		//Now we add the compiledTrainingData
		aotCacheParser.accept("0x0000000801cd5648: @@ CompileTrainingData 80 1 org.infinispan.xsite.NoOpBackupSender org.infinispan.xsite.NoOpBackupSender.getInstance()");

		assertTrue(warningCommand.getTopPackagesUsedAndNotTrained().isEmpty());
	}

	@Test
	void filter() {

		trainingParser.accept("[warning][aot] Skipping org/apache/logging/log4j/core/async/AsyncLoggerContext: Failed " +
				"verification");
		trainingParser.accept("[warning][aot] Skipping com/thoughtworks/xstream/security/ForbiddenClassException: Unlinked " +
				"class not supported by AOTConfiguration");
		trainingParser.accept("[info][aot       ] JVM_StartThread() ignored: java.lang.ref.Reference$ReferenceHandler");
		trainingParser.accept("[warning][aot       ] Preload Warning: Verification failed for org.infinispan.remoting" +
				".transport.jgroups.JGroupsRaftManager");
		trainingParser.accept("[warning][aot       ] Preload Warning: Verification failed for org.apache.logging.log4j.core" +
				".async.AsyncLoggerContext");

		assertEquals(5, warningCommand.getWarnings().size());

		warningCommand.limit = 1;
		assertEquals(1, warningCommand.getWarnings().size());

		warningCommand.limit = null;
		warningCommand.name = "org.apache.logging.log4j.core.async.AsyncLoggerContext";
		assertEquals(2, warningCommand.getWarnings().size());

		warningCommand.name = "java.lang.ref.Reference$ReferenceHandler";
		assertEquals(1, warningCommand.getWarnings().size());
	}
}