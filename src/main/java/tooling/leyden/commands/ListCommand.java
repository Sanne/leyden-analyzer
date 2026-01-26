package tooling.leyden.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tooling.leyden.aotcache.*;
import tooling.leyden.commands.autocomplete.WhichRun;

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

	public void run() {
		final var counter = new AtomicInteger();
		final var elements = findElements(counter);

		elements.forEach(element -> element.toAttributedString().println(parent.getTerminal()));
		parent.getOut().println("Found " + counter.get() + " elements.");
	}

	protected Stream<Element> findElements(AtomicInteger counter) {
		Stream<Element> elements;

		switch (parameters.use) {
			case both -> elements = parent.getInformation().getElements(parameters, true);
			case notCached -> elements = Information.getMyself().filterByParams(
					parameters,
					parent.getInformation().getExternalElements().entrySet().parallelStream()
							.filter(keyElementEntry -> parameters.getName().isBlank()
									|| keyElementEntry.getKey().identifier().equalsIgnoreCase(parameters.getName()))
							.map(keyElementEntry -> keyElementEntry.getValue()));
			default -> elements = parent.getInformation().getElements(parameters, false);
		}

		elements = elements.sorted(Comparator.comparing(Element::getKey).thenComparing(Element::getType));
		elements = elements.peek(item -> counter.incrementAndGet());

		return elements;
	}
}