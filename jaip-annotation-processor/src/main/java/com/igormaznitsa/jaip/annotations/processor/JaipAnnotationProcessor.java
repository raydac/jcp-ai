package com.igormaznitsa.jaip.annotations.processor;

import static com.igormaznitsa.jaip.annotations.processor.utils.AnnotationUtils.findAllInternalMmdTopicAnnotations;
import static com.igormaznitsa.jaip.annotations.processor.utils.AnnotationUtils.findMmdComments;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.igormaznitsa.jaip.annotations.HasMmdMarkedElements;
import com.igormaznitsa.jaip.annotations.JaipPrompt;
import com.igormaznitsa.jaip.annotations.JaipPrompts;
import com.igormaznitsa.jaip.annotations.processor.utils.AnnotationUtils;
import com.igormaznitsa.jaip.annotations.processor.utils.AnnotationUtils.UriLine;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.File;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;

@SupportedOptions({
})
public class JaipAnnotationProcessor extends AbstractProcessor {

  private static final Map<String, Class<? extends Annotation>> ANNOTATIONS =
      Map.of(
          JaipPrompt.class.getName(), JaipPrompt.class,
          JaipPrompts.class.getName(), JaipPrompts.class,
          HasMmdMarkedElements.class.getName(), HasMmdMarkedElements.class);
  private Trees trees;
  private SourcePositions sourcePositions;
  private Messager messager;
  private Types types;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ANNOTATIONS.keySet();
  }

  @Override
  public synchronized void init(final ProcessingEnvironment processingEnv) {
    super.init(processingEnv);

    this.trees = Trees.instance(processingEnv);
    this.sourcePositions = this.trees.getSourcePositions();
    this.messager = processingEnv.getMessager();
    this.types = processingEnv.getTypeUtils();
  }

  @Override
  public boolean process(
      final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

    final List<FoundJaipAnnotation> foundAnnotationList = new ArrayList<>();

    for (final TypeElement annotation : annotations) {
      final Set<? extends Element> annotatedElements =
          roundEnv.getElementsAnnotatedWith(annotation);

      final Class<? extends Annotation> annotationClass =
          ANNOTATIONS.get(annotation.getQualifiedName().toString());
      requireNonNull(
          annotationClass,
          () -> "Unexpectedly annotation class not found for " + annotation.getQualifiedName());

      annotatedElements.forEach(
          element -> {
            final List<Map.Entry<? extends Annotation, UriLine>> annotationInstances =
                AnnotationUtils.findAnnotationsWithPositions(this.sourcePositions, this.trees,
                    element, annotationClass);
            final long startPosition =
                AnnotationUtils.findStartPosition(this.sourcePositions, this.trees, element);

            if (annotationClass == JaipPrompts.class) {
              annotationInstances.stream()
                  .flatMap(
                      pair -> AnnotationUtils.findAnnotationsWithPositions(this.sourcePositions,
                          trees, element, JaipPrompt.class).stream())
                  .forEach(
                      pair -> foundAnnotationList.add(
                          new FoundJaipAnnotation(
                              element, pair.getKey(), new File(pair.getValue().getUri()).toPath(),
                              pair.getValue().getLine(), startPosition, false)));
            } else if (annotationClass == HasMmdMarkedElements.class) {
              final UriLine elementSrcPosition =
                  AnnotationUtils.findElementSrcPosition(this.sourcePositions,
                      this.trees,
                      element);

              annotationInstances
                  .forEach(pair -> {
                    if (element instanceof ExecutableElement) {
                      try {
                        final Optional<String> elementSources =
                            AnnotationUtils.findElementSources(this.sourcePositions,
                                this.trees, element);

                        if (elementSources.isPresent()) {
                          final Path elementFile =
                              new File(elementSrcPosition.getUri()).toPath();
                          final AtomicInteger counter = new AtomicInteger();
                          findMmdComments(elementSrcPosition.getLine(), 0,
                              elementSources.get()).forEach(comment -> {
                            counter.incrementAndGet();
                            foundAnnotationList.add(
                                new FoundJaipAnnotation(element, comment, elementFile,
                                    comment.line(), comment.position(), true));
                          });
                          if (counter.get() > 0) {
                            this.messager.printMessage(NOTE,
                                "Found " + counter.get() + " internal comment-markers", element);
                          }
                        }
                      } catch (Exception ex) {
                        this.messager.printMessage(ERROR,
                            "Can't read sources for element: " + ex.getMessage(), element);
                      }


                      findAllInternalMmdTopicAnnotations(
                          this.trees,
                          (ExecutableElement) element)
                          .forEach(pairInternalAnnotations -> {
                            final long localStartPosition =
                                AnnotationUtils.findStartPosition(this.sourcePositions,
                                    this.trees,
                                    pairInternalAnnotations.getValue());
                            final UriLine localPosition =
                                AnnotationUtils.findElementSrcPosition(this.sourcePositions,
                                    this.trees,
                                    pairInternalAnnotations.getValue());
                            foundAnnotationList.add(new FoundJaipAnnotation(
                                pairInternalAnnotations.getValue(),
                                pairInternalAnnotations.getKey(),
                                new File(localPosition.getUri()).toPath(),
                                localPosition.getLine(), localStartPosition, false));
                          });
                    } else {
                      this.messager.printMessage(WARNING,
                          "Detected unexpected element marked by @" +
                              HasMmdMarkedElements.class.getSimpleName() + ": " +
                              element.getClass().getSimpleName(), element);
                    }
                  });
            } else {
              annotationInstances
                  .forEach(
                      pair -> foundAnnotationList.add(
                          new FoundJaipAnnotation(
                              element, pair.getKey(), new File(pair.getValue().getUri()).toPath(),
                              pair.getValue().getLine(), startPosition, false)
                      )
                  );
            }
          });
    }


    if (!foundAnnotationList.isEmpty()) {
      this.messager.printMessage(
          NOTE,
          format(
              "MMD annotation processor has found %d annotations to process",
              foundAnnotationList.size()));

      foundAnnotationList.sort(
          Comparator.comparing(o -> o.getElement().getSimpleName().toString()));

    }

    return true;
  }

}
