package com.igormaznitsa.jcpai.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MarkdownCodeExtractorTest {

  @Test
  @DisplayName("Should extract simple fenced code block without language")
  void shouldExtractSimpleFencedCodeBlock() {
    String markdown = """
        ```
        System.out.println("Hello World");
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(1, result.size());
    assertEquals("System.out.println(\"Hello World\");", result.get(0));
  }

  @Test
  @DisplayName("Should extract fenced code block with Java language specifier")
  void shouldExtractFencedCodeBlockWithJavaLanguage() {
    String markdown = """
        ```java
        public class Test {
            public static void main(String[] args) {
                System.out.println("Test");
            }
        }
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(1, result.size());
    String code = result.get(0);
    assertTrue(code.contains("public class Test"));
    assertTrue(code.contains("System.out.println(\"Test\");"));
    assertTrue(code.contains("public static void main"));
  }

  @Test
  @DisplayName("Should extract fenced code block with various language specifiers")
  void shouldExtractFencedCodeBlockWithVariousLanguages() {
    String markdown = """
        Python:
        ```python
        print("Hello Python")
        ```
        
        JavaScript:
        ```javascript
        console.log("Hello JS");
        ```
        
        C++:
        ```cpp
        std::cout << "Hello C++";
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(3, result.size());
    assertEquals("print(\"Hello Python\")", result.get(0));
    assertEquals("console.log(\"Hello JS\");", result.get(1));
    assertEquals("std::cout << \"Hello C++\";", result.get(2));
  }

  @Test
  @DisplayName("Should extract multiple fenced code blocks from same document")
  void shouldExtractMultipleFencedCodeBlocks() {
    String markdown = """
        First block:
        ```java
        int x = 1;
        int y = 2;
        ```
        
        Some text here.
        
        Second block:
        ```
        String name = "John";
        System.out.println(name);
        ```
        
        Third block:
        ```python
        def hello():
            print("world")
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(3, result.size());
    assertTrue(result.get(0).contains("int x = 1;"));
    assertTrue(result.get(0).contains("int y = 2;"));
    assertTrue(result.get(1).contains("String name = \"John\";"));
    assertTrue(result.get(2).contains("def hello():"));
  }

  @Test
  @DisplayName("Should handle empty fenced code block")
  void shouldHandleEmptyFencedCodeBlock() {
    String markdown = """
        Empty block:
        ```
        ```
        
        Another empty block with language:
        ```java
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(2, result.size());
    assertEquals("", result.get(0));
    assertEquals("", result.get(1));
  }

  @Test
  @DisplayName("Should handle fenced code block with only whitespace")
  void shouldHandleFencedCodeBlockWithWhitespace() {
    String markdown = """
        ```
        
        
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(1, result.size());
    assertEquals("", result.get(0)); // Should be trimmed to empty string
  }

  @Test
  @DisplayName("Should preserve internal formatting and indentation")
  void shouldPreserveInternalFormattingAndIndentation() {
    String markdown = """
        ```java
        public class Example {
            public void method() {
                if (condition) {
                    System.out.println("nested");
                }
            }
        }
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(1, result.size());
    String code = result.get(0);
    assertTrue(code.contains("    public void method() {"));
    assertTrue(code.contains("        if (condition) {"));
    assertTrue(code.contains("            System.out.println(\"nested\");"));
  }

  @Test
  @DisplayName("Should handle fenced code with special characters and symbols")
  void shouldHandleFencedCodeWithSpecialCharacters() {
    String markdown = """
        ```javascript
        const regex = /```/g;
        const str = "Hello `world` with backticks";
        const obj = { key: "value", num: 42 };
        // Comment with special chars: @#$%^&*()
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(1, result.size());
    String code = result.get(0);
    assertTrue(code.contains("const str = \"Hello `world` with backticks\";"), code);
    assertTrue(code.contains("// Comment with special chars: @#$%^&*()"), code);
    assertTrue(code.contains("const regex = /```/g;"), code);
  }

  @Test
  @DisplayName("Should handle fenced code with newlines and blank lines")
  void shouldHandleFencedCodeWithNewlinesAndBlankLines() {
    String markdown = """
        ```java
        public void method1() {
            // First method
        }
        
        public void method2() {
            // Second method
        
            // With blank line above
        }
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(1, result.size());
    String code = result.get(0);
    assertTrue(code.contains("public void method1()"));
    assertTrue(code.contains("public void method2()"));
    assertTrue(code.contains("// With blank line above"));
    // Should preserve the blank lines within the code block
    assertTrue(code.contains("\n\npublic void method2()"));
  }

  @Test
  @DisplayName("Should return empty list when no fenced code blocks present")
  void shouldReturnEmptyListWhenNoFencedCodeBlocks() {
    String markdown = """
        # Regular Markdown Document
        
        This is just regular text with some `inline code` but no fenced blocks.
        
        Here's a list:
        - Item 1
        - Item 2
        
        And some **bold** text.
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Should handle empty input string")
  void shouldHandleEmptyInput() {
    String markdown = "";

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Should not match incomplete fenced blocks")
  void shouldNotMatchIncompleteFencedBlocks() {
    String markdown = """
        This has an opening fence but no closing:
        ```java
        public void method() {
            System.out.println("incomplete");
        }
        
        This is just regular text now.
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Should handle fenced blocks mixed with other markdown elements")
  void shouldHandleFencedBlocksMixedWithMarkdown() {
    String markdown = """
        # Code Examples
        
        Here's a Java example:
        
        ```java
        public static void main(String[] args) {
            System.out.println("Hello World");
        }
        ```
        
        And here's some **bold text** and a [link](http://example.com).
        
        Another code block:
        ```python
        for i in range(10):
            print(i)
        ```
        
        > This is a blockquote
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(2, result.size());
    assertTrue(result.get(0).contains("public static void main"));
    assertTrue(result.get(1).contains("for i in range(10):"));
  }

  @Test
  @DisplayName("Should handle complex language identifiers")
  void shouldHandleComplexLanguageIdentifiers() {
    String markdown = """
        ```c++
        std::vector<int> vec;
        ```
        
        ```objective-c
        NSString *str = @"Hello";
        ```
        
        ```shell-session
        $ ls -la
        ```
        
        ```text
        Plain text block
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(4, result.size());
    assertEquals("std::vector<int> vec;", result.get(0));
    assertEquals("NSString *str = @\"Hello\";", result.get(1));
    assertEquals("$ ls -la", result.get(2));
    assertEquals("Plain text block", result.get(3));
  }

  @Test
  @DisplayName("Should handle consecutive fenced blocks")
  void shouldHandleConsecutiveFencedBlocks() {
    String markdown = """
        ```java
        int a = 1;
        ```
        ```python
        b = 2
        ```
        ```javascript
        let c = 3;
        ```
        """;

    List<String> result = MarkdownCodeExtractor.extractFencedCodeBlocks(markdown);

    assertEquals(3, result.size());
    assertEquals("int a = 1;", result.get(0));
    assertEquals("b = 2", result.get(1));
    assertEquals("let c = 3;", result.get(2));
  }
}