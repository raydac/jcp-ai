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

package com.igormaznitsa.jaip.annotations.processor;

import static java.util.Objects.requireNonNull;

import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.Objects;
import javax.lang.model.element.Element;

/**
 * Immutable data object wrapping found MMD annotation, its path and line number.
 */
public class FoundJaipAnnotation {
  /**
   * Found annotation.
   */
  private final Annotation annotation;
  /**
   * Path to source class file.
   */
  private final Path path;
  /**
   * Line of the annotation in the source class file.
   */
  private final long line;

  /**
   * Start position of element inside file
   */
  private final long startPositionInsideFile;

  /**
   * Element providing the annotation.
   */
  private final Element element;

  /**
   * Flag shows that wrapper created for some internal block of the element and not related to the element directly.
   *
   * @since 1.6.6
   */
  private final boolean internal;

  /**
   * Constructor.
   *
   * @param element    element providing annotation, must not be null
   * @param annotation found annotation, must not be null
   * @param path       source class file path, must not be null
   * @param line       line number of the annotation in the source class file
   * @param internal   flag shows that wrapper created for some internal block of the element and not related to the element directly
   * @throws NullPointerException     thrown if any argument is null
   * @throws IllegalArgumentException thrown if line is zero or negative one
   */
  public FoundJaipAnnotation(
      final Element element,
      final Annotation annotation,
      final Path path,
      final long line,
      final long startPositionInsideFile,
      final boolean internal
  ) {
    this.internal = internal;
    this.element = requireNonNull(element);
    this.annotation = requireNonNull(annotation);
    this.path = requireNonNull(path);
    if (line < 1 || startPositionInsideFile < 0) {
      throw new IllegalArgumentException("Can't detect position of element inside file: " + line);
    }
    this.line = line;
    this.startPositionInsideFile = startPositionInsideFile;
  }

  public long getStartPositionInsideFile() {
    return this.startPositionInsideFile;
  }

  public boolean isInternal() {
    return this.internal;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.annotation, this.path, this.line);
  }

  @Override
  public boolean equals(final Object that) {
    if (that == null) {
      return false;
    }
    if (that == this) {
      return true;
    }
    if (that instanceof FoundJaipAnnotation) {
      final FoundJaipAnnotation thatInstance = (FoundJaipAnnotation) that;
      return this.annotation.equals(thatInstance.annotation)
          && this.line == thatInstance.line
          && this.startPositionInsideFile == thatInstance.startPositionInsideFile
          && this.path.equals(thatInstance.path);
    }
    return false;
  }

  /**
   * Get annotation and automatically cast to type.
   *
   * @param <A> cast to annotation type
   * @return the annotation, must not be null
   */
  @SuppressWarnings("unchecked")
  public <A extends Annotation> A asAnnotation() {
    return (A) this.annotation;
  }

  /**
   * Get source class file path.
   *
   * @return the source class file path, must not be null
   */
  public Path getPath() {
    return this.path;
  }

  /**
   * Get line number of the annotation in source class file.
   *
   * @return line number, positive one
   */
  public long getLine() {
    return this.line;
  }

  /**
   * Get the base annotated element.
   *
   * @return the annotated element, must not be null
   */
  public Element getElement() {
    return this.element;
  }

  @Override
  public String toString() {
    return "MmdAnnotationWrapper{" + "position=" + this.getPath().toString() + ":" +
        this.getLine() + '}';
  }
}
