![JCP-AI Project logo](assets/git_banner_optimized.svg)   
[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 17+](https://img.shields.io/badge/java-17%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven 3.8+](https://img.shields.io/badge/maven-3.8%2b-green.svg)](https://maven.apache.org/)      
[![Maven central](https://img.shields.io/badge/jcp--ai--anthropic-1.0.0-green.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|jcp-ai-anthropic|1.0.0|jar)
[![Maven central](https://img.shields.io/badge/jcp--ai--gemini-1.0.0-green.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|jcp-ai-gemini|1.0.0|jar)
[![Maven central](https://img.shields.io/badge/jcp--ai--openai-1.0.0-green.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|jcp-ai-openai|1.0.0|jar)

# Changelog

## 1.0.1-SNAPSHOT
 - improved prompt cache file processing, added threshold to remove old responses and variable
  `jcpai.prompt.cache.file.gc.threshold` to define unuse threshold

## 1.0.0 (22-jul-2025)
 - added adapter jcp-ai-anthropic 1.0.0
 - added adapter jcp-ai-gemini 1.0.0
 - added adapter jcp-ai-openai 1.0.0

# Pre-word

A long time ago, I created [one of the first Java preprocessors (called JCP)](https://github.com/raydac/java-comment-preprocessor) to make
building projects easier. The preprocessor's business is to read and change the program text. LLMs also work by
generating text based on given input, so combining them with a preprocessor is a logical step.

The JCP preprocessor allows you to keep blocks of text in comments, and starting from version 7.2.0, it can send them to
external services for processing. This gave me the idea to connect it with an LLM, so the result from the LLM could be
inserted directly into the program code (with minor normalizations).

Since the preprocessor can work with Maven, Gradle, and Ant, the ability to use LLMs automatically becomes available
for these build tools as well.

[![Arthur's acres sanctuary donation](assets/arthur_sanctuary_banner.png)](https://www.arthursacresanimalsanctuary.org/donate)

# How it works?

JCP-AI is a set of extension libraries that provide specialized services capable of calling external LLMs to process text. I’ve
added support for LLMs that have official open-source Java clients.   
Currently, it provides connectors for:

- [__Gemini AI__ (Google)](jcp-ai-gemini/README.md)
- [__Claude AI__ (Anthropic)](jcp-ai-anthropic/README.md)
- [__ChatGPT__ (OpenAI)](jcp-ai-openai/README.md)

![Sequence diagram](assets/sequence1.png)

The preprocessor discovers JCP-AI processors through Java's service registration mechanism, so it's enough for them to
appear in its classpath for them to become automatically available. For better flexibility and compatibility, JCP-AI
client libraries don’t include any client code themselves; instead, they rely on a client library already present in the
classpath.

# Example for Maven

Let's take a look at a small example, how to inject a bit AI into a Maven project and get some its profit during build.

## Tune pom.xml

As the first step, we should tune the project pom.xml, inject [JCP](https://github.com/raydac/java-comment-preprocessor) into build process and include JCP-AI. Let's use
Gemini AI as target LLM. The build section in the case should look like the snippet below:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.igormaznitsa</groupId>
            <artifactId>jcp</artifactId>
            <version>7.2.1</version>
            <executions>
                <execution>
                    <id>preprocessSources</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>preprocess</goal>
                    </goals>
                    <configuration>
                        <allowBlocks>true</allowBlocks>
                        <preserveIndents>true</preserveIndents>
                        <vars>
                            <jcpai.gemini.model>${jcpai.gemini.model}</jcpai.gemini.model>
                            <jcpai.gemini.api.key>${jcpai.gemini.api.key}</jcpai.gemini.api.key>
                            <jcpai.prompt.cache.file>${jcpai.prompt.cache.file}</jcpai.prompt.cache.file>
                        </vars>
                    </configuration>
                </execution>
            </executions>
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
        </plugin>
    </plugins>
</build>
```

Through the dependency section of the JCP plugin, we inject JCP-AI GeminiAI connector
and [its official REST client library](https://github.com/googleapis/java-genai). I specially don't include dependencies
to clients into JCP-AI connectors to change easily their version and don't keep dependency hard link between
dependencies.

## Inject prompt into sources

For work with prompts as text blocks I recommend you to turn on text block mode for JCP with flag `allowBlocks`, it
allows add some prompts directly into sources like below

```java
//$"""AI> code level is Java /*$mvn.project.property.maven.compiler.release$*/
//$"""AI> generate method implements fastest sort algorithm with minimal memory overhead, the speed is priority:
//$"""AI>     public static int [] fastSort(final int [] array, final boolean asc)
//$"""AI> where arguments are
//$"""AI>   int [] array is array to be sorted
//$"""AI>   asc is flag shows if true then ascending order for result, descending order otherwise
//$"""AI> it returns the same incoming array if it is null, empty or single value array, else returns new version of array with sorted values.
//$"""AI> the method should contain whole implementation of sort algorithm without any use of third side libraries, helpers and utility classes
//$"""AI> can't have additional methods and functions, all implementation must be as the single method
//$"""AI>
//$"""AI> special requirements and restrictions:
//$"""AI> 1. the method has javadoc header description
//$"""AI> 2. the method doesn't contain any internal method comment, only lines of code
//$"""AI> 3. don't use both single line comments and block comments inside the method code
//$"""AI> 4. if any import needed then use canonical class name and don't add import section
//$"""AI> 5. it is only method, must not have any class wrapping
//#-
public static int[] fastSort(final int[] array, final boolean asc) {
  throw new UnsupportedOperationException("not generated");
}
//#+
```

All sequent lines marked as `//$"""AI>` will be recognized as single prompt, they will be accumulated as text block
and provided to JCP-AI for processing. After processing, the result will fully replace the prompt text.
The result sources can be found in the maven project folder by path `target/generated-sources/preprocessed`.

## Tune JCP-AI

Requests to LLMs are not cheap, so I have provided way to cache their responses. We can provide JCP global variable
`jcpai.prompt.cache.file` with path to caching file through preprocessor config and JCP-AI starts save gotten prompts in
the defined file as JSON. During every call it will be looking for already presented response for a prompt in the cache
and inject existing cached text if it is presented.

# JCP-AI parameters

All parameters of JCP-AI can be provided as local or global variables of JCP, in the plugin it is the `var` config
section.

## Common variables

JCP-AI provides set of common parameters for all connectors:

- __jcpai.prompt.cache.file__ - path to a cache file which contains prompt results in JSON format
- __jcpai.prompt.only.processor__ - if multiple JCP-AI connectors detected as services then all they will be called for
  same prompt and their result will be accumulated, but this parameter allows to specify only connector which will
  be called in the case if needed.
- __jcpai.prompt.temperature__ - float value to define __temperature__ for LLM process
- __jcpai.prompt.timeout.ms__ - integer number of milliseconds for requests timeout, it will be provided directly
  to the calling REST client and its scope of responsibility
- __jcpai.prompt.top.p__ - TopP parameter for LLM process if client supports it
- __jcpai.prompt.top.k__ - TopK parameter for LLM process if client supports it
- __jcpai.prompt.seed__ - Seed parameter for LLM process if client supports it
- __jcpai.prompt.max.tokens__ - limit number for output tokens for LLM process if client supports it
- __jcpai.prompt.instruction.system__ - text to be sent as system instruction with prompt, if not defined then default
  one will be sent

