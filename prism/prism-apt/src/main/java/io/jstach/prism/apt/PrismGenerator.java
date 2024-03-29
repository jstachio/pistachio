/*
Copyright (c) 2006,2007, Bruce Chapman

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
 are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation and/or
      other materials provided with the distribution.
    * Neither the name of the Hickory project nor the names of its contributors
      may be used to endorse or promote products derived from this software without
      specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/*
 * PrismGenerator.java
 *
 * Created on 27 June 2006, 22:07
 */

package io.jstach.prism.apt;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * An AnnotationProcessor for generating prisms. Do not use this class directly.
 *
 * @author Bruce
 * @author agentgt
 */
// @GeneratePrisms({
// @GeneratePrism(GeneratePrisms.class),
// @GeneratePrism(GeneratePrism.class)
// })
@SupportedAnnotationTypes({ "io.jstach.prism.GeneratePrism", "io.jstach.prism.GeneratePrisms" })
public final class PrismGenerator extends AbstractProcessor {

	/**
	 * Required for service loader
	 */
	public PrismGenerator() {
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}

	private final Map<String, TypeMirror> generated = new HashMap<>();

	@Override
	public boolean process(Set<? extends TypeElement> tes, RoundEnvironment renv) {
		if (renv.processingOver()) {
			return true;
		}

		final TypeElement a = processingEnv.getElementUtils().getTypeElement("io.jstach.prism.GeneratePrism");
		final TypeElement as = processingEnv.getElementUtils().getTypeElement("io.jstach.prism.GeneratePrisms");

		for (final Element e : renv.getElementsAnnotatedWith(a)) {
			final GeneratePrismPrism ann = GeneratePrismPrism.getInstanceOn(e);
			if (ann.isValid) {
				generateIfNew(ann, e, Collections.<DeclaredType, String>emptyMap());
			}
		}
		for (final Element e : renv.getElementsAnnotatedWith(as)) {
			final GeneratePrismsPrism ann = GeneratePrismsPrism.getInstanceOn(e);
			if (ann.isValid) {
				final Map<DeclaredType, String> otherPrisms = new HashMap<>();
				for (final GeneratePrismPrism inner : ann.value()) {
					getPrismName(inner);
					otherPrisms.put((DeclaredType) inner.value(), getPrismName(inner));
				}
				for (final GeneratePrismPrism inner : ann.value()) {
					generateIfNew(inner, e, otherPrisms);
				}
			}
		}
		return false;
	}

	private String getPrismName(GeneratePrismPrism ann) {
		String name = ann.name();
		if ("".equals(name)) {
			name = ((DeclaredType) ann.value()).asElement().getSimpleName() + "Prism";
		}
		return name;
	}

	private void generateIfNew(GeneratePrismPrism ann, Element e, Map<DeclaredType, String> otherPrisms) {
		final String name = getPrismName(ann);
		String packageName = getPackageName(e);
		// workaround for bug that has been fixed in a later build
		if ("unnamed package".equals(packageName)) {
			packageName = "";
		}
		final String prismFqn = "".equals(packageName) ? name : packageName + "." + name;
		if (generated.containsKey(prismFqn)) {
			// if same value dont need to generate, if different then error
			if (generated.get(prismFqn).equals(ann.value())) {
				return;
			}
			processingEnv.getMessager()
				.printMessage(Diagnostic.Kind.ERROR,
						String.format("%s has already been generated for %s", prismFqn, generated.get(prismFqn)), e,
						ann.mirror);
			return;
		}
		generatePrism(name, packageName, (DeclaredType) ann.value(), ann.publicAccess() ? "public " : "", otherPrisms);
		generated.put(prismFqn, ann.value());
	}

	private String getPackageName(Element e) {
		while (e.getKind() != ElementKind.PACKAGE) {
			e = e.getEnclosingElement();
		}
		return ((PackageElement) e).getQualifiedName().toString();
	}

	private enum StaticMethods {

		ARRAYS, VALUE

	}

