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

	public JvmTranslator() {
		this(Collections.singletonList("java.lang"));
	}

	public JvmTranslator(String... imported) {
		this(Arrays.asList(imported));
	}

	public JvmTranslator(Collection<String> imported) {
		this.imported = imported;
	}

	public String toDescriptor(String type) throws JvmTranslatorException {
		StringBuilder sb = new StringBuilder();
		append(sb, stripGenerics(type));
		return sb.toString();
	}

	public List<String> toFieldDescriptor(String decl) throws JvmTranslatorException {
		return toFieldDescriptor(Collections.singletonList(decl));
	}

	public List<String> toFieldDescriptor(List<String> tokens) throws JvmTranslatorException {
		return toFieldDescriptor(tokens, 0);
	}

	public List<String> toFieldDescriptor(List<String> tokens, int start)
					throws JvmTranslatorException {
		String decl = stripGenerics(String.join(" ", tokens.subList(start, tokens.size())));
		StringBuilder desc = new StringBuilder();
		String name = append(desc, decl);
		List<String> retval = new ArrayList<>();
		retval.add(name);
		retval.add(desc.toString());
		return retval;
	}

	public List<String> toMethodDescriptor(String decl) throws JvmTranslatorException {
		return toMethodDescriptor(Collections.singletonList(decl));
	}

	public List<String> toMethodDescriptor(List<String> tokens) throws JvmTranslatorException {
		return toMethodDescriptor(tokens, 0);
	}

	/**
	 * This is either two tokens -- method name and internal VM descriptor -- or the method definition
	 * in normal Java form (without attributes). If it's the latter, we want to convert it to the
	 * former.
	 */
	public List<String> toMethodDescriptor(List<String> tokens, int start)
					throws JvmTranslatorException {
		List<String> descList = tokens.subList(start, tokens.size());
		if (descList.size() == 2 && descList.get(1).charAt(0) == '(') {
			return descList;
		}
		List<String> returnList = new ArrayList<>(2);

		String sig = stripGenerics(String.join(" ", descList));
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
