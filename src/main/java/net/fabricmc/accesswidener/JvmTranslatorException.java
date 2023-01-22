package net.fabricmc.accesswidener;

public class JvmTranslatorException extends Throwable {

	public JvmTranslatorException(String msg) {
		super(msg);
	}

	public JvmTranslatorException(String fmt, Object... args) {
		this(String.format(fmt, args));
	}
}