	private void generatePrism(String name, String packageName, DeclaredType typeMirror, String access,
			Map<DeclaredType, String> otherPrisms) {
		inners.clear();
		final String prismFqn = "".equals(packageName) ? name : packageName + "." + name;
		PrintWriter out = null;
		try {
			// out = new PrintWriter(processingEnv.getFiler().createSourceFile(prismFqn));
			out = new PrintWriter(processingEnv.getFiler().createSourceFile(prismFqn).openWriter());
		}
		catch (final IOException ex) {
			ex.printStackTrace();
		}
		try {

			if (!"".equals(packageName)) {
				out.format("package %s;\n\n", packageName);
			}
			out.format("import java.util.Map;\n");
			out.format("import javax.lang.model.element.AnnotationMirror;\n");
			out.format("import javax.lang.model.element.Element;\n");
			out.format("import javax.lang.model.element.AnnotationValue;\n");

			out.format("import javax.lang.model.element.ExecutableElement;\n");
			out.format("import javax.lang.model.element.TypeElement;\n");
			out.format("import javax.lang.model.util.ElementFilter;\n\n");
			out.format("import org.eclipse.jdt.annotation.Nullable;\n\n");

			final String annName = ((TypeElement) typeMirror.asElement()).getQualifiedName().toString();
			out.format("/**\n");
			out.format(" * A Prism representing an {@code @%s} annotation. \n", annName);
			out.format(" */ \n");
			out.format("%sclass %s {\n", access, name);

			// SHOULD make public only if the anotation says so, package by default.
			var staticMethods = generateClassBody("", out, name, name, typeMirror, access, otherPrisms);

			// recurse for inner prisms!!
			for (final DeclaredType next : inners) {
				final String innerName = next.asElement().getSimpleName().toString();
				((TypeElement) typeMirror.asElement()).getQualifiedName().toString();

				// javadoc
				out.format("    /**\n");
				out.format("     * %s inner prism. \n", innerName);
				out.format("     */\n");
				// code
				out.format("    %sstatic class %s {\n", access, innerName);
				staticMethods.addAll(generateClassBody("    ", out, name, innerName, next, access, otherPrisms));
				out.format("    }\n");

			}
			generateStaticMembers(out, staticMethods);
			out.format("}\n");
		}
		finally {
			out.close();
		}
		processingEnv.getMessager()
			.printMessage(Diagnostic.Kind.NOTE, String.format("Generated prism %s for @%s", prismFqn, typeMirror));
	}

	List<DeclaredType> inners = new ArrayList<>();

