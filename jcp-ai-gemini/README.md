# Gemini AI

As REST client I use the [`googleapis/java-genai`](https://github.com/googleapis/java-genai) library. It allows communication with [Gemini AI LLMs]( (https://gemini.google.com/app)).

## Maven dependency

The recommended dependency section for Java Comment Preprocessor

```xml
<dependencies>
    <dependency>
        <groupId>com.igormaznitsa</groupId>
        <artifactId>jcp-ai-gemini</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.google.genai</groupId>
        <artifactId>google-genai</artifactId>
        <version>1.9.0</version>
    </dependency>
</dependencies>
```

## Specific variables

The common variables can be found [here](../README.md#common-variables)

- __jcpai.gemini.base.url__ - base URL for REST calls, it will be provided for REST client
- __jcpai.gemini.model__ - name of the model to be used for prompt processing
- __jcpai.gemini.project.id__ - project id for authentication if needed
- __jcpai.gemini.api.key__ - api key for authentication if needed
- __jcpai.gemini.generate.content.config.json__ - string contains JSON config for generate content
- __jcpai.gemini.http.config.json__ - string contain JSON config for client http options
- __jcpai.gemini.client.options.json__ - string contain whole JSON config for client

