# i18n Plugin

<!-- Plugin description -->
An IntelliJ Platform Plugin that enhances your internationalization workflow with inline translation hints and real-time translation fetching for i18n/i18next projects.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be extracted by the [Gradle](/build.gradle.kts) during the build process.

To keep everything working, do not remove `<!-- ... -->` sections.
<!-- Plugin description end -->

IntelliJ IDEA plugin for internationalization support with inline translation hints.

## Features

- Displays inline hints showing translations for i18n keys
- Configurable translation service URL
- Real-time translation fetching

## Development

This plugin was built using the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

### Building

```bash
./gradlew buildPlugin
```

### Running

```bash
./gradlew runIde
```
