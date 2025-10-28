package tooling.leyden.commands;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import tooling.leyden.aotcache.ClassObject;
import tooling.leyden.aotcache.ConstantPoolObject;
import tooling.leyden.aotcache.Information;
import tooling.leyden.aotcache.Element;
import tooling.leyden.aotcache.ReferencingElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Command(name = "tree", mixinStandardHelpOptions = true,
		version = "1.0",
		description = {"Shows the dependency graph of a class.",
				"By default, only classes will be shown, as symbols associated will be resolved to classes.",
				"This means, elements that refer to/use the root element. ",
				"Blue italic elements have already been shown and will not be expanded."},
		subcommands = {CommandLine.HelpCommand.class})
class TreeCommand implements Runnable {

	@CommandLine.ParentCommand
	DefaultCommand parent;

	@CommandLine.Mixin
	private CommonParameters parameters;

	@CommandLine.Option(names = {"-l", "--level"},
			description = {"Maximum number of tree levels to display."},
			defaultValue = "3",
			arity = "0..*",
			paramLabel = "<N>")
	protected Integer level;

	@CommandLine.Option(names = {"-max"},
			description = {"Maximum number of elements to display. By default, 100. If using -1, it shows all " +
					"elements. Note that on some cases this may mean showing thousands of elements."},
			defaultValue = "100",
			arity = "0..*",
			paramLabel = "<N>")
	protected Integer max;

	@CommandLine.Option(names = {"-r", "--reverse"},
			description = {
					"Show which classes are using this element instead of which classes are used by this element."},
			defaultValue = "false",
			arity = "0..1",
			paramLabel = "<true>")
	protected Boolean reverse;

	public void run() {

		if (parameters.getName() == null || parameters.getName().isBlank()) {
			(new AttributedString("ERROR: You must specify the name of the class to build the graph.",
					AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold())).println(parent.getTerminal());
			return;
		}

		if (parameters.types == null) {
			parameters.types = new String[]{"Class", "Object"};
		}

		List<Element> elements = parent.getInformation().getElements(parameters.getName(), parameters.packageName,
				parameters.excludePackageName,
				parameters.showArrays, parameters.useNotCached, "Class").toList();

		if (!elements.isEmpty()) {
			elements.forEach(e -> {
				(new AttributedString("Calculating dependency graph... ",
						AttributedStyle.DEFAULT)).println(parent.getTerminal());
				parent.getTerminal().flush();
				(new AttributedString("+ ")).print(parent.getTerminal());
				e.toAttributedString().println(parent.getTerminal());
				printReferrals(e, "  ", new ArrayList<>(List.of(e)), 0);
			});
			parent.getTerminal().flush();
		} else {
			(new AttributedString("ERROR: Element not found. Try looking for it with ls.",
					AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold())).println(parent.getTerminal());
		}
	}

	private void printReferrals(Element root, String leftPadding, List<Element> travelled, Integer level) {
		if (level > this.level || (max > 0 && travelled.size() > max))
			return;
		level++;

		boolean isFirst = true;
		for (Element refer : getElementsReferencingThisOne(root)) {
			AttributedStringBuilder asb = new AttributedStringBuilder();

			if (isFirst) {
				asb.append(leftPadding.substring(0, leftPadding.length() - 1) + '\\');
				asb.append(AttributedString.NEWLINE);
			} else {
				asb.append(leftPadding + '|');
				asb.append(AttributedString.NEWLINE);
			}

			if (travelled.contains(refer)) {
				asb.style(AttributedStyle.DEFAULT.bold().italic().foreground(AttributedStyle.BLUE));
				asb.append(leftPadding + "- ");
			} else {
				asb.append(leftPadding + "+ ");
			}

			asb.append(refer.toAttributedString());
			asb.toAttributedString().println(parent.getTerminal());

			if (!travelled.contains(refer)) {
				travelled.add(refer);
				printReferrals(refer, leftPadding + "  ", travelled, level);
			}
			isFirst = false;

			if (max > 0 && travelled.size() > max) {
				break;
			}
		}
	}

	private Set<Element> getElementsReferencingThisOne(Element element) {
		var referenced = new HashSet<Element>();
		final var includeSymbols = Arrays.stream(parameters.types)
				.anyMatch(t -> t.equalsIgnoreCase("Symbol"));

		if (reverse) {
			List<Element> tmp = new ArrayList<>();

			if (element instanceof ClassObject c) {
				if (includeSymbols) {
					tmp.addAll(c.getSymbols());
				} else {
					c.getSymbols().forEach(s -> tmp.addAll(getElementsReferencingThisOne(s)));
				}
			} else {
				List<ReferencingElement> elements = parent.getInformation().getAll().parallelStream()
						.filter(e -> (e instanceof ReferencingElement))
						.map(ReferencingElement.class::cast)
						.filter(e -> (e.getReferences().contains(element)))
						.toList();
				for (Element e : elements) {
					if (e.getType().equalsIgnoreCase("Symbol")) {
						if (includeSymbols) {
							tmp.add(e);
						}
						// Add the class this symbol refers to
						// each symbol with a class should have only one class, so this is fine
						((ReferencingElement) e).getReferences().stream()
								.filter(c -> c.getType().equalsIgnoreCase("Class"))
								.forEach(tmp::add);

					} else {
						tmp.add(e);
					}
				}
			}

			referenced.addAll(filter(tmp.parallelStream()).toList());
		} else {
			if (element instanceof ClassObject classObject) {
				//Add object instances of this class
				if (Arrays.stream(parameters.types).anyMatch(t -> t.equalsIgnoreCase("Object"))) {
					referenced.addAll(filter(parent.getInformation().getAll().parallelStream()
							.filter(e -> e.getType().equalsIgnoreCase("Object"))
							.filter(e -> ((ReferencingElement) e).getReferences().contains(element))).toList());
				}

				if (includeSymbols) {
					referenced.addAll(
							filter(classObject.getSymbols()
									.parallelStream().map(Element.class::cast))
									.toList());
				} else {
					//Add whatever the symbols redirect us to
					Set<Element> builtBySymbols = new HashSet<>();
					classObject.getSymbols().forEach(s -> traverseSymbols(s, builtBySymbols, new HashSet<>()));
					referenced.addAll(filter(builtBySymbols.parallelStream()).toList());
				}

				//If there are no symbols (maybe no log was loaded), show at least the methods
				if (classObject.getSymbols().isEmpty()) {
					referenced.addAll(classObject.getMethods());
				}
			} else if (element instanceof ConstantPoolObject cp) {
				referenced.addAll(filter(Stream.of(cp.getPoolHolder())).toList());
			} else if (element instanceof ReferencingElement re) {
				referenced.addAll(filter(re.getReferences().parallelStream()).toList());
			}
		}

		//remove parent node, if it is there
		referenced.remove(element);

		return referenced;
	}

	private void traverseSymbols(ReferencingElement s, Set<Element> referenced, Set<Element> walkedBy) {
		for (Element e : s.getReferences()) {
			if (max > 0 && referenced.size() > max) {
				break;
			}
			if (!walkedBy.contains(e) && !referenced.contains(e)) {
				walkedBy.add(e);
				if (e.getType().equalsIgnoreCase("Symbol")) {
					traverseSymbols((ReferencingElement) e, referenced, walkedBy);
				} else {
					referenced.add(e);
				}
			}
		}
	}

	//Delegate on Information for filtering
	private Stream<Element> filter(Stream<Element> elements) {
		return Information.filterByParams(parameters.packageName, parameters.excludePackageName, parameters.showArrays,
				parameters.types, elements);
	}

}