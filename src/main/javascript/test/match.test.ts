import {createGenerator, type SourceCodeTransformer} from '@unocss/core'
import {presetWind3, transformerVariantGroup} from 'unocss'
import {describe, expect, it} from 'vitest'
import {applyTransformers, getMatchedPositionsFromCode} from '../src/match'

const code = '<template><div class="md:(flex grid) p-4"></div></template>'

async function annotationsWith(transformer: SourceCodeTransformer, source = code, id = 'x.ts') {
  const generator = await createGenerator({
    presets: [presetWind3()],
    // transformerVariantGroup is itself `enforce: 'pre'`, so the transformer under test is
    // listed first to make sure it runs before variant group within the same phase
    transformers: [transformer, transformerVariantGroup()],
  })
  return applyTransformers(generator, source, id)
}

const classNames = (annotations: { className: string }[]) => annotations.map(it => it.className)

describe('applyTransformers', () => {
  it('survives a transformer that overwrites the whole source', async () => {
    const annotations = await annotationsWith({
      name: 'rewrite-whole-file',
      enforce: 'pre',
      transform(s) {
        s.overwrite(0, s.length(), s.toString())
      },
    })

    expect(classNames(annotations)).toEqual(['md:flex', 'md:grid'])
  })

  it('skips transformers excluded by idFilter', async () => {
    let ran = false
    await annotationsWith({
      name: 'vue-only',
      idFilter: id => id.endsWith('.vue'),
      transform() {
        ran = true
      },
    })

    expect(ran).toBe(false)
  })

  it('runs transformers on a snippet with no id, regardless of idFilter', async () => {
    let ran = false
    await annotationsWith({
      name: 'vue-only',
      idFilter: id => id.endsWith('.vue'),
      transform() {
        ran = true
      },
    }, 'md:(flex grid)', '')

    expect(ran).toBe(true)
  })

  it('keeps other annotations when an idFilter throws', async () => {
    const annotations = await annotationsWith({
      name: 'bad-filter',
      idFilter() {
        throw new Error('boom')
      },
      transform() {
      },
    })

    expect(classNames(annotations)).toEqual(['md:flex', 'md:grid'])
  })

  it('keeps other annotations when a transformer throws', async () => {
    const annotations = await annotationsWith({
      name: 'throws',
      enforce: 'pre',
      transform() {
        throw new Error('boom')
      },
    })

    expect(classNames(annotations)).toEqual(['md:flex', 'md:grid'])
  })

  it('reports offsets in the original source when an earlier transformer shortens it', async () => {
    const source = `<!-- remove me -->${code}`
    const annotations = await annotationsWith({
      name: 'strip-comment',
      enforce: 'pre',
      transform(s) {
        s.remove(0, '<!-- remove me -->'.length)
      },
    }, source)

    expect(annotations.map(it => source.slice(it.offset, it.offset + it.length))).toEqual(['flex', 'grid'])
  })
})

describe('getMatchedPositionsFromCode', () => {
  it('returns matches and annotations when a transformer edits the source', async () => {
    const generator = await createGenerator({
      presets: [presetWind3()],
      transformers: [
        transformerVariantGroup(),
        {
          name: 'rewrite-whole-file',
          enforce: 'pre',
          transform(s) {
            s.overwrite(0, s.length(), s.toString())
          },
        },
      ],
    })

    const result = await getMatchedPositionsFromCode(generator, code, 'x.ts')

    expect(classNames(result.extraAnnotations)).toEqual(['md:flex', 'md:grid'])
    expect(result.matched).toContain('p-4')
  })
})
