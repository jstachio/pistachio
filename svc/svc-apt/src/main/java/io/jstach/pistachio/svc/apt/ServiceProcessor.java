package io.jstach.pistachio.svc.apt;

import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.eclipse.jdt.annotation.Nullable;

public class ServiceProcessor extends AbstractProcessor {

	public static String SERVICE_PROVIDER_ANNOTATION = "io.jstach.pistachio.svc.ServiceProvider";

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Set.of(SERVICE_PROVIDER_ANNOTATION);
	}

	private static @Nullable AnnotationMirror getMirror(String fqn, Element target) {
		for (AnnotationMirror m : target.getAnnotationMirrors()) {
			CharSequence mfqn = ((TypeElement) m.getAnnotationType().asElement()).getQualifiedName();
			if (mfqn != null && fqn.contentEquals(mfqn))
				return m;
		}
		return null;
	}

	private final Map<String, Set<String>> services = new ConcurrentHashMap<String, Set<String>>();

	@Override
	public synchronized boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (!roundEnv.processingOver()) {
			return read(roundEnv);
		}
		write();
		return false;
	}

	private boolean read(RoundEnvironment roundEnv) {
		TypeElement servisaur = processingEnv.getElementUtils().getTypeElement(SERVICE_PROVIDER_ANNOTATION);

		if (servisaur == null) {
			processingEnv.getMessager().printMessage(Kind.WARNING,
					SERVICE_PROVIDER_ANNOTATION + " class is missing. Skip processing.");
			return false;
		}

		Elements elements = processingEnv.getElementUtils();

		// discover services from the current compilation sources
		for (Element e : roundEnv.getElementsAnnotatedWith(servisaur)) {
			var am = getMirror(SERVICE_PROVIDER_ANNOTATION, e);
			if (am == null)
				continue;
			if (!e.getKind().isClass() && !e.getKind().isInterface()) {
				processingEnv.getMessager().printMessage(Kind.WARNING,
						"Not correct type for annotation @" + SERVICE_PROVIDER_ANNOTATION, e);
				continue;
			}
			TypeElement type = (TypeElement) e;
			List<TypeElement> contracts = getServiceInterfaces(type, am);

			for (TypeElement contract : contracts) {
				String cn = elements.getBinaryName(contract).toString();
				Set<String> v = services.computeIfAbsent(cn, (_k) -> new TreeSet<String>());
				requireNonNull(v);
				v.add(elements.getBinaryName(type).toString());

			}

		}
		return false;
	}

	private void write() {
		// Read the existing service files
		Filer filer = processingEnv.getFiler();
		for (Map.Entry<String, Set<String>> e : services.entrySet()) {
			String contract = e.getKey();
			try {
				FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract);
				BufferedReader r = new BufferedReader(
						new InputStreamReader(f.openInputStream(), StandardCharsets.UTF_8));
				String line;
				while ((line = r.readLine()) != null)
					e.getValue().add(line);
				r.close();
			}
			catch (FileNotFoundException x) {
				// missing and thus not created yet
			}
			catch (java.nio.file.NoSuchFileException x) {
				// missing and thus not created yet
			}
			catch (IOException x) {
				processingEnv.getMessager().printMessage(Kind.ERROR,
						"Failed to load existing service definition file. SPI: " + contract + " exception: " + x);
			}
		}

		// Write the service files
		for (Map.Entry<String, Set<String>> e : services.entrySet()) {
			try {
				String contract = e.getKey();
				processingEnv.getMessager().printMessage(Kind.NOTE, "Writing META-INF/services/" + contract);
				FileObject f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + contract);
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(f.openOutputStream(), "UTF-8"));
				for (String value : e.getValue())
					pw.println(value);
				pw.close();
			}
			catch (IOException x) {
				processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to write service definition files: " + x);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T> getValues(String key, AnnotationMirror a) {
		for (var e : a.getElementValues().entrySet()) {
			if (key.equals(e.getKey().getSimpleName().toString())) {
				List<T> result = new ArrayList<>();
				var values = (List<AnnotationValue>) e.getValue().getValue();
				for (var v : values) {
					result.add((T) v.getValue());
				}
				return result;
			}
		}
		return List.of();
	}

	private List<TypeElement> getServiceInterfaces(TypeElement type, AnnotationMirror a) {
		List<TypeElement> typeElementList = new ArrayList<TypeElement>();

		List<TypeMirror> types = getValues("value", a);

		for (TypeMirror m : types) {
			if (m.getKind() == TypeKind.VOID) {
				// This inferrring of the service was inpsired by Kohsuke
				// Metainf
				boolean hasBaseClass = type.getSuperclass().getKind() != TypeKind.NONE
						&& !isObject(type.getSuperclass());
				boolean hasInterfaces = !type.getInterfaces().isEmpty();
				if (hasBaseClass ^ hasInterfaces) {
					if (hasBaseClass) {
						typeElementList.add((TypeElement) ((DeclaredType) type.getSuperclass()).asElement());
					}
					else {
						typeElementList.add((TypeElement) ((DeclaredType) type.getInterfaces().get(0)).asElement());
					}
					continue;
				}

				error(type, "SPI type was not specified, and could not be inferred.");
				continue;
			}

			if (m instanceof DeclaredType dt) {
				typeElementList.add((TypeElement) dt.asElement());
				continue;
			}
			else {
				error(type, "Invalid type specified as the SPI");
				continue;
			}
		}
		return typeElementList;
	}

	private boolean isObject(@Nullable TypeMirror t) {
		if (t instanceof DeclaredType dt) {
			return ((TypeElement) dt.asElement()).getQualifiedName().toString().equals("java.lang.Object");
		}
		return false;
	}

	private void error(Element source, String msg) {
		processingEnv.getMessager().printMessage(Kind.ERROR, msg, source);
	}

}