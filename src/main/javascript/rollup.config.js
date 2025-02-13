import typescript from "@rollup/plugin-typescript";
import babel from "@rollup/plugin-babel";
import nodeResolve from "@rollup/plugin-node-resolve";
import commonjs from "@rollup/plugin-commonjs";

export default {
    input: 'src/service.ts',
    output: {
        dir: '../../../unojs',
        format: 'es',
        preserveModules: true,
        entryFileNames: '[name].mjs',
    },
    plugins: [
        typescript(),
        nodeResolve(),
        commonjs(),
        babel({
            babelHelpers: 'bundled'
        }),
    ],
}