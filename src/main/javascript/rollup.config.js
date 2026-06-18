import typescript from "@rollup/plugin-typescript";
import babel from "@rollup/plugin-babel";
import nodeResolve from "@rollup/plugin-node-resolve";
import commonjs from "@rollup/plugin-commonjs";
import json from "@rollup/plugin-json";
import fs from "node:fs";
import path from "node:path";

// jiti 在运行时通过 createRequire(import.meta.url)("../dist/babel.cjs") 动态加载
// babel transformer。这种 createRequire 调用 rollup/commonjs 插件无法静态分析，
// 不会被打包，导致产物运行时报 "Cannot find module '../dist/babel.cjs'"。
// 这里在打包结束后，按产物中实际出现的 jiti 目录，从源 node_modules 同路径
// 拷贝匹配版本的 babel.cjs 到产物中。
function copyJitiBabel() {
    return {
        name: 'copy-jiti-babel',
        writeBundle(options) {
            const outDir = path.resolve(options.dir);
            const walk = (dir) => {
                for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
                    const full = path.join(dir, entry.name);
                    if (entry.isDirectory()) {
                        walk(full);
                    } else if (entry.name === 'jiti.mjs' && path.basename(dir) === 'dist') {
                        const rel = path.relative(outDir, path.join(dir, 'babel.cjs'));
                        const src = path.resolve(rel); // 源 node_modules 同路径
                        const dest = path.join(dir, 'babel.cjs');
                        if (fs.existsSync(src) && !fs.existsSync(dest)) {
                            fs.copyFileSync(src, dest);
                            console.log(`[copy-jiti-babel] ${rel}`);
                        }
                    }
                }
            };
            walk(outDir);
        },
    };
}

export default {
    input: 'src/service.ts',
    output: {
        dir: '../../../unojs',
        format: 'es',
        preserveModules: true,
        entryFileNames: '[name].mjs',
    },
    plugins: [
        json(),
        typescript(),
        nodeResolve(),
        commonjs(),
        babel({
            babelHelpers: 'bundled'
        }),
        copyJitiBabel(),
    ],
}