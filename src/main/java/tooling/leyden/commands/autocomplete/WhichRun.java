package tooling.leyden.commands.autocomplete;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


public class WhichRun implements Iterable<String> {
	public enum Types {
		all,
		training,
		production,
		both,
		none;
	}

	private List<String> names = Arrays.stream(InfoCommandTypes.Types.values()).map(type -> type.name()).toList();

	@Override
	public Iterator<String> iterator() {
		return names.iterator();
	}
}
