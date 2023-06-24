import {sourcePluginFactory, sourceObjectFields} from "unconfig/presets";
import {loadConfig} from "@unocss/config";
import {createGenerator} from "@unocss/core";
import {createAutocomplete, searchUsageBoundary} from "@unocss/autocomplete";
import presetUno from "@unocss/preset-uno";

const defaultConfig = {
    presets: [presetUno()],
    separators: []
};

let generator = createGenerator({}, defaultConfig);
let autocomplete = createAutocomplete(generator);

export function resolveConfig(roorDir) {
    return loadConfig(process.cwd(), roorDir, [
        sourcePluginFactory({
            files: ["vite.config", "svelte.config", "iles.config"],
            targetModule: "unocss/vite",
            parameters: [{command: "serve", mode: "development"}],
        }),
        sourcePluginFactory({
            files: ["astro.config"],
            targetModule: "unocss/astro",
        }),
        sourceObjectFields({
            files: "nuxt.config",
            fields: "unocss",
        }),
    ]).then((result) => {
        generator.setConfig(result.config, defaultConfig);
        autocomplete = createAutocomplete(generator);
        return generator.config;
    });
}

export function getComplete(content, cursor) {
    return autocomplete.suggestInFile(content, cursor);
}

export function resolveCSS(item) {
    return generator.generate(item.label, {
        preflights: false,
        safelist: false,
    });
}

export function resolveCSSByOffset(content, cursor) {
    return generator.generate(searchUsageBoundary(content, cursor).content, {
        preflights: false,
        safelist: false,
    });
}

process.stdin.on("data", async (chunk) => {
    console.log(JSON.stringify(await getComplete(chunk.toString(), 0)));
})