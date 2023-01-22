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

	// @ParameterizedTest
	// @ValueSource(strings = {"boolean", "char", "byte", "short", "int", "long", "float", "double"
	// 				})
	// void primitiveTypeDefined(String prim) {
	// 	assertThat()
	// }

	@Test
	void trailingSemicolonIgnored() throws JvmTranslatorException {
		assertThat(new JvmTranslator().toMethodDescriptor("boolean foo();")).hasSameElementsAs(
						List.of("foo", "()Z"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"boolean", "char", "byte", "short", "int", "long", "float", "double",
					"void"})
	void returnsType(String returnType) throws JvmTranslatorException {
		assertThat(new JvmTranslator().toMethodDescriptor(returnType + " foo()")).hasSameElementsAs(
						List.of("foo", "()" + PRIM_TYPES.get(returnType)));
	}


	@ParameterizedTest
	@ValueSource(strings = {"boolean", "char", "byte", "short", "int", "long", "float", "double",
					"void"})
	void returnsTypeWhenSplitUp(String returnType) throws JvmTranslatorException {
		assertThat(new JvmTranslator().toMethodDescriptor(
						List.of(returnType, "foo", "(", ")"))).hasSameElementsAs(
						List.of("foo", "()" + PRIM_TYPES.get(returnType)));
	}

	@Test
	void primitiveParameters() throws JvmTranslatorException {
		assertThat(new JvmTranslator().toMethodDescriptor(
						"void foo(boolean, char, byte, short, int, long, float, double)")).hasSameElementsAs(
						List.of("foo", "(ZCBSIJFD)V"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"char   [] [ ] blah", "char [  ] blah [ ]", "char blah[ ] [ ]"})
	void primitiveArrays(String params) throws JvmTranslatorException {
		assertThat(
						new JvmTranslator().toMethodDescriptor("int[] foo(" + params + ")")).hasSameElementsAs(
						List.of("foo", "([[C)[I"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"java.util.List   [] [ ] blah", "java.util.List [  ] blah [ ]",
					"java.util.List blah[ ] [ ]"})
	void objArrays(String params) throws JvmTranslatorException {
		assertThat(
						new JvmTranslator().toMethodDescriptor("int[] foo(" + params + ")")).hasSameElementsAs(
						List.of("foo", "([[Ljava.util.List;)[I"));
	}

	@Test
	void importedByDefault() throws JvmTranslatorException {
		assertThat(new JvmTranslator().toMethodDescriptor("String foo(Object foo)")).hasSameElementsAs(
						List.of("foo", "(Ljava.lang.Object;)Ljava.lang.String;"));
	}

	@Test
	void importedExplicitly() throws JvmTranslatorException {
		assertThat(new JvmTranslator("java.lang", "java.util").toMethodDescriptor(
						"Set foo(Object foo)")).hasSameElementsAs(
						List.of("foo", "(Ljava.lang.Object;)Ljava.util.Set;"));
	}

	@Test
	void notJustAscii() throws JvmTranslatorException {
		assertThat(new JvmTranslator(List.of("net.fabricmc.accesswidener")).toMethodDescriptor(
						"JvmTranslatorTest$Élevée நண்பர்(JvmTranslatorTest$健)")).hasSameElementsAs(
						List.of("நண்பர்", "(Lnet.fabricmc.accesswidener.JvmTranslatorTest$健;)"
										+ "Lnet.fabricmc.accesswidener.JvmTranslatorTest$Élevée;"));
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
