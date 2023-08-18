package io.jstach.prism.apt;

import java.io.PrintWriter;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class PrismWriter {

	final String name;

	String mirrorType;

	String prismType;

	final boolean arrayed;

	final ExecutableElement m;

	final String access; // "public" or ""

	PrismWriter(ExecutableElement m, boolean arrayed, String access) {
		this.m = m;
		this.arrayed = arrayed;
		this.access = access;
		this.name = m.getSimpleName().toString();
	}

	public void setPrismType(String prismType) {
		this.prismType = prismType;
	}

	public void setMirrorType(String mirrorType) {
		this.mirrorType = mirrorType;
	}

	void writeField(String indent, PrintWriter out) {
		out.format("%s    /** store prism value of %s */\n", indent, name);
		if (arrayed) {
			out.format("%s    private java.util.List<%s> _%s;\n\n", indent, prismType, name);
		}
		else {
			out.format("%s    private %s _%s;\n\n", indent, nullable(prismType), name);
		}
	}

	static String nullable(String className) {
		int index = className.lastIndexOf(".");
		if (index < 1) {
			return "@Nullable " + className;
		}
		return className.substring(0, index) + ".@Nullable " + className.substring(index + 1);

	}

	/* return source code that converts an expr of mirrorType to prismType. */
	String mirror2prism(String expr) {
		return String.format(m2pFormat, expr);
	}

	String m2pFormat = "undefinedConverter(%s)";

	public void setM2pFormat(String m2pFormat) {
		this.m2pFormat = m2pFormat;
	}

	void writeInitializer(String indent, PrintWriter out) {
		if (arrayed) {
			if (mirrorType.equals(prismType)) {
				out.format("%s        _%s = getArrayValues(\"%s\",%s.class);\n", indent, name, name, prismType);
			}
			else {
				out.format("%s        java.util.List<%s> %sMirrors = getArrayValues(\"%s\",%s.class);\n", indent,
						mirrorType, name, name, mirrorType);
				out.format("%s         _%s = new java.util.ArrayList<%s>(%sMirrors.size());\n", indent, name, prismType,
						name);
				out.format("%s        for(%s %sMirror : %sMirrors) {\n", indent, mirrorType, name, name);
				out.format("%s            _%s.add(%s);\n", indent, name, mirror2prism(name + "Mirror"));
				out.format("%s        }\n", indent);
			}
		}
		else if (mirrorType.equals(prismType)) {
			out.format("%s        _%s = getValue(\"%s\",%s.class);\n", indent, name, name, prismType);
		}
		else {
			out.format("%s        %s %sMirror = getValue(\"%s\",%s.class);\n", indent, mirrorType, name, name,
					mirrorType);
			out.format("%s        valid = valid && %sMirror != null;\n", indent, name);
			out.format("%s        _%s = %sMirror == null ? null : %s;\n", indent, name, name,
					mirror2prism(name + "Mirror"));
		}
	}

	void writeMethod(String indent, PrintWriter out) {
		if (arrayed) {
			out.format("%s    /**\n", indent);
			out.format(
					"%s     * Returns a List&lt;%s&gt; representing the value of the {@code %s} member of the Annotation.\n",
					indent, prismType, m);
			out.format("%s     * @return list of values.\n", indent);

			out.format("%s     * @see %s#%s()\n", indent, ((TypeElement) m.getEnclosingElement()).getQualifiedName(),
					name);
			out.format("%s     */ \n", indent);
			out.format("%s    %sjava.util.List<%s> %s() { return requireMember(_%s); }\n\n", indent, access, prismType,
					name, name);
		}
		else {
			out.format("%s    /**\n", indent);
			out.format("%s     * Returns a %s representing the value of the {@code %s %s} member of the Annotation.\n",
					indent, prismType, m.getReturnType(), m);
			out.format("%s     * @return prism value.\n", indent);
			out.format("%s     * @see %s#%s()\n", indent, ((TypeElement) m.getEnclosingElement()).getQualifiedName(),
					name);
			out.format("%s     */ \n", indent);
			out.format("%s    %s%s %s() { return requireMember(_%s); }\n\n", indent, access, prismType, name, name);
		}
	}

}
