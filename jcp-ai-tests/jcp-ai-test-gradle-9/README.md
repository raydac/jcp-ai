It is a test gradle project wrapped by maven project. It allows start test as just `mvn clean build` with Maven. If you
want start it directly in Gradle then you can use command line:   
`gradle clean test jar --info --rerun-tasks --scan -Pjcpai_test_version=1.0.0 -Pjcp_test_version=7.2.1 -Pgemini_ai_client=1.10.0`

It requires `secret_properties.properties` file in the test module root, the file should contain options with parameters
for the client.
A token can be created through [the AI Studio](https://aistudio.google.com/app/apikey)

```properties
jcpai.gemini.model=<MODEL_NAME>
jcpai.gemini.api.key=<SECRET_API_KEY_TOKEN>
```
