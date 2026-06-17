# jena-fuseki-spring-boot-starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zendodx/jena-fuseki-spring-boot-starter?color=blue&logo=apache-maven)](https://mvnrepository.com/artifact/io.github.zendodx/jena-fuseki-spring-boot-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-11%2B-orange?logo=openjdk)](https://www.oracle.com/java/)
[![codecov](https://codecov.io/gh/zendodx/jena-fuseki-spring-boot-starter/branch/master/graph/badge.svg)](https://codecov.io/gh/zendodx/jena-fuseki-spring-boot-starter)
[![GitHub Stars](https://img.shields.io/github/stars/zendodx/jena-fuseki-spring-boot-starter?style=social)](https://github.com/zendodx/jena-fuseki-spring-boot-starter/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/zendodx/jena-fuseki-spring-boot-starter?style=social)](https://github.com/zendodx/jena-fuseki-spring-boot-starter/forks)

##### 📖English Documentation | 📖 [中文文档](README.md)

## Overview

Enable Spring Boot projects to operate Apache Jena Fuseki triple stores (knowledge graphs / RDF graph databases) as
easily as using JdbcTemplate to access a relational database.
Users only need to configure the Fuseki server address in application.yml and @Autowired-inject JenaFusekiTemplate to
get started — no need to worry about connection management, serialization, authentication, or any other low-level
details.

## Tool Preparation

- JDK11+
- Maven3.6+

## Add Dependencies

```xml
<!-- https://mvnrepository.com/artifact/io.github.zendodx/jena-fuseki-spring-boot-starter -->
<dependency>
    <groupId>io.github.zendodx</groupId>
    <artifactId>jena-fuseki-spring-boot-starter</artifactId>
    <version>${jena-fuseki-spring-boot-starter.version}</version>
</dependency>
```

## User Documentation

- [API Document](docs/dev-guide/API文档.md)
- [Changelog](docs/changelog.md)
- [Contribute](docs/contribute.md)

## Open Source License

jena-fuseki-spring-boot-starter is an open-source project licensed under
the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).