	private EnumSet<StaticMethods> generateClassBody(final String indent, final PrintWriter out, final String outerName,
			final String name, final DeclaredType typeMirror, String access, Map<DeclaredType, String> otherPrisms) {
		final List<PrismWriter> writers = new ArrayList<>();
		for (final ExecutableElement m : ElementFilter.methodsIn(typeMirror.asElement().getEnclosedElements())) {
			writers.add(getWriter(m, access, otherPrisms));
		}
		for (final PrismWriter w : writers) {
			w.writeField(indent, out);
		}

		final String annName = ((TypeElement) typeMirror.asElement()).getQualifiedName().toString();

		out.format("%s    /**\n", indent);
		out.format("%s     * Qualified class name of annotation.\n", indent);
		out.format("%s     */\n", indent);
		out.format("%s    public static final String PRISM_ANNOTATION_TYPE = \"%s\";\n\n", indent,
				((TypeElement) (typeMirror.asElement())).getQualifiedName());
		if (!writers.isEmpty()) {
			out.format("%s    /**\n", indent);
			out.format("%s     * An instance of the Values inner class whose\n", indent);
			out.format("%s     * methods return the AnnotationValues used to build this prism. \n", indent);
			out.format("%s     * Primarily intended to support using Messager.\n", indent);
			out.format("%s     */\n", indent);
			out.format("%s    %sfinal Values values;\n", indent, access);
		}
		final boolean inner = !"".equals(indent);
		// write factory methods
		if (!inner) {
			// javadoc
			out.format("%s    /**\n", indent);
			out.format("%s     * Return a prism representing the {@code @%s} annotation on 'e'. \n", indent, annName);
			out.format("%s     * similar to {@code e.getAnnotation(%s.class)} except that \n", indent, annName);
			out.format("%s     * an instance of this class rather than an instance of {@code %s}\n", indent, annName);
			out.format("%s     * is returned.\n", indent);
			out.format("%s     * @param e element. \n", indent);
			out.format("%s     * @return prism for element. \n", indent);
			out.format("%s     */\n", indent);
			// code
			out.format("%s    %sstatic @Nullable %s getInstanceOn(Element e) {\n", indent, access, name);
			out.format("%s        AnnotationMirror m = getMirror(PRISM_ANNOTATION_TYPE, e);\n", indent);
			out.format("%s        if(m == null) return null;\n", indent);
			out.format("%s        return getInstance(m);\n", indent);
			out.format("%s   }\n\n", indent);
		}
		out.format("%s    /**\n", indent);
		out.format("%s     * Return a prism of the {@code @%s} annotation whose mirror is mirror. \n", indent, annName);
		out.format("%s     * @param mirror mirror. \n", indent);
		out.format("%s     * @return prism for mirror \n", indent);
		out.format("%s     */\n\n", indent);
		out.format("%s    %sstatic %s getInstance(AnnotationMirror mirror) {\n", indent, inner ? "private " : access,
				name);
		out.format("%s        String mirrorType = mirror.getAnnotationType().toString();\n", indent);
		out.format("%s        if(!PRISM_ANNOTATION_TYPE.equals(mirrorType)) {\n", indent, name);
		out.format(
				"%s             throw new java.lang.IllegalArgumentException(\"expected: \" + PRISM_ANNOTATION_TYPE + \" got: \" + mirrorType);\n",
				indent);
		out.format("%s        }\n", indent);
		out.format("%s        return new %s(mirror);\n", indent, name);
		out.format("%s    }\n\n", indent);
		// write constructor
		out.format("%s    private %s(AnnotationMirror mirror) {\n", indent, name);
		out.print(
				"""
						        for(var e : mirror.getElementValues().entrySet()) {
						            memberValues.put(e.getKey().getSimpleName().toString(), e.getValue());
						        }
						        for(ExecutableElement member : ElementFilter.methodsIn(mirror.getAnnotationType().asElement().getEnclosedElements())) {
						            var defaultValue = member.getDefaultValue();
						            if (defaultValue == null) continue;
						            defaults.put(member.getSimpleName().toString(), defaultValue);
						        }
						""");
		for (final PrismWriter w : writers) {
			w.writeInitializer(indent, out);
		}
		if (!writers.isEmpty()) {
			out.format("%s        this.values = new Values(memberValues);\n", indent);
		}
		out.format("%s        this.mirror = mirror;\n", indent);
		out.format("%s        this.isValid = valid;\n", indent);
		out.format("%s    }\n\n", indent);

		// write methods
		for (final PrismWriter w : writers) {
			w.writeMethod(indent, out);
		}

		// write isValid and getMirror methods
		// javadoc
		out.format("%s    /**\n", indent);
		out.format("%s     * Determine whether the underlying AnnotationMirror has no errors.\n", indent);
		out.format("%s     * True if the underlying AnnotationMirror has no errors.\n", indent);
		out.format("%s     * When true is returned, none of the methods will return null.\n", indent);
		out.format("%s     * When false is returned, a least one member will either return null, or another\n", indent);
		out.format("%s     * prism that is not valid.\n", indent);
		out.format("%s     */\n", indent);
		// code
		out.format("%s    %sfinal boolean isValid;\n", indent, access);
		out.format("%s    \n", indent);

		// javadoc
		out.format("%s    /**\n", indent);
		out.format("%s     * The underlying AnnotationMirror of the annotation\n", indent);
		out.format("%s     * represented by this Prism. \n", indent);
		out.format("%s     * Primarily intended to support using Messager.\n", indent);
		out.format("%s     */\n", indent);
		// code
		out.format("%s    %sfinal AnnotationMirror mirror;\n", indent, access);

		if (!writers.isEmpty()) {
			// write Value class
			// javadoc
			out.format("%s    /**\n", indent);
			out.format("%s     * A class whose members corespond to those of %s\n", indent, annName);
			out.format("%s     * but which each return the AnnotationValue corresponding to\n", indent);
			out.format("%s     * that member in the model of the annotations. Returns null for\n", indent);
			out.format("%s     * defaulted members. Used for Messager, so default values are not useful.\n", indent);
			out.format("%s     */\n", indent);
			// code
			out.format("%s    %sstatic class Values {\n", indent, access);
			out.format("%s       private Map<String, AnnotationValue> values;\n", indent);
			out.format("%s       private Values(Map<String, AnnotationValue> values) {\n", indent);
			out.format("%s           this.values = values;\n", indent);
			out.format("%s       }    \n", indent);

			for (final PrismWriter w : writers) {
				// javadoc
				out.format("%s       /**\n", indent);
				out.format("%s        * Return the AnnotationValue corresponding to the %s() \n", indent, w.name);
				out.format("%s        * member of the annotation, or null when the default value is implied.\n",
						indent);
				out.format("%s        * @return annotation value.\n", indent);
				out.format("%s        */\n", indent);
				// code
				out.format("%s       %s@Nullable AnnotationValue %s(){ return values.get(\"%s\");}\n", indent, access,
						w.name, w.name);
			}
			out.format("%s    }\n", indent);
		}
		EnumSet<StaticMethods> methods = EnumSet.noneOf(StaticMethods.class);
		for (var pw : writers) {
			if (pw.arrayed) {
				methods.add(StaticMethods.ARRAYS);
			}
			else {
				methods.add(StaticMethods.VALUE);
			}
		}
		generateFixedClassContent(indent, out, outerName, methods);

		return methods;
	}

