/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 HEC, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package mil.army.usace.hec.services.annotations.spi;

import com.google.auto.service.AutoService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
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

import mil.army.usace.hec.services.annotations.ServiceProvider;
import mil.army.usace.hec.services.annotations.ServiceProviders;


/**
 * Infrastructure for generating {@code META-INF/services/*} and
 * {@code META-INF/namedservices/*} registrations from annotations.
 *
 * @author Shannon Newbold (sjnewbold@rmanet.com)
 */
@AutoService(javax.annotation.processing.Processor.class)
@SupportedAnnotationTypes(value = {"mil.army.usace.hec.services.annotations.ServiceProvider", "mil.army.usace.hec.services.annotations.ServiceProviders"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class ServiceProviderProcessor extends AbstractProcessor
{

	private final Map<ProcessingEnvironment, Map<String, SortedSet<ServiceLoaderLine>>> _outputFilesByProcessor = new WeakHashMap<>();
	private final Map<ProcessingEnvironment, Map<String, List<Element>>> _originatingElementsByProcessor = new WeakHashMap<>();
	private final Map<TypeElement, Boolean> _verifiedClasses = new WeakHashMap<>();


	@Override
	public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
	{
		if(roundEnv.errorRaised())
		{
			return false;
		}
		if(roundEnv.processingOver())
		{
			writeServices();
			_outputFilesByProcessor.clear();
			_originatingElementsByProcessor.clear();
			return true;
		}
		else
		{
			return handleProcess(annotations, roundEnv);
		}
	}


	/**
	 * Register a service.
	 * If the class does not have an appropriate signature, an error will be printed and the registration skipped.
	 *
	 * @param clazz      the service implementation type
	 * @param annotation the (top-level) annotation registering the service, for diagnostic purposes
	 * @param type       the type to which the implementation must be assignable
	 * @param path       a path under which to register, or "" if inapplicable
	 * @param position   a position at which to register, or {@link Integer#MAX_VALUE} to skip
	 * @param supersedes possibly empty list of implementation to supersede
	 */
	protected final void register(TypeElement clazz, Class<? extends Annotation> annotation, TypeMirror type,
								  String path, int position, String[] supersedes)
	{

		Boolean verify = _verifiedClasses.get(clazz);
		if(verify == null)
		{
			verify = verifyServiceProviderSignature(clazz, annotation);
			_verifiedClasses.put(clazz, verify);
		}

		if(!verify)
		{
			return;
		}
		String impl = processingEnv.getElementUtils().getBinaryName(clazz).toString();
		String xface = processingEnv.getElementUtils().getBinaryName(
				(TypeElement) processingEnv.getTypeUtils().asElement(type)).toString();
		if(!processingEnv.getTypeUtils().isAssignable(clazz.asType(), type))
		{
			AnnotationMirror ann = findAnnotationMirror(clazz, annotation);
			processingEnv.getMessager().printMessage(Kind.ERROR, impl + " is not assignable to " + xface, clazz, ann,
					findAnnotationValue(ann, "service"));
			return;
		}
		processingEnv.getMessager().printMessage(Kind.NOTE,
				impl + " to be registered as a " + xface + (path.length() > 0 ? " under " + path : ""));
		String rsrc = (path.length() > 0 ? "META-INF/namedservices/" + path + "/" : "META-INF/services/") + xface;
		{
			Map<String, List<Element>> originatingElements = _originatingElementsByProcessor.computeIfAbsent(
					processingEnv, k -> new HashMap<>());
			List<Element> origEls = originatingElements.computeIfAbsent(rsrc, k -> new ArrayList<>());
			origEls.add(clazz);
		}
		Map<String, SortedSet<ServiceLoaderLine>> outputFiles = _outputFilesByProcessor.computeIfAbsent(processingEnv,
				k -> new HashMap<>());
		SortedSet<ServiceLoaderLine> lines = outputFiles.get(rsrc);
		if(lines == null)
		{
			lines = new TreeSet<>();
			try
			{
				try
				{
					FileObject in = processingEnv.getFiler().getResource(StandardLocation.SOURCE_PATH, "", rsrc);
					in.openInputStream().close();
					processingEnv.getMessager().printMessage(Kind.ERROR,
							"Cannot generate " + rsrc + " because it already exists in sources: " + in.toUri());
					return;
				}
				catch(FileNotFoundException x)
				{
					// Good.
				}
				catch(RuntimeException ex)
				{
					//this is because eclipse is really stupid and throws exceptions when you try to get at source_path.
					//seems like we are seeing the same issue with gradle + java 8
					processingEnv.getMessager().printMessage(Kind.WARNING,
							"Skipping check to determine if META-INF service definition already exists for: " + rsrc);
				}
				try
				{
					FileObject in = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", rsrc);
					try(InputStream is = in.openInputStream())
					{
						ServiceLoaderLine.parse(new InputStreamReader(is, StandardCharsets.UTF_8), lines); // NOI18N
					}
				}
				catch(IOException x)
				{
					// OK, created for the first time
				}

			}
			catch(IOException x)
			{
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				x.printStackTrace(pw);
				pw.flush();
				sw.flush();
				processingEnv.getMessager().printMessage(Kind.ERROR, "Register: " + x.toString() + sw.toString());
				try
				{
					sw.close();
				}
				catch(IOException e)
				{
				}
				return;
			}
			outputFiles.put(rsrc, lines);
		}
		lines.add(new ServiceLoaderLine(impl, position, supersedes));
	}

	/**
	 * @param element    a source element
	 * @param annotation a type of annotation
	 * @return the instance of that annotation on the element, or null if not found
	 */
	private AnnotationMirror findAnnotationMirror(Element element, Class<? extends Annotation> annotation)
	{
		for(AnnotationMirror ann : element.getAnnotationMirrors())
		{
			if(processingEnv.getElementUtils().getBinaryName((TypeElement) ann.getAnnotationType().asElement()).
					contentEquals(annotation.getName()))
			{
				return ann;
			}
		}
		return null;
	}

	/**
	 * @param annotation an annotation instance (null permitted)
	 * @param name       the name of an attribute of that annotation
	 * @return the corresponding value if found
	 */
	private AnnotationValue findAnnotationValue(AnnotationMirror annotation, String name)
	{
		if(annotation != null)
		{
			for(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet())
			{
				if(entry.getKey().getSimpleName().contentEquals(name))
				{
					return entry.getValue();
				}
			}
		}
		return null;
	}

	private boolean verifyServiceProviderSignature(TypeElement clazz, Class<? extends Annotation> annotation)
	{
		AnnotationMirror ann = findAnnotationMirror(clazz, annotation);
		if(!clazz.getModifiers().contains(Modifier.PUBLIC))
		{
			processingEnv.getMessager().printMessage(Kind.ERROR, clazz + " must be public", clazz, ann);
			return false;
		}
		if(clazz.getModifiers().contains(Modifier.ABSTRACT))
		{
			processingEnv.getMessager().printMessage(Kind.ERROR, clazz + " must not be abstract", clazz, ann);
			return false;
		}
		{
			boolean hasDefaultCtor = false;
			for(ExecutableElement constructor : ElementFilter.constructorsIn(clazz.getEnclosedElements()))
			{
				if(constructor.getModifiers().contains(Modifier.PUBLIC) && constructor.getParameters().isEmpty())
				{
					hasDefaultCtor = true;
					break;
				}
			}
			if(!hasDefaultCtor)
			{
				processingEnv.getMessager().printMessage(Kind.ERROR,
						clazz + " must have a public no-argument constructor", clazz, ann);
				return false;
			}
		}
		return true;
	}

	private void writeServices()
	{
		for(Map.Entry<ProcessingEnvironment, Map<String, SortedSet<ServiceLoaderLine>>> outputFiles : _outputFilesByProcessor.entrySet())
		{
			for(Map.Entry<String, SortedSet<ServiceLoaderLine>> entry : outputFiles.getValue().entrySet())
			{
				try
				{
					FileObject out = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
							entry.getKey(),
							_originatingElementsByProcessor.get(outputFiles.getKey()).get(entry.getKey()).toArray(
									new Element[0]));
					try(OutputStream os = out.openOutputStream())
					{
						PrintWriter w = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
						for(ServiceLoaderLine line : entry.getValue())
						{
							line.write(w);
						}
						w.flush();
						w.close();
					}
				}
				catch(IOException x)
				{
					processingEnv.getMessager().printMessage(Kind.ERROR,
							"Failed to write to " + entry.getKey() + ": " + x.toString());
				}
			}
		}
	}


	/**
	 * The regular body of {@link #process}.
	 * Called during regular rounds if there are no outstanding errors.
	 * In the last round, one of the processors will write out generated registrations.
	 *
	 * @param annotations as in {@link #process}
	 * @param roundEnv    as in {@link #process}
	 * @return as in {@link #process}
	 */
	protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
	{
		for(Element el : roundEnv.getElementsAnnotatedWith(ServiceProvider.class))
		{
			TypeElement clazz = (TypeElement) el;
			ServiceProvider sp = clazz.getAnnotation(ServiceProvider.class);
			register(clazz, ServiceProvider.class, sp);
		}
		for(Element el : roundEnv.getElementsAnnotatedWith(ServiceProviders.class))
		{
			TypeElement clazz = (TypeElement) el;
			ServiceProviders spp = clazz.getAnnotation(ServiceProviders.class);
			for(ServiceProvider sp : spp.value())
			{
				register(clazz, ServiceProviders.class, sp);
			}
		}
		return true;
	}

	private void register(TypeElement clazz, Class<? extends Annotation> annotation, ServiceProvider svc)
	{
		try
		{
			svc.service();
			assert false;
		}
		catch(MirroredTypeException e)
		{
			register(clazz, annotation, e.getTypeMirror(), svc.path(), svc.position(), svc.supersedes());
		}
	}

	@Override
	public Iterable<? extends Completion> getCompletions(Element annotated, AnnotationMirror annotation,
														 ExecutableElement attr, String userText)
	{
		if(processingEnv == null || annotated == null || !annotated.getKind().isClass())
		{
			return Collections.emptyList();
		}

		if(annotation == null || !"rma.services.annotations.ServiceProvider".contentEquals(
				((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName()))
		{
			return Collections.emptyList();
		}

		if(!"service".contentEquals(attr.getSimpleName()))
		{
			return Collections.emptyList();
		}

		TypeElement jlObject = processingEnv.getElementUtils().getTypeElement("java.lang.Object");

		if(jlObject == null)
		{
			return Collections.emptyList();
		}

		Collection<Completion> result = new LinkedList<>();
		List<TypeElement> toProcess = new LinkedList<>();

		toProcess.add((TypeElement) annotated);

		while(!toProcess.isEmpty())
		{
			TypeElement c = toProcess.remove(0);

			result.add(new TypeCompletion(c.getQualifiedName().toString() + ".class"));

			List<TypeMirror> parents = new LinkedList<>();

			parents.add(c.getSuperclass());
			parents.addAll(c.getInterfaces());

			for(TypeMirror tm : parents)
			{
				if(tm == null || tm.getKind() != TypeKind.DECLARED)
				{
					continue;
				}

				TypeElement type = (TypeElement) processingEnv.getTypeUtils().asElement(tm);

				if(!jlObject.equals(type))
				{
					toProcess.add(type);
				}
			}
		}

		return result;
	}

	private static final class TypeCompletion implements Completion
	{

		private final String _type;

		public TypeCompletion(String type)
		{
			this._type = type;
		}

		public String getValue()
		{
			return _type;
		}

		public String getMessage()
		{
			return null;
		}
	}
}
