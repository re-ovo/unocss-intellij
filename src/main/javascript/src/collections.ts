import raw from './generated/collections'

export interface IconsetMeta {
  id: string
  name?: string
  author?: string
  icons: string[]
  height?: number | number[]
  license?: string
}

const collections: IconsetMeta[] = JSON.parse(raw)

const collectionIds = collections.map(i => i.id)

const SUGGESTION_PATTERN = /i-(.+)[:-](.+)/

export function suggestIcon(name: string): string[] {
  const matchResult = SUGGESTION_PATTERN.exec(name)
  if (matchResult) {
    const [, collection, icon] = matchResult
    if(collectionIds.indexOf(collection) >= 0) {
      const icons= collections
          .find(i => i.id === collection)
          ?.icons.filter(i => i.includes(icon)) || []
      return icons.map(i => `i-${collection}:${i}`)
    }
  }
  return []
}