	private PrismWriter getWriter(ExecutableElement m, String access, Map<DeclaredType, String> otherPrisms) {
		final Elements elements = processingEnv.getElementUtils();
		final Types types = processingEnv.getTypeUtils();
		final WildcardType q = types.getWildcardType(null, null);
		final TypeMirror enumType = types.getDeclaredType(elements.getTypeElement("java.lang.Enum"), q);
		TypeMirror typem = m.getReturnType();
		PrismWriter result = null;
		if (typem.getKind() == TypeKind.ARRAY) {
			typem = ((ArrayType) typem).getComponentType();
			result = new PrismWriter(m, true, access);
		}
		else {
			result = new PrismWriter(m, false, access);
		}
		if (typem.getKind().isPrimitive()) {
			final String typeName = types.boxedClass((PrimitiveType) typem).getSimpleName().toString();
			result.setMirrorType(typeName);
			result.setPrismType(typeName);
		}
		else if (typem.getKind() == TypeKind.DECLARED) {
			final DeclaredType type = (DeclaredType) typem;
			// String, enum, annotation, or Class<?>
			if (types.isSameType(type, elements.getTypeElement("java.lang.String").asType())) {
				// String
				result.setMirrorType("String");
				result.setPrismType("String");
			}
			else if (type.asElement().equals(elements.getTypeElement("java.lang.Class"))) {
				// class<? ...>
				result.setMirrorType(TypeMirror.class.getName());
				result.setPrismType(TypeMirror.class.getName());
			}
			else if (types.isSubtype(type, enumType)) {
				// Enum
				result.setMirrorType(VariableElement.class.getName());
				result.setPrismType("String");
				result.setM2pFormat("%s.getSimpleName().toString()");
			}
			else if (types.isSubtype(type, elements.getTypeElement("java.lang.annotation.Annotation").asType())) {
				result.setMirrorType("AnnotationMirror");
				final DeclaredType annType = type;
				String prismName = null;
				for (final DeclaredType other : otherPrisms.keySet()) {
					if (types.isSameType(other, annType)) {
						prismName = otherPrisms.get(other);
						break;
					}
				}
				if (prismName != null) {
					result.setPrismType(prismName);
					result.setM2pFormat(prismName + ".getInstance(%s)");
				}
				else {
					// generate its prism as inner class
					final String prismType = annType.asElement().getSimpleName().toString();
					result.setPrismType(prismType);
					result.setM2pFormat(prismType + ".getInstance(%s)");
					// force generation of inner prism class for annotation
					if (!inners.contains(type)) {
						inners.add(type);
					}
				}
			}
			else {
				System.out.format("Unprocessed type %s", type);
			}
		}
		return result;
	}

