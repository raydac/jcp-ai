Test for communication with Anthropics client.

The file `jcp_ai_anthropics_cache.json` contains cached responses for prompts, it should be removed if you need to
ensure request to LLM.

It requires `secret_properties.properties` file in the test module root, the file should contain options with parameters for the client.
A token can be created through [the Anthropic API keys page](https://console.anthropic.com/settings/keys)

```properties
jcpai.anthropic.model=<MODEL_NAME>
jcpai.anthropic.api.key=<SECRET_API_KEY_TOKEN>
```
