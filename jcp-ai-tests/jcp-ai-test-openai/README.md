Test for communication with OpenAI client.

The file `jcp_ai_openai_cache.json` contains cached responses for prompts, it should be removed if you need to ensure
request to LLM.

It requires `secret_properties.properties` file in the test module root, the file should contain options with parameters for the client.
A token can be created
through [the OpenAI platform API keys page](https://platform.openai.com/settings/organization/api-keys)

```properties
jcpai.openai.model=<MODEL_NAME>
jcpai.openai.org.id=<ORGANIZATION_ID>
jcpai.openai.api.key=<SECRET_API_KEY_TOKEN>
```
