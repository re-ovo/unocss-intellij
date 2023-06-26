# 开发说明
导入到Intellij IDEA即可

## 预构建
### Javascript
本插件通过调用Javascript来实现调用Unocss的API，见 `src/main/javascript/` 目录。
当修改了Javascript代码后需要重新打包，请在该目录下执行 `npm run build` 来重新打包。

打包完毕后，会在项目根目录下生成 `unocss` 文件夹
每次修改完Javascript代码后，需要重新打包，然后重新启动IDEA才能生效。

### 词法解析与语法解析规则
> 请确保你安装了IntelliJ IDEA的Grammar-Kit插件
1. 找到 `src/main/kotlin/me.rerere.unocssintellij/lang/uno.bnf` 文件，右键点击 `Generate Parser Code` 生成语法解析器。
2. 找到 `src/main/kotlin/me.rerere.unocssintellij/lang/uno.flex` 文件，右键点击 `Run JFlex Generator` 生成词法解析器。

