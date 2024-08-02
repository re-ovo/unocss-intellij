// Thanks to https://github.com/antfu/vscode-iconify/blob/main/scripts/prepare.ts
import {resolve, join} from "node:path";
import {fileURLToPath} from "node:url";
import fs from 'fs-extra'
import {IconifyMetaDataCollection} from "@iconify/json";
import {IconsetMeta} from "../src/collections";
import {IconifyJSON} from "@iconify/types";

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const out = resolve(__dirname, '../src/generated')

async function prepareJSON() {
    const dir = resolve(__dirname, '../node_modules/@iconify/json')
    const raw: IconifyMetaDataCollection = await fs.readJSON(join(dir, 'collections.json'))

    const collections = Object.entries(raw).map(([id, v]) => ({
        ...(v as any),
        id,
    }))

    const collectionsMeta: IconsetMeta[] = []

    for (const info of collections) {
        const setData: IconifyJSON = await fs.readJSON(join(dir, 'json', `${info.id}.json`))

        const icons = Object.keys(setData.icons)
        const { id, name, author, height, license } = info
        const meta = { author: author.name, height, name, id, icons, license: license.spdk }
        collectionsMeta.push(meta)
    }

    await fs.ensureDir(out)
    await fs.writeFile(join(out, 'collections.ts'), `export default \`${JSON.stringify(collectionsMeta)}\``, 'utf-8')
}

async function prepare() {
    await prepareJSON()
}

await prepare()
