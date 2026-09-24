#!/usr/bin/env python3
"""本地与 CI 共用的只读附件验收；不签名、不安装、不上传。"""

import json
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = ET.parse(ROOT / "pom.xml").getroot().findtext("m:version", namespaces=ns)
    require(version, "父 POM 缺少版本")
    license_bytes = (ROOT / "LICENSE").read_bytes()
    for module in ("epay-sdk", "epay-sdk-spring-boot-starter"):
        api = ("com/nbsb/epaysdk/api/EPayClient" if module == "epay-sdk"
               else "com/nbsb/epaysdk/spring/EPayAutoConfiguration")
        for classifier in ("", "-sources", "-javadoc"):
            artifact = ROOT / module / "target" / f"{module}-{version}{classifier}.jar"
            require(artifact.is_file(), f"缺少附件：{artifact}")
            with ZipFile(artifact) as jar:
                names = set(jar.namelist())
                require(jar.read("META-INF/LICENSE") == license_bytes, f"许可证不匹配：{artifact}")
                if classifier == "-sources":
                    require(api + ".java" in names, f"缺少源码：{artifact}")
                elif classifier == "-javadoc":
                    require("index.html" in names and api + ".html" in names,
                            f"缺少 Javadoc：{artifact}")
                else:
                    require(api + ".class" in names, f"缺少公开 API：{artifact}")
                    for name in names:
                        if name.endswith(".class"):
                            header = jar.read(name)[:8]
                            require(header[:4] == b"\xca\xfe\xba\xbe"
                                    and int.from_bytes(header[6:8], "big") == 61,
                                    f"制品未使用 Java 17 release：{name}")
                            if name.startswith("com/nbsb/epaysdk/"):
                                package = name.rsplit("/", 1)[0]
                                require(package == package.lower(), f"包名必须小写：{name}")
                    if module == "epay-sdk":
                        require(not any(name.startswith(("org/springframework/", "lombok/",
                                                         "com/nbsb/epaysdk/spring/"))
                                        for name in names), "核心制品混入 Spring 或 Lombok")
                    else:
                        imports = jar.read("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                        require("com.nbsb.epaysdk.spring.EPayAutoConfiguration"
                                in imports.decode("utf-8").splitlines(), "缺少 Boot 3 自动配置注册")
                        metadata = json.loads(jar.read("META-INF/spring-configuration-metadata.json"))
                        properties = {p["name"] for p in metadata.get("properties", [])}
                        required = {"epay.base-url", "epay.merchant-id", "epay.protocol", "epay.enabled",
                                    "epay.disabled-capabilities", "epay.credentials.md5-key",
                                    "epay.credentials.merchant-private-key", "epay.credentials.platform-public-key",
                                    "epay.http.connect-timeout", "epay.http.connection-request-timeout",
                                    "epay.http.response-timeout", "epay.http.request-timeout"}
                        require(required <= properties, f"属性元数据缺失：{sorted(required - properties)}")
            print(f"附件检查通过：{artifact.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
