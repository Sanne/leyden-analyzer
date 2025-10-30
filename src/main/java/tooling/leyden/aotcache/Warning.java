package tooling.leyden.aotcache;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents errors in storing or loading elements to/from the cache.
 */
public class Warning {

	private String id;

	private WarningType type;

	/**
	 * Element that suffered the problem.
	 */
	private Element element;

	/**
	 * String ready to be printed regarding this error.
	 */
	private AttributedString message;

	private static AtomicInteger idGenerator = new AtomicInteger();

	public Warning(Element e, AttributedString message, WarningType type) {
		this.element = e;
		this.type = type;
		this.message = message;
		this.setId(idGenerator.getAndIncrement());
	}

	public Warning(Element element, String description, WarningType type) {
		this(element, new AttributedString(description), type);
	}

	public Warning(String description) {
		this(null, new AttributedString(description), WarningType.Unknown);
	}

	public String getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = String.format("%03d", id);
	}

	public WarningType getType() {
		return type;
	}

	public Element getElement() {
		return element;
	}

	public AttributedString getDescription() {

		AttributedStringBuilder sb = new AttributedStringBuilder();
		sb.style(AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.CYAN));
		sb.append(this.getId());
		sb.style(AttributedStyle.DEFAULT);
		sb.append(" [");
		sb.style(AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.YELLOW));
		sb.append(this.type.name());
		sb.style(AttributedStyle.DEFAULT);
		sb.append("] ");
		sb.append(this.message);
		return sb.toAttributedString();
	}

	public String toString() {
		return message.toString();
	}
}
