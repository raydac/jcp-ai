![JAIP Project logo](assets/git_banner.png)   
[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.igormaznitsa/jaip/badge.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|jaip|1.0.0|jar)
[![Java 11+](https://img.shields.io/badge/java-11%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven 3.8+](https://img.shields.io/badge/maven-3.8%2b-green.svg)](https://maven.apache.org/)

# Pre-word

A long time ago, I
created [one of the first Java preprocessors (called JCP)](https://github.com/raydac/java-comment-preprocessor) to make
building projects easier. The preprocessor's business is to read and change the program text. LLMs also work by
generating text based on given input, so combining them with a preprocessor is a logical step.

The JCP preprocessor allows you to keep blocks of text in comments, and starting from version 7.2.0, it can send them to
external services for processing. This gave me the idea to connect it with an LLM, so the result from the LLM could be
inserted directly into the program code (with minor normalizations).

Since the preprocessor can work with Maven, Gradle, and Ant, the ability to use LLMs automatically becomes available
for these build tools as well.

[![Arthur's acres sanctuary donation](assets/arthur_sanctuary_banner.png)](https://www.arthursacresanimalsanctuary.org/donate)

# How it works?

JAIP is a set of libraries that provide specialized services capable of calling external LLMs to process text. I’ve
added support for LLMs that have official open-source Java clients.   
Currently, it provides connectors for:
- [__Gemini AI__ (Google)](https://gemini.google.com/app)
- [__Claude AI__ (Anthropic)](https://claude.ai)
- [__ChatGPT__ (OpenAI)](https://chatgpt.com/)

![Sequence diagram](assets/sequence1.png)

The preprocessor discovers JAIP processors through Java's service registration mechanism, so it's enough for them to
appear in its classpath for them to become automatically available. For better flexibility and compatibility, JAIP
client
libraries don’t include any client code themselves; instead, they rely on a client library already present in the
classpath.
