#!/usr/bin/env bash
set -euo pipefail

workspace=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
fixture="$workspace/verification/pure-java"
verification_root="$workspace/target/pure-java-verification"
repository="$verification_root/repository"
settings="$verification_root/settings.xml"

# 仅使用工作区的验证仓库；保留已下载的依赖，不触碰或清理用户 ~/.m2。
mkdir -p "$repository"
cat > "$settings" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd"/>
XML

# 不加载用户/global settings 或 mavenrc，也不继承注入的 JVM/classpath/发布参数。
export MAVEN_SKIP_RC=true
unset MAVEN_ARGS MAVEN_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS CLASSPATH
maven=(mvn --batch-mode --no-transfer-progress
  --settings "$settings" --global-settings "$settings"
  "-Dmaven.repo.local=$repository" -P '!release')
java_command=java
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_command="$JAVA_HOME/bin/java"
fi

printf '构建并仅安装父 POM 与核心 SDK 到隔离仓库：%s\n' "$repository"
# 固定 install 插件版本；package 后显式执行 install 目标，不运行已有测试或 release。
# 不构建 Starter，不执行 deploy，也不安装消费者 fixture。
"${maven[@]}" -f "$workspace/pom.xml" -pl epay-sdk -am \
  -Dmaven.test.skip=true package org.apache.maven.plugins:maven-install-plugin:3.1.4:install

sdk_jar="$repository/io/github/nb-sb/epay-sdk/1.0.0-SNAPSHOT/epay-sdk-1.0.0-SNAPSHOT.jar"
if ! cmp -s "$workspace/epay-sdk/target/epay-sdk-1.0.0-SNAPSHOT.jar" "$sdk_jar"; then
  printf '隔离仓库中的 SDK jar 与本次构建产物不一致。\n' >&2
  exit 1
fi

# 仅清理 fixture 自身的 target，避免旧 classpath/类文件混入；隔离仓库下载缓存保留。
"${maven[@]}" -f "$fixture/pom.xml" clean package
consumer_jar="$fixture/target/pure-java-consumer.jar"
classpath_file="$fixture/target/runtime-classpath.txt"
if [[ ! -s "$consumer_jar" || ! -s "$classpath_file" ]]; then
  printf '消费者 jar 或 runtime classpath 未生成。\n' >&2
  exit 1
fi
runtime_classpath=$(< "$classpath_file")

printf '启动独立 JVM；不使用 Maven exec、测试 classpath 或 SDK classes 目录。\n'
# exec 保留验收 JVM 的退出码；此 classpath 仅含消费者 jar 和 Maven 解析的 runtime jar。
exec "$java_command" --add-modules jdk.httpserver \
  -cp "$consumer_jar:$runtime_classpath" \
  com.nbsb.epaysdk.verification.PureJavaConsumer "$sdk_jar" "$consumer_jar" "$repository" "$workspace/README.md"
