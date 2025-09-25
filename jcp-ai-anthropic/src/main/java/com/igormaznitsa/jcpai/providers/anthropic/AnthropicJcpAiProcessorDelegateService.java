package com.igormaznitsa.jcpai.providers.anthropic;

import com.igormaznitsa.jcpai.commons.AbstractJcpAiProcessor;
import com.igormaznitsa.jcpai.commons.SingletonProcessorDelegateService;

public class AnthropicJcpAiProcessorDelegateService extends SingletonProcessorDelegateService {

  private static final AnthropicJcpAiProcessor INSTANCE = new AnthropicJcpAiProcessor();

  @Override
  protected AbstractJcpAiProcessor findInstance() {
    return INSTANCE;
  }
}
