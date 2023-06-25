import {sourceObjectFields, sourcePluginFactory} from "unconfig/presets";
import {loadConfig} from "@unocss/config";
import {createGenerator} from "@unocss/core";
import {createAutocomplete, searchUsageBoundary} from "@unocss/autocomplete";
import readline from "readline";
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

export async function getComplete(content) {
    const suggestions = await autocomplete.suggest(content);
    return Promise.all(suggestions.map(async (suggestion) => {
        return {
            className: suggestion,
            css: (await generator.generate(suggestion, {
                preflights: false,
                safelist: false,
                minify: false
            })).css
        }
    }));
}

export function resolveCSS(item) {
    return generator.generate(item.label, {
        preflights: false,
        safelist: false,
    });
}

export function resolveCSSByOffset(content, cursor) {
    const boundaryContent = searchUsageBoundary(content, cursor).content;
    return generator.generate(boundaryContent, {
        preflights: false,
        safelist: false,
        minify: false
    });
}

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
})

rl.on('line', async (input) => {
    try {
        const command = JSON.parse(input.trim());
        const action = command.action;
        const data = command.data;
        const id = command.id;

        const result = await handle_command(action, data);
        console.log(JSON.stringify({id, action, result}));
    } catch (e) {
        console.error(e);
    }
})

async function handle_command(command, data) {
    if (command === "resolveConfig") {
        await resolveConfig(data?.rootDir || '');
        return {}
    } else if (command === "getComplete") {
        return await getComplete(data.content);
    } else if (command === "resolveCSSByOffset") {
        return await resolveCSSByOffset(data.content, data.cursor);
    } else if (command === "resolveCSS") {
        return await resolveCSS(data.content);
    }
}