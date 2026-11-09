# tie-jexl

[![CI](https://github.com/tie-integration-engine/tie-jexl/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tie-integration-engine/tie-jexl/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.tie-integration-engine/tie-jexl)](https://central.sonatype.com/artifact/io.github.tie-integration-engine/tie-jexl)

`tie-jexl` is a module of [TIE](https://github.com/tie-integration-engine/tie), a stateless Java library for file
integrations: field expressions with [Apache Commons JEXL](https://commons.apache.org/proper/commons-jexl/), in a
restricted sandbox (no class access, no loops, no side effects).

```xml
<dependency>
    <groupId>io.github.tie-integration-engine</groupId>
    <artifactId>tie-jexl</artifactId>
    <version>0.5.0</version>
</dependency>
```

```java
Tie tie = Tie.builder().expressionEngine(new JexlExpressionEngine()).build();
```

This repository is a git submodule of [tie-integration-engine/tie](https://github.com/tie-integration-engine/tie),
which holds the parent pom, the examples and the documentation. The module builds from there:

```shell
git clone --recurse-submodules https://github.com/tie-integration-engine/tie.git
cd tie
./mvnw -pl tie-jexl -am verify
```

See the [wiki](https://github.com/tie-integration-engine/tie/wiki) for the documentation. Licensed under the MIT License.
