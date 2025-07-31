# Kotlin Jupyter Kernel Dependency Version Conflict Reproducer

Currently, either the Kotlin Jupyter kernel does not perform any conflict resolution when there are multiple versions of a dependency or it's picking a lower version. This can lead to hard-to-diagnose runtime failures. This repository demonstrates a case in which this issue arises.

1. **Publish the Example Library to Maven Local**

```sh
./gradlew -p example-library publishLibraryPublicationToMavenLocal
```

2. **Set Up a Python Virtual Environment and Install Jupyter + Kotlin Kernel**

`0.14.1.550` is the latest version of the Kotlin Jupyter kernel published to pypi.org and Anaconda.org, as of this commit's author time.

```sh
python3 -m venv .venv
source .venv/bin/activate
pip install jupyterlab==4.4.5 kotlin-jupyter-kernel==0.14.1.550
```

3. **Run the Notebook on either commit**

- **Expected:** The notebook succeeds. Ideally, it would succeed by resolving okio to the highest version requested, 3.15.0. That's the most common dependency version conflict resolution strategy experienced by Kotlin users because [it's the default in Gradle](gradle-behavior).
- **Actual:** The notebook fails with a `NoClassDefFoundError` for `okio.Socket`, which is used internally by OkHttp at runtime. Note how this class is not directly used by the notebook or the library, making for a rather confusing failure. The notebook fails on one commit but succeeds on the other, according to the order of dependencies in the example-library's pom. The difference between commits is the order of the dependencies in that pom file (or its `build.gradle.kts` dependencies, which result in that pom).

```sh
# This fails because it uses okio 3.7.0, which does not include okio.Socket
git checkout a4517ae && ./gradlew publishToMavenLocal && jupyter nbconvert --to notebook --execute notebook.ipynb
# This succeeds because it uses okio 3.15.0, which includes okio.Socket
git checkout d2cbe19 && ./gradlew publishToMavenLocal && jupyter nbconvert --to notebook --execute notebook.ipynb
```

You could also run `script.main.kts` to see that the Kotlin scripting engine doesn't seem to resolve dependency version conflicts either. However, note that Kotlin scripting has some sort of caching mechanism I couldn't figure out how to disable. I couldn't reproduce the issue reliably while switching commits.

```sh
# Edit the @file:Repository value first to use your user home path
# Should fail
git checkout a4517ae && ./gradlew publishToMavenLocal && kotlin -script script.main.kts
# Should succeed
git checkout d2cbe19 && ./gradlew publishToMavenLocal && kotlin -script script.main.kts
```

## Dependencies comparison

### pom diff

Output of 'bad pom commit' ([a4517ae][bad]) vs 'good pom commit' ([d2cbe19][good]).

```diff
--- build/minimal-bad.pom	2025-07-31 20:39:17
+++ build/minimal-good.pom	2025-07-31 20:39:18
@@ -11,18 +11,18 @@
   <version>SNAPSHOT</version>
   <dependencies>
     <dependency>
-      <groupId>com.squareup.moshi</groupId>
-      <artifactId>moshi</artifactId>
-      <version>1.15.2</version>
-      <scope>compile</scope>
-    </dependency>
-    <dependency>
       <groupId>com.squareup.okhttp3</groupId>
       <artifactId>okhttp-jvm</artifactId>
       <version>5.1.0</version>
       <scope>compile</scope>
     </dependency>
     <dependency>
+      <groupId>com.squareup.moshi</groupId>
+      <artifactId>moshi</artifactId>
+      <version>1.15.2</version>
+      <scope>compile</scope>
+    </dependency>
+    <dependency>
       <groupId>org.jetbrains.kotlin</groupId>
       <artifactId>kotlin-stdlib</artifactId>
       <version>2.1.20</version>
```

### Dependency graphs

#### Bad pom ([a4517ae][bad])

```
------------------------------------------------------------
Root project 'example-library'
------------------------------------------------------------

runtimeClasspath - Runtime classpath of 'main'.
+--- com.squareup.okhttp3:okhttp:5.1.0
|    \--- com.squareup.okhttp3:okhttp-jvm:5.1.0
|         +--- com.squareup.okio:okio:3.15.0
|         |    \--- com.squareup.okio:okio-jvm:3.15.0
|         |         \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.21 -> 2.2.0
|         |              +--- org.jetbrains:annotations:13.0
|         |              +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0 -> 1.8.21 (c)
|         |              \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 -> 1.8.21 (c)
|         \--- org.jetbrains.kotlin:kotlin-stdlib:2.2.0 (*)
+--- com.squareup.moshi:moshi:1.15.2
|    +--- com.squareup.okio:okio:3.7.0 -> 3.15.0 (*)
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21
|         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 -> 2.2.0 (*)
|         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.21
|              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 -> 2.2.0 (*)
\--- org.jetbrains.kotlin:kotlin-stdlib:2.1.20 -> 2.2.0 (*)

(c) - A dependency constraint, not a dependency. The dependency affected by the constraint occurs elsewhere in the tree.
(*) - Indicates repeated occurrences of a transitive dependency subtree. Gradle expands transitive dependency subtrees only once per project; repeat occurrences only display the root of the subtree, followed by this annotation.
```

#### Good pom ([d2cbe19][good])

```
------------------------------------------------------------
Root project 'example-library'
------------------------------------------------------------

runtimeClasspath - Runtime classpath of 'main'.
+--- com.squareup.moshi:moshi:1.15.2
|    +--- com.squareup.okio:okio:3.7.0 -> 3.15.0
|    |    \--- com.squareup.okio:okio-jvm:3.15.0
|    |         \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.21 -> 2.2.0
|    |              +--- org.jetbrains:annotations:13.0
|    |              +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0 -> 1.8.21 (c)
|    |              \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 -> 1.8.21 (c)
|    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21
|         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 -> 2.2.0 (*)
|         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.21
|              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 -> 2.2.0 (*)
+--- com.squareup.okhttp3:okhttp:5.1.0
|    \--- com.squareup.okhttp3:okhttp-jvm:5.1.0
|         +--- com.squareup.okio:okio:3.15.0 (*)
|         \--- org.jetbrains.kotlin:kotlin-stdlib:2.2.0 (*)
\--- org.jetbrains.kotlin:kotlin-stdlib:2.1.20 -> 2.2.0 (*)

(c) - A dependency constraint, not a dependency. The dependency affected by the constraint occurs elsewhere in the tree.
(*) - Indicates repeated occurrences of a transitive dependency subtree. Gradle expands transitive dependency subtrees only once per project; repeat occurrences only display the root of the subtree, followed by this annotation.
```

[good]: https://github.com/gabrielfeo/kotlin-jupyter-version-conflict-repro/commit/d2cbe19c06038bdc00462bc27041d54c14991fcb
[bad]: https://github.com/gabrielfeo/kotlin-jupyter-version-conflict-repro/commit/a4517ae3891e3979c8ca32b9bc88be1cbf26037e
[gradle-behavior]: https://docs.gradle.org/current/userguide/dependency_constraints_conflicts.html#sub:resolving-version-conflicts
