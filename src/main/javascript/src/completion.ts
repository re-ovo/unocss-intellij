import type {UnoGenerator} from "@unocss/core";

/**
 * Moves the project's shortcuts to the front of the suggestions and keeps the order otherwise.
 *
 * A suggestion only counts as a shortcut if it adds no variant beyond what was typed, so `ti`
 * offers `title-1` before `hover:title-1`, and `hover:ti` offers `hover:title-1` first.
 * Shortcuts defined with a RegExp have no fixed name, so they stay where they are.
 */
export function shortcutsFirst(generator: UnoGenerator<any>, input: string, suggestions: string[]) {
  const names = new Set(
    generator.config.shortcuts
      .map(shortcut => shortcut[0])
      .filter(name => typeof name === 'string')
  )
  const typedVariants = input.slice(0, input.lastIndexOf(':') + 1)
  const isShortcut = (suggestion: string) => suggestion.startsWith(typedVariants)
    && names.has(suggestion.slice(typedVariants.length).replace(/^!/, ''))

  return [
    ...suggestions.filter(isShortcut),
    ...suggestions.filter(suggestion => !isShortcut(suggestion)),
  ]
}
