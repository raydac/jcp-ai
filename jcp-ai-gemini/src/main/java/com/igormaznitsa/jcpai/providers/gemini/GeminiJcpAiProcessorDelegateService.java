package com.igormaznitsa.jcpai.providers.gemini;

import com.igormaznitsa.jcpai.commons.AbstractJcpAiProcessor;
import com.igormaznitsa.jcpai.commons.SingletonProcessorDelegateService;

public class GeminiJcpAiProcessorDelegateService extends SingletonProcessorDelegateService {

  private static final GeminiJcpAiProcessor INSTANCE = new GeminiJcpAiProcessor();

  @Override
  protected AbstractJcpAiProcessor findInstance() {
    return INSTANCE;
  }
}
