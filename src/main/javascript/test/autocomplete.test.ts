import {createGenerator} from '@unocss/core'
import {presetWind3} from 'unocss'
import {describe, expect, it} from 'vitest'
import {createFreshAutocomplete} from '../src/autocomplete'

async function setup() {
  const generator = await createGenerator({presets: [presetWind3()]})
  return {generator, autocomplete: createFreshAutocomplete(generator)}
}

describe('createFreshAutocomplete', () => {
  it('offers a class seen after its prefix was first requested', async () => {
    const {generator, autocomplete} = await setup()

    expect(await autocomplete.suggest('rela')).not.toContain('relative')

    await generator.generate('relative', {preflights: false})

    expect(await autocomplete.suggest('rela')).toContain('relative')
  })

  it('keeps cached results while the generator has seen nothing new', async () => {
    const {autocomplete} = await setup()

    const first = await autocomplete.suggest('flex-')

    expect(await autocomplete.suggest('flex-')).toBe(first)
  })

  it('clears cached results once the generator has seen something new', async () => {
    const {generator, autocomplete} = await setup()

    const first = await autocomplete.suggest('flex-')
    await generator.generate('relative', {preflights: false})

    expect(await autocomplete.suggest('flex-')).not.toBe(first)
  })
})
