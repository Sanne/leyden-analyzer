package tooling.leyden.commands.logparser;

import tooling.leyden.aotcache.ClassObject;
import tooling.leyden.aotcache.Element;
import tooling.leyden.commands.LoadFileCommand;

public class ProductionLogParser extends LogParser {

	public ProductionLogParser(LoadFileCommand loadFile) {
		super(loadFile);
	}

	@Override
	protected void processLine(Line line) {
		if (containsTags(line.tags(), "class", "load")) {
			processClassLoad(line);
		}
	}

	@Override
	String getSource() {
		return "Production log";
	}

	private void processClassLoad(Line line) {
		if (line.message().contains(" source: ")) {
			String className = line.message().substring(0, line.message().indexOf("source: ")).trim();
			Element e;
			if (line.message().indexOf("source: shared objects file") > 0) {
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
				information.addAOTCacheElement(e, getSource());

			} else {
				// else this wasn't loaded from the aot.map
				if (className.contains("$$Lambda/")) {
					this.information.getStatistics().incrementValue("[LOG] Lambda Methods not loaded from AOT Cache");
				}
				this.information.getStatistics().incrementValue("[LOG] Classes not loaded from AOT Cache");

				e = new ClassObject(className);
				information.addExternalElement(e, getSource());
			}
			e.addWhereDoesItComeFrom(line.content().substring(line.content().indexOf("source: ")));
		}
	}

}
