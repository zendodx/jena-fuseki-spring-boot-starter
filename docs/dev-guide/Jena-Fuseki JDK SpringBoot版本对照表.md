---
layout: default
title: Jena-Fuseki,JDK,SpringBoot版本对照表
parent: 开发指南
nav_order: 1
---

# 官方版本发布历史

https://archive.apache.org/dist/jena/binaries/

# 版本对照表

根据 Apache Jena 官方 Release Notes 和已知版本信息，整理如下：

## Jena Fuseki 版本与 JDK 版本对应表

### 完整对应关系

| Jena Fuseki 版本          | 最低 JDK 要求   | 推荐 JDK       | 发布时间        | 备注           |
|-------------------------|-------------|--------------|-------------|--------------|
| **5.x（5.0.0 ~ 5.2.x）**  | **Java 17** | Java 17 / 21 | 2024 ~ 至今   | 当前最新主线，模块化重构 |
| **4.x（4.0.0 ~ 4.10.x）** | **Java 11** | Java 11 / 17 | 2021 ~ 2024 | 长期稳定版，主流生产使用 |
| **3.x（3.0.0 ~ 3.17.x）** | **Java 8**  | Java 8 / 11  | 2016 ~ 2021 | 已停止维护        |
| **2.x**                 | Java 7 / 8  | Java 8       | 2013 ~ 2016 | 已废弃          |

---

### 关键版本节点

```
Java 8  ──→  Jena 3.x（最高支持到 3.17.0，2021年停更）
Java 11 ──→  Jena 4.x（从 4.0.0 开始强制要求，2021年发布）
Java 17 ──→  Jena 5.x（从 5.0.0 开始强制要求，2024年发布）
```

---

### 与 Spring Boot 的搭配建议

| Spring Boot 版本            | 推荐 JDK   | 推荐 Jena 版本                    |
|---------------------------|----------|-------------------------------|
| Spring Boot **3.x**       | Java 17+ | **Jena 5.x**（推荐）或 Jena 4.10.x |
| Spring Boot **2.7.x**     | Java 11+ | **Jena 4.x**                  |
| Spring Boot **2.5.x 及以下** | Java 8+  | Jena 3.17.x（不推荐，已停更）          |

---

### 本 Starter 的版本策略建议

```
主分支（main）   →  Jena 5.x  +  JDK 17  +  Spring Boot 3.x
兼容分支（1.0.x）→  Jena 4.x  +  JDK 11  +  Spring Boot 2.7.x
```

目前你的仓库分支是 `1.0.x`，对应 **Jena 4.x + JDK 11 + Spring Boot 2.7.x** 是最稳妥的选择。