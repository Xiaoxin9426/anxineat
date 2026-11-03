#!/usr/bin/env bash
# Git Bash 下调用 Maven 的包装脚本。
# 原因：系统环境变量 MAVEN_HOME 是 Windows 反斜杠路径（D:\develop\...），
# Git Bash 的 mvn shell 脚本无法正确拼接 classpath，会报
# "找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher"。
# 直接调用 mvn.cmd（批处理）可正常工作。
MVN_CMD="/d/develop/Maven/apache-maven-3.9.4-bin/apache-maven-3.9.4/bin/mvn.cmd"
exec "$MVN_CMD" "$@"
