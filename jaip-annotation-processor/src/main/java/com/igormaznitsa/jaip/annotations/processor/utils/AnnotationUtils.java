/*
 * Copyright (C) 2015-2022 Igor A. Maznitsa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igormaznitsa.jaip.annotations.processor.utils;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

import com.igormaznitsa.jaip.annotations.JaipCommentPrompt;
import com.igormaznitsa.jaip.annotations.JaipPrompt;
import com.igormaznitsa.jaip.annotations.JaipPrompts;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

public final class AnnotationUtils {

  private static final Pattern PATTERN_JAIP_PROMPT_AS_COMMENT =
      Pattern.compile("^\\s*@MmdTopic\\s*(?:\\((.*)\\)(.*)|(.*))$", Pattern.CASE_INSENSITIVE);

  private AnnotationUtils() {
  }

  /**
   * Find source line position for element.
   *
   * @param sourcePositions auxiliary utility class, must not be null
   * @param trees           auxiliary utility class, must not be null
   * @param element         element which position should be found
   * @return formed container with URI and line number.
   */
  public static UriLine findElementSrcPosition(
      final SourcePositions sourcePositions, final Trees trees, final Element element) {
    final TreePath treePath = trees.getPath(element);
    final CompilationUnitTree compilationUnit = treePath.getCompilationUnit();

    final long startPosition = findStartPosition(sourcePositions, trees, element);
    final long lineNumber = compilationUnit.getLineMap().getLineNumber(startPosition);
    return new UriLine(compilationUnit.getSourceFile().toUri(), lineNumber);
  }

  /**
   * Find starting position of element inside file.
   *
   * @param sourcePositions auxiliary utility class, must not be null
   * @param trees           auxiliary utility class, must not be null
   * @param element         element which position should be found
   * @return start position of element inside file or -1 if not found
   * @see javax.tools.Diagnostic#NOPOS
   */
  public static long findStartPosition(
      final SourcePositions sourcePositions, final Trees trees, final Element element) {
    final TreePath treePath = trees.getPath(element);
    final CompilationUnitTree compilationUnit = treePath.getCompilationUnit();
    return sourcePositions.getStartPosition(compilationUnit, treePath.getLeaf());
  }

  private static AnnotationValue getAnnotationValue(final AnnotationMirror annotationMirror,
                                                    final String key) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror
        .getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().toString().equals(key)) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Find repeated annotations,
   *
   * @param containerMirror         annotation mirror contains repeatable annotations, must not be null
   * @param repeatedAnnotationClass target repeatable annotation class, must not be null
   * @return list of found annotation mirrors, can't be null
   * @since 1.6.8
   */
  public static List<AnnotationMirror> findRepeatedAnnotations(
      final AnnotationMirror containerMirror,
      final Class<? extends Annotation> repeatedAnnotationClass) {
    final List<AnnotationMirror> result = new ArrayList<>();

    final Repeatable repeatable = repeatedAnnotationClass.getAnnotation(Repeatable.class);
    if (repeatable == null) {
      return result;
    }
    final AnnotationValue value = getAnnotationValue(containerMirror, "value");
    if (value == null) {
      return result;
    }
    if (value.getValue() instanceof List<?>) {
      for (Object a : (List<?>) value.getValue()) {
        if (a instanceof AnnotationMirror
            && (repeatedAnnotationClass.getName()
            .equals(((AnnotationMirror) a).getAnnotationType().toString()))) {
          result.add((AnnotationMirror) a);
        }
      }
    }
    return result;
  }

  /**
   * Find annotations for an element by their class and form pairs with their positions in sources.
   */
  public static List<Map.Entry<? extends Annotation, UriLine>> findAnnotationsWithPositions(
      final SourcePositions sourcePositions,
      final Trees trees,
      final Element element,
      final Class<? extends Annotation> annotationClass
  ) {
    final Annotation[] annotations = element.getAnnotationsByType(annotationClass);
    if (annotations.length == 0) {
      return List.of();
    } else {
      final TreePath treePath = trees.getPath(element);
      final CompilationUnitTree compilationUnit = treePath.getCompilationUnit();

      final Repeatable repeatable = annotationClass.getAnnotation(Repeatable.class);
      final String repeatableClassName = repeatable == null ? "" : repeatable.value().getName();

      final List<UriLine> lines = element.getAnnotationMirrors().stream()
          .flatMap(x -> {
            final String className = x.getAnnotationType().toString();
            if (className.equals(annotationClass.getName())) {
              return Stream.of(x);
            } else if (className.equals(repeatableClassName)) {
              return findRepeatedAnnotations(x, annotationClass).stream();
            } else {
              return Stream.empty();
            }
          })
          .filter(x -> x.getAnnotationType().toString().equals(annotationClass.getName()))
          .map(
              x -> findElementSrcPosition(compilationUnit, trees, sourcePositions,
                  trees.getTree(element, x)))
          .collect(toList());

      if (annotations.length != lines.size()) {
        throw new IllegalStateException(
            String.format(
                "Unexpectedly can't find same number (%d) of annotation mirrors (%d) for type %s",
                annotations.length,
                lines.size(),
                annotationClass.getName())
        );
      }

      final List<Map.Entry<? extends Annotation, UriLine>> result = new ArrayList<>();
      for (int i = 0; i < annotations.length; i++) {
        result.add(Map.entry(annotations[i], lines.get(i)));
      }
      return result;
    }
  }

  /**
   * Find source line position for tree.
   *
   * @param compilationUnitTree compilation unit tree object, must not be null
   * @param sourcePositions     auxiliary utility class, must not be null
   * @param tree                tree element which position should be found
   * @return formed container with URI and line number.
   * @since 1.6.8
   */
  public static UriLine findElementSrcPosition(
      final CompilationUnitTree compilationUnitTree,
      final Trees trees,
      final SourcePositions sourcePositions, final Tree tree) {
    final TreePath treePath = trees.getPath(compilationUnitTree, tree);
    final long startPosition =
        sourcePositions.getStartPosition(compilationUnitTree, treePath.getLeaf());
    final long lineNumber = compilationUnitTree.getLineMap().getLineNumber(startPosition);
    return new UriLine(compilationUnitTree.getSourceFile().toUri(), lineNumber);
  }

  /**
   * Find and read element sources.
   *
   * @param sourcePositions auxiliary utility class, must not be null
   * @param trees           auxiliary utility class, must not be null
   * @param element         element which position should be found
   * @return found read sources if they are presented, empty if not provided
   * @throws IOException if there is a problem to read sources of access error
   * @since 1.6.6
   */
  public static Optional<String> findElementSources(final SourcePositions sourcePositions,
                                                    final Trees trees, final Element element)
      throws IOException {

    final TreePath treePath = trees.getPath(element);
    final CompilationUnitTree compilationUnit = treePath.getCompilationUnit();

    final long startPosition = findStartPosition(sourcePositions, trees, element);
    if (startPosition < 0) {
      return Optional.empty();
    }
    final long endPosition = findEndPosition(sourcePositions, trees, element);
    if (endPosition < 0) {
      return Optional.empty();
    }

    final int textLength = (int) (endPosition - startPosition);
    if (textLength < 0) {
      return Optional.empty();
    }
    if (textLength == 0) {
      return Optional.of("");
    }

    final char[] buffer = new char[textLength];
    final JavaFileObject javaFileObject = compilationUnit.getSourceFile();
    if (javaFileObject != null && javaFileObject.getKind() == JavaFileObject.Kind.SOURCE) {
      int position = 0;
      int restChars = textLength;
      try (final Reader reader = compilationUnit.getSourceFile().openReader(true)) {
        if (reader.skip(startPosition) != startPosition) {
          throw new IOException(
              "Can't skip " + startPosition + " chars in source file: " + javaFileObject);
        }
        while (restChars > 0) {
          final int read = reader.read(buffer, position, restChars);
          if (read < 0) {
            break;
          }
          position += read;
          restChars -= read;
        }
        if (restChars != 0) {
          throw new IOException(
              "Can't read " + textLength + " chars from position " + startPosition + ": " +
                  javaFileObject);
        }
        return Optional.of(new String(buffer));
      }
    }
    return Optional.empty();
  }

  /**
   * Find ending position of element inside file.
   *
   * @param sourcePositions auxiliary utility class, must not be null
   * @param trees           auxiliary utility class, must not be null
   * @param element         element which position should be found
   * @return end position of element inside file or -1 if not found
   * @see javax.tools.Diagnostic#NOPOS
   * @since 1.6.6
   */
  public static long findEndPosition(
      final SourcePositions sourcePositions, final Trees trees, final Element element) {
    final TreePath treePath = trees.getPath(element);
    final CompilationUnitTree compilationUnit = treePath.getCompilationUnit();
    return sourcePositions.getEndPosition(compilationUnit, treePath.getLeaf());
  }

  /**
   * Find first annotations for element, start of list contain annotations found among enclosing elements, tails contains annotations found among ancestors.
   *
   * @param element        element to find annotations, can be null
   * @param annotationType annotation type, must not be null
   * @param typeUtils      type utils class, must not be null
   * @param <A>            annotation type
   * @return list of annotations and elements found by request, must not be null
   */
  public static <A extends Annotation> List<Map.Entry<A, Element>> findFirstAmongEnclosingAndAncestors(
      final Element element,
      final Class<A> annotationType,
      final Types typeUtils
  ) {
    final List<Map.Entry<A, Element>> result = new ArrayList<>();
    result.addAll(findFirstWithEnclosing(element, annotationType, false));
    result.addAll(findFirstWithAncestors(element, annotationType, typeUtils, false));
    return result;
  }

  /**
   * Find all class or interface elements.
   *
   * @param element element which enclosing elements to find, can be null
   * @return found class and interface elements for element, can't be null
   */
  public static List<Element> findAllTypeElements(final Element element) {
    if (element == null) {
      return List.of();
    }

    List<Element> result = new ArrayList<>();
    if (element.getKind().isInterface() || element.getKind().isClass()) {
      result.add(element);
    }
    result.addAll(findAllTypeElements(element.getEnclosingElement()));
    return result;
  }

  /**
   * Find enclosing type for element (i.e. class or interface)
   *
   * @param element target element, must not be null
   * @return found type element or empty otherwise
   */
  public static Optional<? extends Element> findEnclosingType(final Element element) {
    if (element == null) {
      return Optional.empty();
    }
    if (element.getKind() == ElementKind.MODULE || element.getKind() == ElementKind.PACKAGE) {
      return element.getEnclosedElements().stream()
          .filter(e -> e.getKind().isClass() || e.getKind().isInterface())
          .findFirst();
    } else if (element.getKind().isClass() || element.getKind().isInterface()) {
      return Optional.of(element);
    } else {
      return findEnclosingType(element.getEnclosingElement());
    }
  }

  /**
   * Find first required annotations among enclosing elements.
   *
   * @param element        target element, can be null
   * @param annotationType annotation to find, must not be null
   * @param includeElement flag to include target element into search
   * @param <A>            annotation type
   * @return list of pairs found annotations, must not be null
   */
  public static <A extends Annotation> List<Map.Entry<A, Element>> findFirstWithEnclosing(
      final Element element, final Class<A> annotationType, final boolean includeElement) {
    if (element == null) {
      return List.of();
    }
    final List<A> found =
        includeElement ? Arrays.asList(element.getAnnotationsByType(annotationType)) : List.of();
    if (found.isEmpty()) {
      return findFirstWithEnclosing(element.getEnclosingElement(), annotationType, true);
    } else {
      return found.stream().map(x -> Map.entry(x, element)).collect(Collectors.toList());
    }
  }

  /**
   * Find first required annotations among ancestors.
   *
   * @param element        target element, can be null
   * @param annotationType annotation to find, must not be null
   * @param includeElement flag to include target element into search
   * @param <A>            annotation type
   * @return list of pairs found annotations, must not be null
   */
  public static <A extends Annotation> List<Map.Entry<A, Element>> findFirstWithAncestors(
      final Element element,
      final Class<A> annotationType,
      final Types typeUtils,
      final boolean includeElement) {
    if (element == null) {
      return List.of();
    }
    if (includeElement) {
      final A[] found = element.getAnnotationsByType(annotationType);
      if (found.length > 0) {
        return Stream.of(found)
            .map(x -> Map.entry(x, element))
            .collect(Collectors.toList());
      }
    }

    final List<Element> superElements = findAllTypeElements(element)
        .stream()
        .flatMap(x -> {
          try {
            return typeUtils.directSupertypes(x.asType()).stream();
          } catch (IllegalArgumentException ex) {
            return Stream.empty();
          }
        })
        .map(typeUtils::asElement)
        .collect(Collectors.toList());

    return superElements.stream()
        .map(x -> findFirstWithAncestors(x, annotationType, typeUtils, true))
        .filter(x -> !x.isEmpty())
        .findFirst()
        .orElse(List.of());
  }

  /**
   * Find all mmd mark comments in sources provided as a String.
   */
  public static List<JaipCommentPrompt> findMmdComments(final long initialLine,
                                                        final long initialPosition,
                                                        final String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }

    final List<JaipCommentPrompt> result = new ArrayList<>();

    final StringReader reader = new StringReader(text);
    final AtomicInteger positionCounter = new AtomicInteger((int) initialPosition);
    final AtomicInteger lineCounter = new AtomicInteger((int) initialLine);
    try {
      while (true) {
        final PositionedText nextLine =
            findSingleLineCommentText(reader, lineCounter, positionCounter);
        if (nextLine == null) {
          break;
        }

        final Matcher matcher = PATTERN_JAIP_PROMPT_AS_COMMENT.matcher(nextLine.text);
        if (matcher.find()) {
          final String args = matcher.group(1);
          final String others = matcher.group(2);
          final String title = matcher.group(3);
          if (title != null) {
            result.add(JaipCommentPrompt.of(nextLine.line, nextLine.position, title));
          } else {
            result.add(JaipCommentPrompt.of(nextLine.line, nextLine.position, args, others));
          }
        }
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Unexpected IOException", ex);
    }

    return result;
  }

  private static void skipTillClosingJavaComments(final Reader reader,
                                                  final AtomicInteger lineCounter,
                                                  final AtomicInteger positionCounter)
      throws IOException {
    boolean starFound = false;

    while (true) {
      final int chr = reader.read();
      if (chr < 0) {
        return;
      }
      updateLinePositionCounters(chr, lineCounter, positionCounter);
      if (starFound) {
        if (chr == '/') {
          return;
        } else {
          starFound = chr == '*';
        }
      } else if (chr == '*') {
        starFound = true;
      }
    }
  }

  private static void updateLinePositionCounters(final int character, final AtomicInteger line,
                                                 final AtomicInteger position) {
    if (character >= 0) {
      position.incrementAndGet();
      if (character == '\n') {
        position.set(0);
        line.incrementAndGet();
      }
    }
  }

  private static PositionedText findSingleLineCommentText(
      final Reader reader,
      final AtomicInteger lineCounter,
      final AtomicInteger positionCounter
  ) throws IOException {
    final int STATE_NORMAL = 0;
    final int STATE_INSIDE_STRING = 1;
    final int STATE_NEXT_SPECIAL_CHAR = 2;
    final int STATE_FORWARD_SLASH = 3;
    int state = STATE_NORMAL;

    while (true) {
      final int chr = reader.read();
      if (chr < 0) {
        break;
      }
      updateLinePositionCounters(chr, lineCounter, positionCounter);

      switch (state) {
        case STATE_NORMAL: {
          switch (chr) {
            case '\"': {
              state = STATE_INSIDE_STRING;
            }
            break;
            case '/': {
              state = STATE_FORWARD_SLASH;
            }
            break;
            default: {
              // ignore
            }
            break;
          }
        }
        break;
        case STATE_FORWARD_SLASH: {
          switch (chr) {
            case '*': {
              skipTillClosingJavaComments(reader, lineCounter, positionCounter);
              state = STATE_NORMAL;
            }
            break;
            case '/': {
              final int startPosition = positionCounter.get();
              final int startLine = lineCounter.get();

              final StringBuilder builder = new StringBuilder();
              while (true) {
                final int nextChar = reader.read();
                if (nextChar < 0) {
                  break;
                }
                updateLinePositionCounters(nextChar, lineCounter, positionCounter);
                if (nextChar == '\r' || nextChar == '\n') {
                  break;
                }
                builder.append((char) nextChar);
              }
              return new PositionedText(builder.toString().trim(), startLine, startPosition);
            }
            default: {
              state = STATE_NORMAL;
            }
            break;
          }
        }
        break;
        case STATE_INSIDE_STRING: {
          switch (chr) {
            case '\\': {
              state = STATE_NEXT_SPECIAL_CHAR;
            }
            break;
            case '\"': {
              state = STATE_NORMAL;
            }
            break;
            default:
              break;
          }
        }
        break;
        case STATE_NEXT_SPECIAL_CHAR: {
          state = STATE_INSIDE_STRING;
        }
        break;
        default:
          throw new IllegalStateException("Unexpected state: " + state);
      }
    }
    return null;
  }

  /**
   * Find all MmdTopic annotations inside executable element.
   *
   * @param trees             auxiliary utility class, must not be null
   * @param executableElement scanned executable element
   * @return list of pairs for all found MmdTopic annotations and their enclosing elements.
   * @since 1.6.6
   */
  public static List<Map.Entry<JaipPrompt, Element>> findAllInternalMmdTopicAnnotations(
      final Trees trees,
      final ExecutableElement executableElement) {

    final CompilationUnitTree compilationUnitTree =
        trees.getPath(executableElement).getCompilationUnit();

    final List<Map.Entry<JaipPrompt, Element>> result = new ArrayList<>();
    final MethodTree methodTree = trees.getTree(executableElement);
    methodTree.getBody().getStatements()
        .forEach(statement -> result.addAll(
            extractMmdTopicAnnotationsFromTree(compilationUnitTree, trees, statement)));
    return result;
  }

  private static List<Map.Entry<JaipPrompt, Element>> extractMmdTopicAnnotationsFromTree(
      final CompilationUnitTree compilationUnitTree,
      final Trees trees,
      final StatementTree statementTree) {

    final AtomicReference<CompilationUnitTree> compilationUnitTreeRef =
        new AtomicReference<>(compilationUnitTree);

    final TreeScanner<List<Map.Entry<JaipPrompt, Element>>, Void> scanner =
        new TreeScanner<>() {

          @Override
          public List<Map.Entry<JaipPrompt, Element>> visitCompilationUnit(
              final CompilationUnitTree node,
              final Void unused) {
            compilationUnitTreeRef.set(node);
            return List.of();
          }

          @Override
          public List<Map.Entry<JaipPrompt, Element>> visitVariable(
              final VariableTree node, final Void unused) {
            final TreePath treePath = trees.getPath(compilationUnitTreeRef.get(), node);
            final Element variableElement = trees.getElement(treePath);
            return
                concat(
                    Arrays.stream(variableElement.getAnnotationsByType(JaipPrompts.class))
                        .flatMap(x -> Arrays.stream(x.value())),
                    Arrays.stream(variableElement.getAnnotationsByType(JaipPrompt.class)))
                    .map(annotation -> entry(annotation, variableElement))
                    .collect(toList());
          }

          @Override
          public List<Map.Entry<JaipPrompt, Element>> reduce(
              List<Map.Entry<JaipPrompt, Element>> r1,
              List<Map.Entry<JaipPrompt, Element>> r2) {
            if (r1 == null) {
              return r2;
            }
            if (r2 == null) {
              return r1;
            }
            return concat(r1.stream(), r2.stream()).collect(toList());
          }
        };
    return Objects.requireNonNullElse(scanner.scan(statementTree, null), List.of());
  }

  private static final class PositionedText {
    private final String text;
    private final int line;
    private final int position;

    PositionedText(final String text, final int line, final int position) {
      this.line = line;
      this.position = position;
      this.text = text;
    }
  }

  /**
   * Auxiliary container class to keep information about line and sources.
   */
  public static final class UriLine {
    private final URI uri;
    private final long line;

    private UriLine(final URI uri, final long line) {
      this.uri = requireNonNull(uri);
      this.line = line;
    }

    public URI getUri() {
      return this.uri;
    }

    public long getLine() {
      return this.line;
    }
  }

}
