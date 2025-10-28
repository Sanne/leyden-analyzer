package tooling.leyden.commands.logparser;

import tooling.leyden.aotcache.ClassObject;
import tooling.leyden.aotcache.Configuration;
import tooling.leyden.aotcache.ConstantPoolObject;
import tooling.leyden.aotcache.Element;
import tooling.leyden.aotcache.Information;
import tooling.leyden.aotcache.MethodObject;
import tooling.leyden.aotcache.ReferencingElement;
import tooling.leyden.aotcache.WarningType;
import tooling.leyden.commands.LoadFileCommand;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * This class is capable of parsing (certain) Java logs.
 */
public class LogParser implements Consumer<String> {

	private final Information information;
	private final String thisSource;

	public LogParser(LoadFileCommand loadFile) {
		this.information = loadFile.getParent().getInformation();
		thisSource = "Java Log";
	}

	@Override
	public void accept(String content) {
		// -Xlog:class+load,aot:file=aot.log:level,tags
		// Logs in Java have the following form:
		// [level][tag1, tag2,...] Log message

		String level = null;
		String[] tags = new String[]{};

		if (content.indexOf("[") >= 0 && content.indexOf("]") > 0) {
			level = content.substring(content.indexOf("[") + 1, content.indexOf("]"));
			content = content.substring(content.indexOf("]") + 1);
		}

		if (content.indexOf("[") >= 0 && content.indexOf("]") > 0) {
			tags = content.substring(content.indexOf("[") + 1, content.indexOf("]"))
					.trim()
					.split("\\s*,\\s*");
		}

		final String message = content.substring(content.indexOf("]") + 1);
		// [info][class,load] java.lang.invoke.DelegatingMethodHandle$Holder source: shared objects file
		final var trimmedMessage = message.trim();
		if (containsTags(tags, "class", "load")) {
			if (message.contains(" source: ")) {
				String className = message.substring(0, message.indexOf("source: ")).trim();
				Element e;
				if (message.indexOf("source: shared objects file") > 0) {
					if (className.contains("$$Lambda/")) {
						//This is a lambda
						this.information.getStatistics().incrementValue("[LOG] Lambda Methods loaded from AOT Cache");
					}
					var classes = information.getElements(className, null, null, true, true, "Class").findAny();
					if (classes.isEmpty()) {
						//WARNING: this should be covered by the aot map file
						//we are assuming no aot map file was loaded at this point
						//so we create a basic placeholder
						e = new ClassObject(className);
					} else {
						e = classes.get();
					}
					this.information.getStatistics().incrementValue("[LOG] Classes loaded from AOT Cache");
					information.addAOTCacheElement(e, thisSource);

				} else {
					// else this wasn't loaded from the aot.map
					if (className.contains("$$Lambda/")) {
						this.information.getStatistics().incrementValue("[LOG] Lambda Methods not loaded from AOT Cache");
					}
					this.information.getStatistics().incrementValue("[LOG] Classes not loaded from AOT Cache");

					e = new ClassObject(className);
					information.addExternalElement(e, thisSource);
				}
				e.addWhereDoesItComeFrom(content.substring(content.indexOf("source: ")));
			}
		} else if (containsTags(tags, "aot")) {
			if (containsTags(tags, "codecache")) {
				if (containsTags(tags, "init")) {
					if (trimmedMessage.startsWith("Loaded ")
							&& trimmedMessage.endsWith("AOT code entries from AOT Code Cache")) {
						//[info ][aot,codecache,init] Loaded 493 AOT code entries from AOT Code Cache
						information.getStatistics().addValue("[LOG] [CodeCache] Loaded AOT code entries",
								trimmedMessage.substring(7, trimmedMessage.substring(7).indexOf(" ") + 7));
					} else if (trimmedMessage.contains(" total=")) {
						//[debug][aot,codecache,init]   Adapters:  total=493
						//[debug][aot,codecache,init]   Shared Blobs: total=0
						//[debug][aot,codecache,init]   C1 Blobs: total=0
						//[debug][aot,codecache,init]   C2 Blobs: total=0
						information.getStatistics().addValue("[LOG] [CodeCache] Loaded " + trimmedMessage.substring(0,
								trimmedMessage.indexOf(":")).trim(), trimmedMessage.substring(trimmedMessage.indexOf(
								"total=") + 6));
					} else if (trimmedMessage.startsWith("AOT code cache size:")) {
						//[debug][aot,codecache,init]   AOT code cache size: 598432 bytes
						information.getStatistics().addValue("[LOG] [CodeCache] AOT code cache size", trimmedMessage.substring(20).trim());
					}
				}
			} else if (containsTags(tags, "resolve")) {
				if (level.equals("trace")) {
					if (trimmedMessage.startsWith("archived")) {
						processAotTraceResolve(trimmedMessage);
					}
				}
			}
			if (trimmedMessage.startsWith("Skipping ")) {
				processSkipping(message);
			} else if (level.equals("warning")) {
				processWarning(trimmedMessage);
			} else if (level.equals("error")) {
				processError(trimmedMessage);
			} else if (level.equals("info")) {
				processInfo(trimmedMessage);
			}
		}
	}

