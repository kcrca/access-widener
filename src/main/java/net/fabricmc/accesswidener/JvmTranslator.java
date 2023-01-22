package net.fabricmc.accesswidener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JvmTranslator {

	private static final Pattern STRIP_GENERICS = Pattern.compile("<[^<]*>");
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

	public static List<String> toMethodDescriptor(String string) throws JvmTranslatorException {
		return toMethodDescriptor(Collections.singletonList(string));
	}

	public static List<String> toMethodDescriptor(String string, Collection<String> imported)
					throws JvmTranslatorException {
		return toMethodDescriptor(Collections.singletonList(string), imported);
	}

	public static List<String> toMethodDescriptor(List<String> tokens) throws JvmTranslatorException {
		return toMethodDescriptor(tokens, 0);
	}

	public static List<String> toMethodDescriptor(List<String> tokens, Collection<String> imported)
					throws JvmTranslatorException {
		return toMethodDescriptor(tokens, 0, imported);
	}

	/**
	 * This is either two tokens -- method name and internal VM descriptor -- or the method definition
	 * in normal Java form (without attributes). If it's the latter, we want to convert it to the
	 * former.
	 */
	public static List<String> toMethodDescriptor(List<String> tokens, int start)
					throws JvmTranslatorException {
		List<String> importedPackages = Collections.singletonList("java.lang");
		return toMethodDescriptor(tokens, start, importedPackages);
	}

	public static List<String> toMethodDescriptor(
					List<String> tokens, int start, Collection<String> importedPackages
	) throws JvmTranslatorException {
		List<String> descList = tokens.subList(start, tokens.size());
		if (descList.size() == 2 && descList.get(1).charAt(0) == '(') {
			return descList;
		}
		List<String> returnList = new ArrayList<>(2);

		String s = String.join(" ", descList);
		String sig = STRIP_GENERICS.matcher(s).replaceAll("");
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
				append(desc, p, importedPackages);
			}
		}
		desc.append(')');
		append(desc, returnType, importedPackages);

		returnList.add(methodName);
		returnList.add(desc.toString());
		return returnList;
	}

	private static void append(
					StringBuilder desc, String decl, Collection<String> importedPackages
	) throws JvmTranslatorException {
		Matcher m = TYPE_DECL.matcher(decl);
		if (!m.matches()) {
			throw new JvmTranslatorException("Invalid identifier declaration: %s", decl);
		}
		long numArrays = decl.chars().filter(ch -> ch == '[').count();
		for (long i = 0; i < numArrays; i++) {
			desc.append('[');
		}
		String baseType = m.group(1);
		String primType = KNOWN_TYPES.get(baseType);
		if (primType != null) {
			desc.append(primType);
		} else {
			String name = null;
			if (findClass(desc, baseType)) {
				return;
			}
			for (String pkg : importedPackages) {
				if (!pkg.endsWith(".")) {
					pkg += ".";
				}
				if (findClass(desc, pkg + baseType)) {
					return;
				}
			}
			throw new JvmTranslatorException("Unknown type: " + baseType);
		}
	}

	private static boolean findClass(StringBuilder desc, String baseType) {
		try {
			String name = Class.forName(baseType).getName();
			desc.append('L').append(name).append(';');
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}


	private static String emptyIfNull(String group) {
		return group == null ? "" : group;
	}

}
