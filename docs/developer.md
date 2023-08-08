# 开发说明
导入到Intellij IDEA即可, 请确保使用JDK 17

## 预构建
### Javascript
本插件通过调用Javascript来实现调用Unocss的API，见 `src/main/javascript/` 目录。
首次运行之前请先执行 `npm install` 安装依赖。

> 你不需要手动执行 `npm run build`，Gradle构建时会自动执行。

## 运行
执行 `runIde` 任务即可运行插件
