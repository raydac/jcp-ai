package com.igormaznitsa.jcpai.commons;

import static java.util.Objects.requireNonNull;

public final class ContentRecord {
  private final ContentRole role;
  private final String text;

  private ContentRecord(final ContentRole role, final String text) {
    this.role = requireNonNull(role);
    this.text = requireNonNull(text);
  }

  public static ContentRecord of(final ContentRole role, final String text) {
    return new ContentRecord(role, text);
  }

  public ContentRole getRole() {
    return this.role;
  }

  public String getText() {
    return this.text;
  }
}
