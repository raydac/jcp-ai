Test for communication with GeminiAI client.

The file `jcp_ai_gemini_cache.json` contains cached responses for prompts, it should be removed if you need to ensure
request to LLM.

It requires `secret_properties.properties` file in the test module root, the file should contain options with parameters for the client.
A token can be created through [the AI Studio](https://aistudio.google.com/app/apikey)

```properties
jcpai.gemini.model=<MODEL_NAME>
jcpai.gemini.api.key=<SECRET_API_KEY_TOKEN>
```
