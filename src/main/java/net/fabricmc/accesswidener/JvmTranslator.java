package net.fabricmc.accesswidener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates normal Java-language types and type declarations into internal JVM descriptors. For
 * example, "int foo" becomes two elements: the field name "foo" and the type "I", and "String
 * bar(int[] i)" becomes two elements: the method name "bar" and the type "(Ljava.lang.String;)[I".
 *
 * The only exception to the "normal Java language" claim is that nested and inner classes must
 * still be distinguished with "$" instead of ".'. So a class Bar inside a class Foo is "Foo$Bar"
 * instead of "Foo.Bar".
 *
 * Generics are accepted but ignored, as long as the &lt;&gt;s are balanced.
 *
 * All the methods throw {@link JvmTranslatorException} if there is a parsing error, such as an
 * unknown class or imbalanced parentheses, and so on.
 */
public class JvmTranslator {

	private static final String IDENTIFIER_CORE = "\\w[\\w\\d.$]*";
	private static final String IDENTIFIER = "(" + IDENTIFIER_CORE + ")";
	private static final String ARRAY_SPEC = "[\\[\\]\\s]*";
	private static final String TYPE_SPEC = "(" + IDENTIFIER_CORE + ")(" + ARRAY_SPEC + ")";
	private static final Pattern METHOD_SPLIT = Pattern.compile(
					TYPE_SPEC + "\\s+" + IDENTIFIER + "\\s*\\(\\s*(.*)\\s*\\);?",
					Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern TYPE_DECL = Pattern.compile(
					TYPE_SPEC + "\\s*" + IDENTIFIER + "?" + "(" + ARRAY_SPEC + ")?",
					Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern PARAM_SPLIT = Pattern.compile("\\s*,\\s*");
	private static final Map<String, String> KNOWN_TYPES;

	static {
		KNOWN_TYPES = new HashMap<>();
		KNOWN_TYPES.put("byte", "B");
		KNOWN_TYPES.put("char", "C");
		KNOWN_TYPES.put("double", "D");
		KNOWN_TYPES.put("float", "F");
		KNOWN_TYPES.put("int", "I");
		KNOWN_TYPES.put("long", "J");
		KNOWN_TYPES.put("short", "S");
		KNOWN_TYPES.put("boolean", "Z");
		KNOWN_TYPES.put("void", "V");
	}

	private final Collection<String> imported;

	/**
	 * Creates a new translator with the default imports of {@code java.lang}.
	 */
	public JvmTranslator() {
		this(Collections.singletonList("java.lang"));
	}

	/**
	 * Creates a new translator with the specified imports.
	 */
	public JvmTranslator(String... imported) {
		this(Arrays.asList(imported));
	}

	/**
	 * Creates a new translator with the specified imports.
	 */
	public JvmTranslator(Collection<String> imported) {
		this.imported = imported;
	}

	/**
	 * Returns the JVM descriptor for the given type.
	 */
	public String toDescriptor(String type) throws JvmTranslatorException {
		StringBuilder sb = new StringBuilder();
		append(sb, stripGenerics(type));
		return sb.toString();
	}

	/**
	 * Returns the field descriptor for the given string.
	 *
	 * @return A list with the first element being the field name and the second the JVM descriptor.
	 */
	public List<String> toFieldDescriptor(String decl) throws JvmTranslatorException {
		decl = stripGenerics(decl);
		StringBuilder desc = new StringBuilder();
		String name = append(desc, decl);
		List<String> retval = new ArrayList<>();
		retval.add(name);
		retval.add(desc.toString());
		return retval;
	}

	/**
	 * Returns the method descriptor for the given string.
	 *
	 * @return A list with the first element being the method name and the second the JVM descriptor.
	 */
	public List<String> toMethodDescriptor(String decl) throws JvmTranslatorException {
		List<String> returnList = new ArrayList<>(2);

		String sig = stripGenerics(decl);
		Matcher m = METHOD_SPLIT.matcher(sig);
		if (!m.matches()) {
			throw new JvmTranslatorException("Invalid method descriptor: %s", sig);
		}
		String returnType = m.group(1) + emptyIfNull(m.group(2));
		String methodName = m.group(3);
		String parameterTypes = m.group(4);

		StringBuilder desc = new StringBuilder();
		desc.append('(');
		for (String p : PARAM_SPLIT.split(parameterTypes)) {
			if (!p.isEmpty()) {
				append(desc, p);
			}
		}
		desc.append(')');
		append(desc, returnType);

		returnList.add(methodName);
		returnList.add(desc.toString());
		return returnList;
	}

	private String stripGenerics(String s) throws JvmTranslatorException {
		if (s.indexOf('<') < 0) {
			return s;
		}
		StringBuilder stripped = new StringBuilder(s.length());
		int nesting = 0;
		for (char c : s.toCharArray()) {
			if (c == '<') {
				nesting++;
			} else if (c == '>') {
				nesting--;
			} else if (nesting == 0) {
				stripped.append(c);
			}
		}
		if (nesting != 0) {
			throw new JvmTranslatorException("Mismatched <>s: " + s);
		}
		return stripped.toString();
	}

	private String append(StringBuilder desc, String decl) throws JvmTranslatorException {
		Matcher m = TYPE_DECL.matcher(decl);
		if (!m.matches()) {
			throw new JvmTranslatorException("Invalid identifier declaration: %s", decl);
		}
		long numArrays = decl.chars().filter(ch -> ch == '[').count();
		for (long i = 0; i < numArrays; i++) {
			desc.append('[');
		}
		String baseType = m.group(1);
		String name = m.group(3);
		String primType = KNOWN_TYPES.get(baseType);
		if (primType != null) {
			desc.append(primType);
		} else {
			boolean found = false;
			if (!findClass(desc, baseType)) {
				for (String pkg : imported) {
					if (!pkg.endsWith(".")) {
						pkg += ".";
					}
					if (findClass(desc, pkg + baseType)) {
						found = true;
						break;
					}
				}
				if (!found) {
					throw new JvmTranslatorException("Unknown type: " + baseType);
				}
			}
		}
		return name;
	}

	private boolean findClass(StringBuilder desc, String baseType) {
		try {
			String name = Class.forName(baseType).getName();
			desc.append('L').append(name).append(';');
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private String emptyIfNull(String group) {
		return group == null ? "" : group;
	}
}
