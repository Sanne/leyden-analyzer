package tooling.leyden.aotcache;


import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Elements that refer to other types of elements. For example: An element in the ConstantPool may be of certain
 * class, which is defined and loaded on the Information independently.
 **/
public class ReferencingElement extends Element {
	private List<Element> references = new LinkedList<>();
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ReferencingElement(){

	}

	public ReferencingElement(String name, String type) {
		this.setName(name);
		this.setType(type);
	}

	@Override
	public String getKey() {
		return name;
	}

	public List<Element> getReferences() {
		return this.references;
	}

	public void addReference(Element reference) {
		if (!this.references.contains(reference) && this != reference) {
			this.references.add(reference);
			this.references.sort(Comparator.comparing(Element::getType));
		}
	}

	@Override
	public AttributedString getDescription(String leftPadding) {

		AttributedStringBuilder sb = new AttributedStringBuilder();
		sb.append(super.getDescription(leftPadding));

		if (!this.getReferences().isEmpty()) {
			sb.append('\n' + leftPadding + "This element refers to " + getReferences().size() + " other elements.");
		}

		return sb.toAttributedString();
	}
}
