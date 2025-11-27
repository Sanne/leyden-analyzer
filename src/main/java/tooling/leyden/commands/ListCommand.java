package tooling.leyden.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tooling.leyden.aotcache.ClassObject;
import tooling.leyden.aotcache.Element;
import tooling.leyden.aotcache.Information;
import tooling.leyden.aotcache.MethodObject;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Command(name = "ls", mixinStandardHelpOptions = true,
		version = "1.0",
		description = {"List what is on the cache. By default, it lists everything on the cache."},
		subcommands = {CommandLine.HelpCommand.class})
class ListCommand implements Runnable {

	@CommandLine.ParentCommand
	DefaultCommand parent;

	@CommandLine.Mixin
	protected CommonParameters parameters;

	@CommandLine.Option(names = {"--trained"},
			description = {"Only displays elements with training information.",
					"This may restrict the types of elements shown, along with what was passed as parameters."},
			defaultValue = "false",
			arity = "0..1")
	protected Boolean trained;

	@CommandLine.Option(names = {"--run"},
			description = {"Only displays methods that were run on the training run."},
			defaultValue = "false",
			arity = "0..1")
	protected Boolean run;

	@CommandLine.Option(names = {"--showLambdas"},
			description = {"Display lambda classes."},
			defaultValue = "false",
			arity = "0..1")
	protected Boolean showLambdas;

	public void run() {
		final var counter = new AtomicInteger();
		final var elements = findElements(counter);

		elements.forEach(element -> element.toAttributedString().println(parent.getTerminal()));
		parent.getOut().println("Found " + counter.get() + " elements.");
	}

	protected Stream<Element> findElements(AtomicInteger counter) {
		Stream<Element> elements;

		switch (parameters.use) {
			case both -> elements = parent.getInformation().getElements(parameters.getName(), parameters.packageName,
					parameters.excludePackageName, parameters.showArrays, true, parameters.types);
			case notCached ->
					elements = Information.getMyself().filterByParams(
							parameters.packageName, parameters.excludePackageName, parameters.showArrays, parameters.types,
							parent.getInformation().getExternalElements().entrySet().parallelStream()
									.filter(keyElementEntry -> parameters.getName().isBlank()
											|| keyElementEntry.getKey().identifier().equalsIgnoreCase(parameters.getName()))
									.map(keyElementEntry -> keyElementEntry.getValue()));
			default -> elements = parent.getInformation().getElements(parameters.getName(), parameters.packageName,
					parameters.excludePackageName, parameters.showArrays, false, parameters.types);
		}


		if (trained) {
			elements = elements.filter(e -> e.isTraineable() && e.isTrained());
		}

		if (run) {
			elements = elements
					.filter(e -> e.getType().equalsIgnoreCase("Method"))
					.filter(e -> ((MethodObject) e).getMethodCounters() != null);
		}

		if (!showLambdas) {
			elements = elements
					.filter(e -> {
							if (e instanceof ClassObject classObject) {
								return !classObject.getName().contains("$$Lambda");
							} else {
								return true;
							}
					});
		}

		elements = elements.sorted(Comparator.comparing(Element::getKey).thenComparing(Element::getType));
		elements = elements.peek(item -> counter.incrementAndGet());

		return elements;
	}
}