package com.igormaznitsa.jcpai.providers.openai;

import com.igormaznitsa.jcpai.commons.AbstractJcpAiProcessor;
import com.igormaznitsa.jcpai.commons.SingletonProcessorDelegateService;

public class OpenAiJcpAiProcessorDelegateService extends SingletonProcessorDelegateService {

  private static final OpenAiJcpAiProcessor INSTANCE = new OpenAiJcpAiProcessor();

  @Override
  protected AbstractJcpAiProcessor findInstance() {
    return INSTANCE;
  }
}