	private void processAotTraceResolve(String trimmedMessage) {
		final var splitMessage = trimmedMessage.substring(trimmedMessage.indexOf("]: ") + 2).trim().split("\\s+");

		//First we find the Symbol related to
		final var parentClassName = splitMessage[0];
		var elementSearch = information.getElements(parentClassName, null, null, true, true, "Symbol").findAny();
		ReferencingElement parentSymbol;
		if (elementSearch.isPresent()) {
			parentSymbol = (ReferencingElement) elementSearch.get();
		} else {
			parentSymbol = new ReferencingElement(parentClassName, "Symbol");
			information.addExternalElement(parentSymbol, thisSource);
		}

		// If a class already exists with this Symbol, link it. If not, ignore it.
		// We will do the heavy creation work on AOT Parser, if any is loaded
		// because at this point, we don't know anything about the class... except the name
		// is it cached? is it not? Who knows with this information?
		var classObj = information.getElements(parentClassName.replaceAll("/", "."), null, null, true, true,
				"Class").findAny();
		if (classObj.isPresent()) {
			((ClassObject) classObj.get()).addSymbol(parentSymbol);
			parentSymbol.addReference(classObj.get());

			// Now search for the corresponding ConstantPool and, if exists, link this class to its poolHolder
			// again, don't create it, just... wait for an AOT Cache map file if it does not exist yet
			var cp = information.getElements(parentClassName.replaceAll("/", "."), null, null, true, true,
					"ConstantPool").findAny();
			if (cp.isPresent()) {
				((ConstantPoolObject) cp.get()).setPoolHolder((ClassObject) classObj.get());
			}
		}

		if (trimmedMessage.startsWith("archived klass")) {
//	archived klass  CP entry [  2]: org/infinispan/rest/framework/impl/InvocationImpl unreg => java/lang/Object boot
			findOrCreateSymbolAndLinkToParent(parentSymbol,
					"Used by " + parentSymbol.getKey() + " " + splitMessage[4] + ".", splitMessage[3]);
		} else if (trimmedMessage.startsWith("archived field")) {
// archived field  CP entry [ 20]: org/infinispan/rest/framework/impl/InvocationImpl => org/infinispan/rest/framework/impl/InvocationImpl.action:Ljava/lang/String;
			final var names = splitMessage[2].split(":");
			final String source = "Used by a field in " + names[0] + ".";
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[1]);
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[0].substring(0, names[0].lastIndexOf(".")));
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[0].substring(names[0].lastIndexOf(".") + 1));
		} else if (trimmedMessage.startsWith("archived method")
				|| trimmedMessage.startsWith("archived interface method")) {
// archived interface method CP entry [ 13]: jdk/jfr/internal/jfc/model/XmlNot java/util/List.size:()I => java/util/List
// archived method CP entry [338]: jdk/jfr/internal/dcmd/DCmdStart jdk/jfr/internal/dcmd/Argument.<init>
// :(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZLjava/lang/String;Z)V => jdk/jfr/internal/dcmd/Argument
			final var names = splitMessage[1].split(":");
			final String source = "Used by method " + names[0] + ".";
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[0].substring(0, names[0].lastIndexOf(".")));
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[0].substring(names[0].lastIndexOf(".") + 1));
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[1]);
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, splitMessage[3]);
		} else if (trimmedMessage.startsWith("archived indy ")) {
// archived indy   CP entry [294]: jdk/jfr/internal/dcmd/DCmdDump (0) => java/lang/invoke/LambdaMetafactory.metafactory:
// (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
			final var names = splitMessage[3].split(":");
			final String source = "Used by indy " + splitMessage[0] + ".";
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[0].substring(0, names[0].lastIndexOf(".")));
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[0].substring(names[0].lastIndexOf(".") + 1));
			findOrCreateSymbolAndLinkToParent(parentSymbol, source, names[1]);
		}
	}

	private void findOrCreateSymbolAndLinkToParent(ReferencingElement parentSymbol, String source, String symbolName) {
		java.util.Optional<Element> elementSearch;
		ReferencingElement referencedSymbol;
		elementSearch = information.getElements(symbolName, null, null, true, true, "Symbol").findAny();
		if (elementSearch.isPresent()) {
			referencedSymbol = (ReferencingElement) elementSearch.get();
		} else {
			referencedSymbol = new ReferencingElement(symbolName, "Symbol");
			information.addExternalElement(referencedSymbol, thisSource);
		}

		// If a class already exists with this Symbol, link it. If not, ignore it.
		// We will fill it when an AOT Cache loads, if it loads
		// (maybe it is not even a class, so don't care if this fails)
		var classObj = information.getElements(symbolName.replaceAll("/", "."), null, null, true, true,
				"Class").findAny();
		if (classObj.isPresent()) {
			((ClassObject) classObj.get()).addSymbol(referencedSymbol);
			referencedSymbol.addReference(classObj.get());
		}

		referencedSymbol.addWhereDoesItComeFrom(source);
		parentSymbol.addReference(referencedSymbol);
	}

	private void processWarning(String trimmedMessage) {
		if (trimmedMessage.startsWith("The AOT cache was created by a different")) {
			information.addWarning(null, trimmedMessage, WarningType.CacheLoad);
		} else {
			//Very generic, but at least catch things
			information.addWarning(null, trimmedMessage, WarningType.Unknown);
		}
	}

	private void processError(String trimmedMessage) {
		if (trimmedMessage.startsWith("An error has occurred while processing the AOT cache")
				|| trimmedMessage.equals("Loading static archive failed.")
				|| trimmedMessage.equals("Unable to map shared spaces")) {
			information.addWarning(null, trimmedMessage, WarningType.CacheLoad);
		} else {
			//Very generic, but at least catch things
			information.addWarning(null, trimmedMessage, WarningType.Unknown);
		}
	}

	private void processInfo(String trimmedMessage) {
		if (trimmedMessage.startsWith("Core region alignment:")) {
//	[info][aot] Core region alignment: 4096
			information.getConfiguration().addValue("Core region alignment", trimmedMessage.substring(23));
		} else if (trimmedMessage.startsWith("The AOT configuration file was created with ")) {
//[info][aot] The AOT configuration file was created with UseCompressedOops = 1, UseCompressedClassPointers = 1, UseCompactObjectHeaders = 0
			String[] config = trimmedMessage.split(" ");
			for (int i = 8; i < config.length - 1; i++) {
				if (config[i].equals("=")) {
					information.getConfiguration().addValue(config[i - 1], config[i + 1].replace(",", ""));
				}
			}
		} else if (trimmedMessage.startsWith("ArchiveRelocationMode:")) {
//[info][aot] ArchiveRelocationMode: 1 # always map archive(s) at an alternative address
			information.getConfiguration().addValue("ArchiveRelocationMode", trimmedMessage.substring(22));
		} else if (trimmedMessage.startsWith("Reserved") && trimmedMessage.contains("bytes")) {
			if (trimmedMessage.contains("at 0x")) {
//[info][aot       ] Reserved output buffer space at 0x00007f5702e00000 [1084227584 bytes]
				storeConfigurationSplitByCharacter(information.getAllocation(), trimmedMessage, "at", false);
			} else {
//[info][aot] Reserved archive_space_rs [0x0000000057000000 - 0x000000005c000000] (83886080) bytes (includes protection zone)
//[info][aot] Reserved class_space_rs   [0x000000005c000000 - 0x000000009c000000] (1073741824) bytes
				storeConfigurationSplitByCharacter(information.getAllocation(), trimmedMessage, "[", true);
			}
		} else if (trimmedMessage.startsWith("Mapped static")) {
//[info][aot] Mapped static  region #0 at base 0x0000000057001000 top 0x0000000058fbe000 (ReadWrite)
//[info][aot] Mapped static  region #1 at base 0x0000000058fbe000 top 0x000000005bd99000 (ReadOnly)
//[info][aot] Mapped static  region #2 at base 0x00007f57574fe000 top 0x00007f5757600000 (Bitmap)
			var key = trimmedMessage.substring(0, trimmedMessage.indexOf("at base"));
			var value = trimmedMessage.substring(trimmedMessage.indexOf("0x"));
			information.getAllocation().addValue(key, value);
		} else if (trimmedMessage.startsWith("archived module property")) {
//[info][aot] archived module property jdk.module.main: (null)
//[info][aot] archived module property jdk.module.addexports: java.naming/com.sun.jndi.ldap=ALL-UNNAMED
//[info][aot] archived module property jdk.module.enable.native.access: ALL-UNNAMED
			storeConfigurationSplitByCharacter(information.getConfiguration(), trimmedMessage, ":", false);
		} else if (trimmedMessage.startsWith("initial ") && trimmedMessage.indexOf(":") > 0) {
//[info][aot] initial optimized module handling: enabled
//[info][aot] initial full module graph: disabled
			storeConfigurationSplitByCharacter(information.getConfiguration(), trimmedMessage, ":", false);
		} else if (trimmedMessage.startsWith("Using AOT-linked classes: ")) {
//[info][aot] Using AOT-linked classes: false (static archive: no aot-linked classes)
			//Maybe we should be more explicit on the info command about this
			storeConfigurationSplitByCharacter(information.getConfiguration(), trimmedMessage, ":", false);
		} else if (trimmedMessage.startsWith("JVM_StartThread() ignored:")) {
//[info][aot       ] JVM_StartThread() ignored: java.lang.ref.Reference$ReferenceHandler
			this.information.addWarning(null, trimmedMessage, WarningType.CacheCreation);
		} else if (trimmedMessage.startsWith("Heap range = ")
				|| trimmedMessage.startsWith("heap range")) {
//[info][aot       ] Heap range = [0x00000000e0000000 - 0x0000000100000000]
			storeConfigurationSplitByCharacter(information.getAllocation(), trimmedMessage, "=", false);
		} else if (trimmedMessage.startsWith("string table array (single level) length")) {
//[info][aot       ] Heap range = [0x00000000e0000000 - 0x0000000100000000]
			storeConfigurationSplitByCharacter(information.getStatistics(), trimmedMessage, "=", false);
		} else if (trimmedMessage.startsWith("Archived")) {
//[info][aot       ] Archived 4797 interned strings
//[info][aot       ] Archived 97 method handle intrinsics (26392 bytes)
			var msg = trimmedMessage.split(" ");
			var value = msg[1];
			msg[0] = "";
			msg[1] = "";
			information.getStatistics().addValue(String.join(" ", msg), value);
		} else if (trimmedMessage.startsWith("Shared file region (")) {
//[info][aot       ] Shared file region (rw) 0: 31818032 bytes, addr 0x0000000800001000 file offset 0x00001000 crc 0xc67c8575
//[info][aot       ] Shared file region (ro) 1: 47394376 bytes, addr 0x0000000801e5a000 file offset 0x01e5a000 crc 0xf5404ff5
//[info][aot       ] Shared file region (ac) 4:   607256 bytes, addr 0x0000000804b8d000 file offset 0x04b8d000 crc 0x5b0ab513
//[info][aot       ] Shared file region (bm) 2:  1060832 bytes, addr 0x0000000000000000 file offset 0x04c22000 crc 0x57b8467c
//[info][aot       ] Shared file region (hp) 3:  1481952 bytes, addr 0x00000000ffe00000 file offset 0x04d25000 crc 0x44000459
			var msg = trimmedMessage.split("\\s+");
			var key = trimmedMessage.substring(0, 25);
			information.getConfiguration().addValue(key + " size", msg[5] + " " + msg[6].replace(",", ""));
			information.getAllocation().addValue(key + " addr", msg[8]);
			information.getAllocation().addValue(key + " file offset", msg[11]);
			information.getConfiguration().addValue(key + " crc", msg[13]);
		} else if (trimmedMessage.startsWith("Number of classes")) {
//[info][aot       ] Number of classes 10857
			information.getStatistics().addValue(trimmedMessage.substring(0, trimmedMessage.lastIndexOf(" ")),
					trimmedMessage.substring(trimmedMessage.lastIndexOf(" ") + 1));
		} else if (trimmedMessage.indexOf(" = ") > 0) {
			//This is very generic processing, but, why not?
			//What's the worst that can happen? :)

//[info][aot       ]     instance classes   = 10170, aot-linked =  3059, inited =   422
//[info][aot       ]       boot             =  3010, aot-linked =  3010, inited =   420
//[info][aot       ]         vm             =   151, aot-linked =   151, inited =    42
//[info][aot       ]       platform         =    45, aot-linked =    45, inited =     2
//[info][aot       ]       app              =     4, aot-linked =     4, inited =     0
//[info][aot       ]       unregistered     =  7111, aot-linked =     0, inited =     0
//[info][aot       ]       (enum)           =   382, aot-linked =    82, inited =     8
//[info][aot       ]       (hidden)         =   272, aot-linked =   272, inited =   272
//[info][aot       ]       (old)            =    16, aot-linked =     0, inited =     0
//[info][aot       ]       (unlinked)       =  7111, boot = 0, plat = 0, app = 0, unreg = 7111
//[info][aot       ]     obj array classes  =   678
//[info][aot       ]     type array classes =     9
//[info][aot       ]                symbols = 189961
//[info][aot       ] Full module graph = enabled
//[info][aot       ] Class  CP entries = 127257, archived =  20941 ( 16,5%), reverted =      0
//[info][aot       ] Field  CP entries =  44366, archived =  11143 ( 25,1%), reverted =      0
//[info][aot       ] Method CP entries =  15059, archived =  14986 ( 99,5%), reverted =     73
//[info][aot       ] Indy   CP entries =    218, archived =    218 (100,0%), reverted =      0
//[info][aot       ] Platform loader initiated classes =   1583
//[info][aot       ] App      loader initiated classes =   1617
//[info][aot       ] MethodCounters                    =   8942 (  572288 bytes)
//[info][aot       ] KlassTrainingData                 =   1798 (   71920 bytes)
//[info][aot       ] MethodTrainingData                =   5615 (  539040 bytes)
//[info][aot       ] Size of heap region = 1481952 bytes, 30415 objects, 5388 roots, 4131 native ptrs
//[info][aot       ] oopmap =       4 ...  370488 (  0% ... 100% =  99%)
//[info][aot       ] ptrmap =   75961 ...  132749 ( 41% ...  71% =  30%)
			String[] toProcess = new String[]{trimmedMessage};
			if (trimmedMessage.contains(",")) {
				toProcess = trimmedMessage.split(",");
			}

			String firstKey = null;
			for (String s : toProcess) {
				if (s.indexOf("=") == s.lastIndexOf("=") && s.indexOf("=") > 0) {
					//we should have a string in the form 'something = thingy'
					//if this is not the first element on the array, append the first element for context
					final var message = s.trim();
					if (firstKey != null) {
						s = firstKey + s;
					} else {
						firstKey = s.substring(0, s.indexOf(" =")).trim() + " ";
					}
					storeConfigurationSplitByCharacter(information.getStatistics(), s, " = ", false);
				}
			}
		}
		//TODO Difficult to process because it doesn't come in one line:
//[info][aot       ] Allocating RW objects ...
//[info][aot       ] done (218321 objects)
//[info][aot       ] Allocating RO objects ...
//[info][aot       ] done (432657 objects)

	}

	private void storeConfigurationSplitByCharacter(Configuration config, String trimmedMessage, String character,
													boolean includeCharacter) {
		var key = trimmedMessage.substring(0, trimmedMessage.indexOf(character));
		var value = trimmedMessage.substring(
				trimmedMessage.indexOf(character) + (includeCharacter ? 0 : character.length() + 1));
		config.addValue(key, value);
	}

	//[1055.926s][warning][aot] Skipping org/apache/logging/log4j/core/async/AsyncLoggerContext: Failed verification
