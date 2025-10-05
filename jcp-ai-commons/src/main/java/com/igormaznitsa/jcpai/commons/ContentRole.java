package com.igormaznitsa.jcpai.commons;

public enum ContentRole {
  SYSTEM(true),
  USER(false),
  ASSISTANT(true),
  DEVELOPER(false);

  private final boolean model;

  ContentRole(final boolean model) {
    this.model = model;
  }

  public boolean isModel() {
    return this.model;
  }
}
