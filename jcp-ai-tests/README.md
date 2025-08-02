Set of test cases for JCP-AI.

By default they use caches for prompt responses, the profile `clear-cache` should be activated to clear caches   
`mvn clean install -Pclear-cache`

The Gradle test cases activated through `gradle` profile, but they require Gradle executable path.
`mvn clean install -Pgradle`

Start all test cases with Gradle ones and ensure cache cleaning   
`mvn clean install -Pclear-cache,gradle`