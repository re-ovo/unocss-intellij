import {UnoGenerator} from "@unocss/core";
import MagicString from "magic-string";

// remove @unocss/transformer-directives transformer to get matched result from source code
const ignoreTransformers = [
    '@unocss/transformer-directives',
    '@unocss/transformer-compile-class',
]

/**
 * Ported from VSCode extension and removed pug support, maybe support it in the future.
 *
 * @param generator
 * @param code
 * @param id
 * @see https://github.com/unocss/unocss/blob/main/packages/shared-common/src/index.ts#L188
 */
export async function getMatchedPositionsFromCode(generator: UnoGenerator, code: string, id = '') {
    const s = new MagicString(code)
    const tokens = new Set()
    const ctx = {uno: generator, tokens} as any

    const transformers = generator.config.transformers
      ?.filter(i => !ignoreTransformers.includes(i.name))
    const annotations = []
    for (const enforce of ['pre', 'default', 'post']) {
        for (const i of transformers?.filter(i => (i.enforce ?? 'default') === enforce) || []) {
            const result = await i.transform(s, id, ctx)
            const _annotations = result?.highlightAnnotations
            if (_annotations) {
                annotations.push(..._annotations)
            }
        }
    }

    const genResult = await generator.generate(s.toString(), {preflights: false})
    return {matched: [...genResult.matched], extraAnnotations: annotations}
}