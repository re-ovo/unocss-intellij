import {createAutocomplete} from '@unocss/autocomplete'
import {createGenerator} from '@unocss/core'
import {presetWind3} from 'unocss'
import {describe, expect, it} from 'vitest'
import {orderSuggestions, shortcutsFirst} from '../src/completion'

async function setup() {
  return createGenerator({
    presets: [presetWind3()],
    shortcuts: [
      {underlined: 'underline', 'title-1': 'text-lg'},
      [/^dynamic-(.+)$/, ([, value]) => `text-${value}`],
    ],
  })
}

describe('shortcutsFirst', () => {
  it('moves shortcuts ahead of other suggestions and keeps the rest in order', async () => {
    const generator = await setup()

    expect(shortcutsFirst(generator, 'u', ['underline-auto', 'underline-red', 'underlined']))
      .toEqual(['underlined', 'underline-auto', 'underline-red'])
  })

  it('does not count a shortcut behind a variant that was not typed', async () => {
    const generator = await setup()

    expect(shortcutsFirst(generator, 'ti', ['timber:title-1', 'title-1']))
      .toEqual(['title-1', 'timber:title-1'])
  })

  it('counts a shortcut behind a variant that was typed', async () => {
    const generator = await setup()

    expect(shortcutsFirst(generator, 'hover:ti', ['hover:timber:title-1', 'hover:title-1']))
      .toEqual(['hover:title-1', 'hover:timber:title-1'])
  })

  it('counts an important shortcut', async () => {
    const generator = await setup()

    expect(shortcutsFirst(generator, '!ti', ['!timeline', '!title-1']))
      .toEqual(['!title-1', '!timeline'])
  })

  it('leaves shortcuts defined with a RegExp in place', async () => {
    const generator = await setup()

    expect(shortcutsFirst(generator, 'd', ['display-none', 'dynamic-red']))
      .toEqual(['display-none', 'dynamic-red'])
  })

  it('keeps a shortcut within maxItems when preset utilities sort ahead of it', async () => {
    const generator = await setup()
    const autocomplete = createAutocomplete(generator)
    const suggestions = await autocomplete.suggest('underl', true)

    expect(suggestions.slice(0, 25)).not.toContain('underlined')
    expect(shortcutsFirst(generator, 'underl', suggestions)[0]).toBe('underlined')
  })
})

describe('orderSuggestions', () => {
  const suggestions = ['underline-auto', 'underlined']

  it('moves shortcuts first with the smart order and prefix matching', async () => {
    const generator = await setup()

    expect(orderSuggestions(generator, 'u', suggestions, 'prefix', 'smart'))
      .toEqual(['underlined', 'underline-auto'])
  })

  it('keeps the order with the alphabetical order', async () => {
    const generator = await setup()

    expect(orderSuggestions(generator, 'u', suggestions, 'prefix', 'alphabetical')).toEqual(suggestions)
  })

  it('keeps the relevance ranking with fuzzy matching', async () => {
    const generator = await setup()

    expect(orderSuggestions(generator, 'u', suggestions, 'fuzzy', 'smart')).toEqual(suggestions)
  })
})
