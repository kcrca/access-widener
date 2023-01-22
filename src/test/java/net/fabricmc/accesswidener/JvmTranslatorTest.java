package net.fabricmc.accesswidener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("ALL")
class JvmTranslatorTest {

	private static final Map<String, String> PRIM_TYPES;

	static {
		PRIM_TYPES = new HashMap<>();
		PRIM_TYPES.put("byte", "B");
		PRIM_TYPES.put("char", "C");
		PRIM_TYPES.put("double", "D");
		PRIM_TYPES.put("float", "F");
		PRIM_TYPES.put("int", "I");
		PRIM_TYPES.put("long", "J");
		PRIM_TYPES.put("short", "S");
		PRIM_TYPES.put("boolean", "Z");
		PRIM_TYPES.put("void", "V");
	}

	@SuppressWarnings("unused")
	static class Élevée {

	}

	@SuppressWarnings("unused")
	static class 健 {

	}

	@ParameterizedTest
	@ValueSource(strings = {"boolean", "char", "byte", "short", "int", "long", "float", "double"})
	void primitiveTypeDefined(String prim) throws JvmTranslatorException {
		String result = new JvmTranslator().toDescriptor(prim);
		assertThat(result).isEqualTo(PRIM_TYPES.get(prim));
	}

	@Test
	void importedTypeDefined() throws JvmTranslatorException {
		String result = new JvmTranslator().toDescriptor("String");
		assertThat(result).isEqualTo("Ljava/lang/String;");
	}

	@Test
	void unimportedTypeDefined() throws JvmTranslatorException {
		String result = new JvmTranslator().toDescriptor("java.util.Map");
		assertThat(result).isEqualTo("Ljava/util/Map;");
	}

	@Test
	void mismatchedGenericsThrows() {
		assertThatThrownBy(() -> new JvmTranslator().toDescriptor("Map<String")).isInstanceOf(
						JvmTranslatorException.class);
		assertThatThrownBy(() -> new JvmTranslator().toDescriptor("Map<String>>")).isInstanceOf(
						JvmTranslatorException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"<>", "<String>", "<Map<String, List<Set<Long>>>>"})
	void genericsIgnored(String generic) throws JvmTranslatorException {
		String result = new JvmTranslator().toDescriptor("Class" + generic);
		assertThat(result).isEqualTo("Ljava/lang/Class;");
	}

	@ParameterizedTest
	@ValueSource(strings = {"boolean", "char", "byte", "short", "int", "long", "float", "double"})
	void primitiveField(String type) throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toFieldDescriptor(type + " foo");
		assertThat(result).hasSameElementsAs(List.of("foo", PRIM_TYPES.get(type)));
	}

	@Test
	void objectField() throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toFieldDescriptor("Object foo");
		assertThat(result).hasSameElementsAs(List.of("foo", "Ljava/lang/Object;"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"<>", "<String>", "<Map<String, List<Set<Long>>>>"})
	void genericsIgnoredForField(String generic) throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toFieldDescriptor("Class" + generic + " foo");
		assertThat(result).hasSameElementsAs(List.of("foo", "Ljava/lang/Class;"));
	}


	@Test
	void trailingSemicolonIgnored() throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor("boolean foo();");
		assertThat(result).hasSameElementsAs(List.of("foo", "()Z"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"boolean", "char", "byte", "short", "int", "long", "float", "double",
					"void"})
	void methodReturnsType(String returnType) throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor(returnType + " foo()");
		assertThat(result).hasSameElementsAs(List.of("foo", "()" + PRIM_TYPES.get(returnType)));
	}


	@ParameterizedTest
	@ValueSource(strings = {"boolean", "char", "byte", "short", "int", "long", "float", "double",
					"void"})
	void returnsTypeWhenSplitUp(String returnType) throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor(returnType + " foo()");
		assertThat(result).hasSameElementsAs(List.of("foo", "()" + PRIM_TYPES.get(returnType)));
	}

	@Test
	void primitiveParameters() throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor(
						"void foo(boolean, char, byte, short, int, long, float, double)");
		assertThat(result).hasSameElementsAs(List.of("foo", "(ZCBSIJFD)V"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"char   [] [ ] blah", "char [  ] blah [ ]", "char blah[ ] [ ]"})
	void primitiveArrays(String params) throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor("int[] foo(" + params + ")");
		assertThat(result).hasSameElementsAs(List.of("foo", "([[C)[I"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"java.util.List   [] [ ] blah", "java.util.List [  ] blah [ ]",
					"java.util.List blah[ ] [ ]"})
	void objArrays(String params) throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor("int[] foo(" + params + ")");
		assertThat(result).hasSameElementsAs(List.of("foo", "([[Ljava/util/List;)[I"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"<>", "<String>", "<Map<String, List<Set<Long>>>"})
	void genericArrays(String generic) throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor("Class[] foo(Class[] bar[])");
		assertThat(result).hasSameElementsAs(List.of("foo", "([[Ljava/lang/Class;)[Ljava/lang/Class;"));
	}

	@Test
	void importedByDefault() throws JvmTranslatorException {
		List<String> result = new JvmTranslator().toMethodDescriptor("String foo(Object foo)");
		assertThat(result).hasSameElementsAs(List.of("foo", "(Ljava/lang/Object;)Ljava/lang/String;"));
	}

	@Test
	void importedExplicitly() throws JvmTranslatorException {
		List<String> result = new JvmTranslator("java.lang", "java.util").toMethodDescriptor(
						"Set foo(Object foo)");
		assertThat(result).hasSameElementsAs(List.of("foo", "(Ljava/lang/Object;)Ljava/util/Set;"));
	}

	@Test
	void notJustAscii() throws JvmTranslatorException {
		List<String> result = new JvmTranslator(
						List.of("net.fabricmc.accesswidener")).toMethodDescriptor(
						"JvmTranslatorTest$Élevée நண்பர்(JvmTranslatorTest$健)");
		assertThat(result).hasSameElementsAs(List.of("நண்பர்",
						"(Lnet/fabricmc/accesswidener/JvmTranslatorTest$健;)"
										+ "Lnet/fabricmc/accesswidener/JvmTranslatorTest$Élevée;"));
	}

	@Test
	void noMethodName() {
		assertThatThrownBy(() -> new JvmTranslator().toMethodDescriptor("void(int)")).isInstanceOf(
						JvmTranslatorException.class);
	}

	@Test
	void unknownType() {
		assertThatThrownBy(() -> new JvmTranslator().toMethodDescriptor("void foo(bar)")).isInstanceOf(
						JvmTranslatorException.class);
	}
}
