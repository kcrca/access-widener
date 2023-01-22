package net.fabricmc.accesswidener;

/**
 * A problem with parsing the java-language definitions in {@link JvmTranslator}
 */
public class JvmTranslatorException extends Throwable {

	/**
	 * @inheritDoc
	 */
	public JvmTranslatorException(String msg) {
		super(msg);
	}

	/**
	 * Invoke String.format() with the parameters to get the message.
	 */
	public JvmTranslatorException(String fmt, Object... args) {
		this(String.format(fmt, args));
	}
}
