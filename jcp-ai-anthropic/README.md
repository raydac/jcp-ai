# Anthropic

As REST client I use the [`anthropics/anthropic-sdk-java`](https://github.com/anthropics/anthropic-sdk-java) library. It allows communication with [ClaudeAI LLMs](https://claude.ai).

## Maven dependency

The recommended dependency section for Java Comment Preprocessor

```xml
<dependencies>
    <dependency>
        <groupId>com.igormaznitsa</groupId>
        <artifactId>jcp-ai-anthropic</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.anthropic</groupId>
        <artifactId>anthropic-java</artifactId>
        <version>2.2.0</version>
    </dependency>
</dependencies>
```

## Specific variables

The common variables can be found [here](../README.md#common-variables) 

- __jcpai.anthropic.base.url__ - base URL for REST calls, it will be provided for REST client
- __jcpai.anthropic.model__ - name of the model to be used for prompt processing
- __jcpai.anthropic.auth.token__ - authentication token if needed
- __jcpai.anthropic.api.key__ - api key if needed