//[1055.928s][warning][aot] Skipping org/apache/logging/slf4j/Log4jLoggerFactory$$Lambda+0x800000258: nest_host class org/apache/logging/slf4j/Log4jLoggerFactory is excluded
//[1055.928s][warning][aot] Skipping jdk/proxy1/$Proxy29: Unsupported location
//[1055.929s][warning][aot] Skipping org/slf4j/ILoggerFactory: Old class has been linked
//[1055.929s][warning][aot] Skipping jdk/internal/event/SecurityProviderServiceEvent: JFR event class
//[1055.929s][warning][aot] Skipping com/thoughtworks/xstream/security/ForbiddenClassException: Unlinked class not supported by AOTConfiguration
//[warning][aot] Skipping java/lang/invoke/BoundMethodHandle$Species_LI because it is dynamically generated
	private void processSkipping(String message) {
		String[] msg = message.trim().split("\\s+");
		String className = msg[1].replace("/", ".").replace(":", "").trim();
		msg[0] = "";
		msg[1] = "";
		String reason = String.join(" ", msg).trim();
		Element element = null;
		if (className.contains("$$")) {

			var elements = information.getElements(className.replace("$$", "."), null, null, true, true, "Method").findAny();
			if (elements.isPresent()) {
				element = elements.get();
			} else {
				elements = information.getElements(className.substring(0, className.indexOf("$$")), null, null, true,
						true, "Class").findAny();
				if (elements.isPresent()) {
					element = elements.get();
				} else {
					element = new MethodObject();
					((MethodObject) element).setName(className);
				}
			}
		} else {
			var elements = information.getElements(className, null, null, true, true, "Class").findAny();
			if (elements.isPresent()) {
				element = elements.get();
			} else {
				element = new ClassObject(className);
			}
		}
		information.addWarning(element, reason, WarningType.CacheCreation);
	}

	private boolean containsTags(String[] tags, String... wantedTags) {
		return Arrays.asList(tags).containsAll(Arrays.asList(wantedTags));
	}
}
