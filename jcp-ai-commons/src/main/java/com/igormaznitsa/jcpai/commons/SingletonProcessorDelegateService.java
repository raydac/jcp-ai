package com.igormaznitsa.jcpai.commons;

import com.igormaznitsa.jcp.context.CommentTextProcessor;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.extension.PreprocessorExtension;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Auxiliary class allows to have only instanced among services of processor instead of instance per service
 *
 * @since 1.1.0
 */
public abstract class SingletonProcessorDelegateService implements CommentTextProcessor,
    PreprocessorExtension {

  public SingletonProcessorDelegateService() {

  }

  protected abstract AbstractJcpAiProcessor findInstance();

  @Override
  public String processUncommentedText(PreprocessorContext preprocessorContext, int i, String s) {
    return this.delegateExecution(
        t -> t.processUncommentedText(preprocessorContext, i, s));
  }

  private <R> R delegateExecution(Function<AbstractJcpAiProcessor, R> supplier) {
    final AbstractJcpAiProcessor instance = this.findInstance();
    if (instance != null) {
      return supplier.apply(instance);
    }
    throw new IllegalStateException("Not inited processor delegate");
  }

  @Override
  public boolean hasAction(final int i) {
    return this.delegateExecution(t -> t.hasAction(i));
  }

  @Override
  public boolean hasUserFunction(final String name, final Set<Integer> arity) {
    return this.delegateExecution(t -> t.hasUserFunction(name, arity));
  }

  @Override
  public boolean processAction(final PreprocessorContext preprocessorContext,
                               final List<Value> values) {
    return this.delegateExecution(t -> t.processAction(preprocessorContext, values));
  }

  @Override
  public Value processUserFunction(final PreprocessorContext preprocessorContext,
                                   final String s,
                                   final List<Value> values) {
    return this.delegateExecution(t -> t.processUserFunction(preprocessorContext, s, values));
  }

  @Override
  public Set<Integer> getUserFunctionArity(final String s) {
    return this.delegateExecution(t -> t.getUserFunctionArity(s));
  }

  @Override
  public void onContextStarted(final PreprocessorContext context) {
    this.delegateExecution(t -> {
      t.onContextStarted(context);
      return true;
    });
  }

  @Override
  public void onContextStopped(PreprocessorContext context, Throwable error) {
    this.delegateExecution(t -> {
      t.onContextStopped(context, error);
      return true;
    });
  }
}
