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

export function getComplete(content) {
    return autocomplete.suggest(content);
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
        return await getComplete(data.content, data.content.length);
    }
}