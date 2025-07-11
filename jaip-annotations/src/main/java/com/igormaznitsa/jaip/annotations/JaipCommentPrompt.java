package com.igormaznitsa.jaip.annotations;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
public class JaipCommentPrompt implements JaipPrompt {
  public static JaipCommentPrompt of(int line, int position, String args, String others) {
    return null;
  }

  public static JaipCommentPrompt of(int line, int position, String args) {
    return null;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return JaipPrompt.class;
  }

  public long line() {
    return -1;
  }

  public long position() {
    return -1;
  }
}
