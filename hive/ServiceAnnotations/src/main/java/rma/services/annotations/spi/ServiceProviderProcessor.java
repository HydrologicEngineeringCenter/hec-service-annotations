package rma.services.annotations.spi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import rma.services.annotations.ServiceProvider;
import rma.services.annotations.ServiceProviders;


/**
 * Infrastructure for generating {@code META-INF/services/*} and
 * {@code META-INF/namedservices/*} registrations from annotations.
 * @author Shannon Newbold (sjnewbold@rmanet.com)
 */
@SupportedAnnotationTypes(value={"rma.services.annotations.ServiceProvider","rma.services.annotations.ServiceProviders"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class ServiceProviderProcessor extends AbstractProcessor {

    private final Map<ProcessingEnvironment,Map<String,SortedSet<ServiceLoaderLine>>> outputFilesByProcessor =
            new WeakHashMap<ProcessingEnvironment,Map<String,SortedSet<ServiceLoaderLine>>>();
    private final Map<ProcessingEnvironment,Map<String,List<Element>>> originatingElementsByProcessor =
            new WeakHashMap<ProcessingEnvironment,Map<String,List<Element>>>();
    private final Map<TypeElement,Boolean> verifiedClasses = new WeakHashMap<TypeElement,Boolean>();

    public ServiceProviderProcessor() {}

	
    public @Override final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.errorRaised()) {
            return false;
        }
        if (roundEnv.processingOver()) {
            writeServices();
            outputFilesByProcessor.clear();
            originatingElementsByProcessor.clear();
            return true;
        } else {
            return handleProcess(annotations, roundEnv);
        }
    }

    

    /**
     * Register a service.
     * If the class does not have an appropriate signature, an error will be printed and the registration skipped.
     * @param clazz the service implementation type
     * @param annotation the (top-level) annotation registering the service, for diagnostic purposes
     * @param type the type to which the implementation must be assignable
     * @param path a path under which to register, or "" if inapplicable
     * @param position a position at which to register, or {@link Integer#MAX_VALUE} to skip
     * @param supersedes possibly empty list of implementation to supersede
     */
    protected final void register(TypeElement clazz, Class<? extends Annotation> annotation,
            TypeMirror type, String path, int position, String[] supersedes) {
	
        Boolean verify = verifiedClasses.get(clazz);
        if (verify == null) {
            verify = verifyServiceProviderSignature(clazz, annotation);
            verifiedClasses.put(clazz, verify);
        }
        if (!verify) {
            return;
        }
        String impl = processingEnv.getElementUtils().getBinaryName(clazz).toString();
        String xface = processingEnv.getElementUtils().getBinaryName((TypeElement) processingEnv.getTypeUtils().asElement(type)).toString();
        if (!processingEnv.getTypeUtils().isAssignable(clazz.asType(), type)) {
            AnnotationMirror ann = findAnnotationMirror(clazz, annotation);
            processingEnv.getMessager().printMessage(Kind.ERROR, impl + " is not assignable to " + xface,
                    clazz, ann, findAnnotationValue(ann, "service"));
            return;
        }
        processingEnv.getMessager().printMessage(Kind.NOTE,
                impl + " to be registered as a " + xface + (path.length() > 0 ? " under " + path : ""));
        String rsrc = (path.length() > 0 ? "META-INF/namedservices/" + path + "/" : "META-INF/services/") + xface;
        {
            Map<String,List<Element>> originatingElements = originatingElementsByProcessor.get(processingEnv);
            if (originatingElements == null) {
                originatingElements = new HashMap<String,List<Element>>();
                originatingElementsByProcessor.put(processingEnv, originatingElements);
            }
            List<Element> origEls = originatingElements.get(rsrc);
            if (origEls == null) {
                origEls = new ArrayList<Element>();
                originatingElements.put(rsrc, origEls);
            }
            origEls.add(clazz);
        }
        Map<String,SortedSet<ServiceLoaderLine>> outputFiles = outputFilesByProcessor.get(processingEnv);
        if (outputFiles == null) {
            outputFiles = new HashMap<String,SortedSet<ServiceLoaderLine>>();
            outputFilesByProcessor.put(processingEnv, outputFiles);
        }
        SortedSet<ServiceLoaderLine> lines = outputFiles.get(rsrc);
        if (lines == null) {
            lines = new TreeSet<ServiceLoaderLine>();
            try {
                try {
                    FileObject in = processingEnv.getFiler().getResource(StandardLocation.SOURCE_PATH, "", rsrc);
                    in.openInputStream().close();
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                            "Cannot generate " + rsrc + " because it already exists in sources: " + in.toUri());
                    return;
                } catch (NullPointerException ex) {
                    // trying to prevent java.lang.NullPointerException
                    // at com.sun.tools.javac.util.DefaultFileManager.getFileForOutput(DefaultFileManager.java:1078)
                    // at com.sun.tools.javac.util.DefaultFileManager.getFileForOutput(DefaultFileManager.java:1054)
                    // at com.sun.tools.javac.processing.JavacFiler.getResource(JavacFiler.java:434)
                    // at org.netbeans.modules.openide.util.AbstractServiceProviderProcessor.register(AbstractServiceProviderProcessor.java:163)
                    // at org.netbeans.modules.openide.util.ServiceProviderProcessor.register(ServiceProviderProcessor.java:99)
                } catch (FileNotFoundException x) {
                    // Good.
                }
                try {
                    FileObject in = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", rsrc);
                    InputStream is = in.openInputStream();
                    try {
                        ServiceLoaderLine.parse(new InputStreamReader(is, "UTF-8"), lines); // NOI18N
                    } finally {
                        is.close();
                    }
                } catch (FileNotFoundException x) {
                    // OK, created for the first time
                }
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Kind.ERROR, x.toString());
                return;
            }
            outputFiles.put(rsrc, lines);
        }
        lines.add(new ServiceLoaderLine(impl, position, supersedes));
    }

    /**
     * @param element a source element
     * @param annotation a type of annotation
     * @return the instance of that annotation on the element, or null if not found
     */
    private AnnotationMirror findAnnotationMirror(Element element, Class<? extends Annotation> annotation) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            if (processingEnv.getElementUtils().getBinaryName((TypeElement) ann.getAnnotationType().asElement()).
                    contentEquals(annotation.getName())) {
                return ann;
            }
        }
        return null;
    }

    /**
     * @param annotation an annotation instance (null permitted)
     * @param name the name of an attribute of that annotation
     * @return the corresponding value if found
     */
    private AnnotationValue findAnnotationValue(AnnotationMirror annotation, String name) {
        if (annotation != null) {
            for (Map.Entry<? extends ExecutableElement,? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private boolean verifyServiceProviderSignature(TypeElement clazz, Class<? extends Annotation> annotation) {
        AnnotationMirror ann = findAnnotationMirror(clazz, annotation);
        if (!clazz.getModifiers().contains(Modifier.PUBLIC)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, clazz + " must be public", clazz, ann);
            return false;
        }
        if (clazz.getModifiers().contains(Modifier.ABSTRACT)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, clazz + " must not be abstract", clazz, ann);
            return false;
        }
        {
            boolean hasDefaultCtor = false;
            for (ExecutableElement constructor : ElementFilter.constructorsIn(clazz.getEnclosedElements())) {
                if (constructor.getModifiers().contains(Modifier.PUBLIC) && constructor.getParameters().isEmpty()) {
                    hasDefaultCtor = true;
                    break;
                }
            }
            if (!hasDefaultCtor) {
                processingEnv.getMessager().printMessage(Kind.ERROR, clazz + " must have a public no-argument constructor", clazz, ann);
                return false;
            }
        }
        return true;
    }

    private void writeServices() {
        for (Map.Entry<ProcessingEnvironment,Map<String,SortedSet<ServiceLoaderLine>>> outputFiles : outputFilesByProcessor.entrySet()) {
            for (Map.Entry<String,SortedSet<ServiceLoaderLine>> entry : outputFiles.getValue().entrySet()) {
                try {
                    FileObject out = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", entry.getKey(),
                            originatingElementsByProcessor.get(outputFiles.getKey()).get(entry.getKey()).toArray(new Element[0]));
                    OutputStream os = out.openOutputStream();
                    try {
                        PrintWriter w = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
                        for (ServiceLoaderLine line : entry.getValue()) {
                            line.write(w);
                        }
                        w.flush();
                        w.close();
                    } finally {
                        os.close();
                    }
                } catch (IOException x) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to write to " + entry.getKey() + ": " + x.toString());
                }
            }
        }
    }

	
	/**
     * The regular body of {@link #process}.
     * Called during regular rounds if there are no outstanding errors.
     * In the last round, one of the processors will write out generated registrations.
     * @param annotations as in {@link #process}
     * @param roundEnv as in {@link #process}
     * @return as in {@link #process}
     */
	protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for (Element el : roundEnv.getElementsAnnotatedWith(ServiceProvider.class)) {
			TypeElement clazz = (TypeElement) el;
			ServiceProvider sp = clazz.getAnnotation(ServiceProvider.class);
			register(clazz, ServiceProvider.class, sp);
		}
		for (Element el : roundEnv.getElementsAnnotatedWith(ServiceProviders.class)) {
			TypeElement clazz = (TypeElement) el;
			ServiceProviders spp = clazz.getAnnotation(ServiceProviders.class);
			for (ServiceProvider sp : spp.value()) {
				register(clazz, ServiceProviders.class, sp);
			}
		}
		return true;
	}

    private void register(TypeElement clazz, Class<? extends Annotation> annotation, ServiceProvider svc) {
        try {
            svc.service();
            assert false;
            return;
        } catch (MirroredTypeException e) {
            register(clazz, annotation, e.getTypeMirror(), svc.path(), svc.position(), svc.supersedes());
        }
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element annotated, AnnotationMirror annotation, ExecutableElement attr, String userText) {
        if (processingEnv == null || annotated == null || !annotated.getKind().isClass()) {
            return Collections.emptyList();
        }

        if (   annotation == null
            || !"rma.services.annotations.ServiceProvider".contentEquals(((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName())) {
            return Collections.emptyList();
        }

        if (!"service".contentEquals(attr.getSimpleName())) {
            return Collections.emptyList();
        }

        TypeElement jlObject = processingEnv.getElementUtils().getTypeElement("java.lang.Object");

        if (jlObject == null) {
            return Collections.emptyList();
        }
        
        Collection<Completion> result = new LinkedList<Completion>();
        List<TypeElement> toProcess = new LinkedList<TypeElement>();

        toProcess.add((TypeElement) annotated);

        while (!toProcess.isEmpty()) {
            TypeElement c = toProcess.remove(0);

            result.add(new TypeCompletion(c.getQualifiedName().toString() + ".class"));

            List<TypeMirror> parents = new LinkedList<TypeMirror>();

            parents.add(c.getSuperclass());
            parents.addAll(c.getInterfaces());

            for (TypeMirror tm : parents) {
                if (tm == null || tm.getKind() != TypeKind.DECLARED) {
                    continue;
                }

                TypeElement type = (TypeElement) processingEnv.getTypeUtils().asElement(tm);

                if (!jlObject.equals(type)) {
                    toProcess.add(type);
                }
            }
        }

        return result;
    }

    private static final class TypeCompletion implements Completion {

        private final String type;

        public TypeCompletion(String type) {
            this.type = type;
        }

        public String getValue() {
            return type;
        }

        public String getMessage() {
            return null;
        }
    }
}
