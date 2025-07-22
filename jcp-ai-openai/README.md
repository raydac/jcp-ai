# OpenAI

As REST client I use the [`openai/openai-java`](https://github.com/openai/openai-java) library. It allows communication with [ChatGPT LLMs](https://chatgpt.com/).

## Maven dependency

The recommended dependency section for Java Comment Preprocessor

```xml
<dependencies>
    <dependency>
        <groupId>com.igormaznitsa</groupId>
        <artifactId>jcp-ai-openai</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.openai</groupId>
        <artifactId>openai-java</artifactId>
        <version>2.16.0</version>
    </dependency>
</dependencies>
```

## Specific variables

The common variables can be found [here](../README.md#common-variables)

- __jcpai.openai.base.url__ - base URL for REST calls, it will be provided for REST client
- __jcpai.openai.model__ - name of the model to be used for prompt processing
- __jcpai.openai.project__ - name of a project to process prompt
- __jcpai.openai.org.id__ - organization id used for authentication
- __jcpai.openai.webhook.secret__ - webhook secret parameter if needed
- __jcpai.openai.api.key__ - api key if needed
