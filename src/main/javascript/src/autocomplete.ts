import {type AutocompleteOptions, createAutocomplete} from "@unocss/autocomplete";
import type {UnoGenerator} from "@unocss/core";

/**
 * Creates an autocomplete that stays in step with the generator.
 *
 * Autocomplete suggests classes the generator has already seen, but it also caches results per
 * input. Without clearing that cache, a class seen after a prefix was first requested would never
 * be offered for that prefix.
 */
export function createFreshAutocomplete(generator: UnoGenerator<any>, options?: AutocompleteOptions) {
  const autocomplete = createAutocomplete(generator, options)
  const suggest = autocomplete.suggest
  let knownClasses = generator.getCachedTokens('').length

  autocomplete.suggest = async (input, allowsEmptyInput) => {
    const classes = generator.getCachedTokens('').length
    if (classes !== knownClasses) {
      knownClasses = classes
      autocomplete.cache.clear()
    }
    return suggest(input, allowsEmptyInput)
  }

  return autocomplete
}