	private void generateStaticMembers(PrintWriter out, Set<StaticMethods> staticMethods) {
		boolean arrays = staticMethods.contains(StaticMethods.ARRAYS);
		boolean value = staticMethods.contains(StaticMethods.VALUE);
		String mirrorCode = """
				private static @Nullable AnnotationMirror getMirror(String fqn, Element target) {
				    for (AnnotationMirror m :target.getAnnotationMirrors()) {
				        CharSequence mfqn = ((TypeElement)m.getAnnotationType().asElement()).getQualifiedName();
				        if(fqn.contentEquals(mfqn)) return m;
				    }
				    return null;
				}
				""";
		out.print(indent(mirrorCode));
		String valueCode = """
				private static <T> @Nullable T getValue(
				    Map<String, AnnotationValue> memberValues,
				    Map<String, AnnotationValue> defaults,
				    String name, Class<T> clazz) {

				    AnnotationValue av = memberValues.get(name);
				    if(av == null) av = defaults.get(name);
				    if(av == null) {
				        return null;
				    }
				    if(clazz.isInstance(av.getValue())) return clazz.cast(av.getValue());
				    return null;
				}
				""";
		if (value) {
			out.print(indent(valueCode));
		}

		String requireMemberCode = """
				private static <T> T requireMember(@Nullable T t) {
				    if (t == null) {
				        throw new java.util.NoSuchElementException("prism is invalid");
				    }
				    return t;
				}
								""";
		if (value || arrays) {
			out.print(indent(requireMemberCode));
		}

		String arrayCode = """
				private static <T> java.util.List<T> getArrayValues(
				    Map<String, AnnotationValue> memberValues,
				    Map<String, AnnotationValue> defaults, String name,
				    final Class<T> clazz) {

				    AnnotationValue av = memberValues.get(name);
				    if(av == null) av = defaults.get(name);
				    if(av == null) {
				        return java.util.Collections.emptyList();
				    }
				    if(av.getValue() instanceof java.util.List) {
				        java.util.List<T> result = new java.util.ArrayList<T>();
				        for(AnnotationValue v : getValueAsList(av)) {
				            if(clazz.isInstance(v.getValue())) {
				                result.add(clazz.cast(v.getValue()));
				            } else{
				                return java.util.Collections.emptyList();
				            }
				        }
				        return result;
				    } else {
				        return java.util.Collections.emptyList();
				    }
				}
				@SuppressWarnings("unchecked")
				private static java.util.List<AnnotationValue> getValueAsList(AnnotationValue av) {
				    return (java.util.List<AnnotationValue>)av.getValue();
				}
				""";
		if (arrays) {
			out.print(indent(arrayCode));
		}
	}

	private static String indent(String code) {
		return code.lines().map(line -> "    " + line + "\n").collect(Collectors.joining());
	}

	private void generateFixedClassContent(String indent, PrintWriter out, String outerName,
			EnumSet<StaticMethods> methods) {
		out.format("%s    private Map<String, AnnotationValue> defaults = new java.util.HashMap<>();\n", indent);
		out.format("%s    private Map<String, AnnotationValue> memberValues = new java.util.HashMap<>();\n", indent);
		out.format("%s    private boolean valid = true;\n", indent);
		out.format("\n");

		if (methods.contains(StaticMethods.VALUE)) {
			out.format("%s    private <T> @Nullable T getValue(String name, Class<T> clazz) {\n", indent);
			out.format("%s        @Nullable T result = %s.getValue(memberValues, defaults, name, clazz);\n", indent,
					outerName);
			out.format("%s        if(result == null) valid = false;\n", indent);
			out.format("%s        return result;\n", indent);
			out.format("%s    } \n", indent);
			out.format("\n");
		}

		if (methods.contains(StaticMethods.ARRAYS)) {
			out.format("%s    private <T> java.util.List<T> getArrayValues(String name, final Class<T> clazz) {\n",
					indent);
			out.format("%s        java.util.List<T> result = %s.getArrayValues(memberValues, defaults, name, clazz);\n",
					indent, outerName);
			out.format("%s        if(result == java.util.Collections.emptyList()) valid = false;\n", indent);
			out.format("%s        return result;\n", indent);
			out.format("%s    }\n", indent);
		}
	}

}
