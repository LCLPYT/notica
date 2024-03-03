# fabric-mod-boilerplate
A boilerplate to setup a fabric mod quickly.

## Needed configuration
- `gradle.properties`:
Change `maven-group` and `archives_base_name`; configure Fabric versions if needed
- modify package names of `src/main/java/com/example` and `src/client/java/com/example` as you like
- `src/main/resources/fabric.mod.json`: change `modid`, `name`, `description`, `authors`, `contact/*`, `icon`, `entrypoints/{main,client,fabric-datagen}`, `mixins` (adjust `testmod.mixins.json` and `testmod.client.mixins.json`)
- rename `src/main/resources/testmod.mixins.json` and `src/client/resources/testmod.client.mixins.json`
- rename classes `src/main/java/com/example/ExampleMod` and `src/client/java/com/example/ExampleModClient`
- rename folder `src/main/resources/assets/testmod`, insert your modid
- `build.gradle`: Adjust `publishing.publications.mavenJava.pom.{name,description}`, `loom.runs.datagen.vmArg[Dfabric-api.datagen.modid]`
- `src/{main,client}/resources/testmod.mixins.json`: Adjust the `package` property
- `settings.gradle` change rootProject.name value

## Versioning
This boilerplate uses the [`gradle-build-utils`](https://github.com/LCLPYT/GradleBuildUtils) Gradle plugin, which will determine the current version from the latest Git tag.
To update the version, commit your changes and tag it with a semantic version comform tag.

Example:
```
git tag 1.0.0
```

### Common pitfall: "Could not determine version"
If your gradle builds fails, and you get this error message:
```
Caused by: java.lang.IllegalStateException: Could not determine version
```
The issue is, that there are no tags in your Git repository yet.
You may just tag the current commit with `git tag 0.1.0`, indicating it is a pre-release.

## Publishing
If you want to publish your mod from CLI, you can create a `publish.properties` file in the project root.
The contents should look like this:
```properties
mavenHost=https://your.maven.repo.here
mavenUser=user
mavenPassword=password
```
When the file is not present, or doesn't contain these entries, the filesystem will be used as fallback (in the `repo/` directory).

### GitHub Actions
If you are using GitHub Actions to publish your mod, you can define Actions secrets to authenticate.
Just define `DEPLOY_URL`, `DEPLOY_USER` and `DEPLOY_PASSWORD` as Action secrets on your repository.
Don't forget to pass them as environment variables in your action definition.

The `gradle-build-utils` Gradle plugin will not always succeed at getting your Git tag version.
As reliable workaround, it is recommended to set a `CI_VERSION` environment variable that can be determined quite easily by GitHub Actions.

As an example GitHub Action definition, you can use [the publish action from MMOContent](https://github.com/LCLPYT/MMOContent/blob/c89ca987f2f451b524313c06401e8e4a2b5d6de5/.github/workflows/gradle-publish.yml).
