package tooling.leyden.commands.autocomplete;

import tooling.leyden.aotcache.Information;

import java.util.Iterator;


public class Addressess implements Iterable<String> {
	@Override
	public Iterator<String> iterator() {
		return Information.getMyself().getAddressess().iterator();
	}
}
