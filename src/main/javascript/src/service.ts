import {GenerateResult, presetUno} from "unocss";
import {createGenerator} from "@unocss/core";
import {createAutocomplete, searchUsageBoundary} from "@unocss/autocomplete";
import readline from "readline";
import {deprecationCheck, resolveNuxtOptions} from "./utils";
import {getMatchedPositionsFromCode} from "./match";
import {loadConfig} from "@unocss/config";
import {sourceObjectFields, sourcePluginFactory} from "unconfig/presets";

console.log('[UnoProcess]', `Hello from service.js! ${process.cwd()}`);

const defaultConfig = {presets: [presetUno()]};

let generator = createGenerator({}, defaultConfig);
let autocomplete = createAutocomplete(generator);

export async function resolveConfig(rootDir: string) {
  const loadResult = await loadConfig(process.cwd(), rootDir, [
    sourcePluginFactory({
      files: [
        'vite.config',
        'svelte.config',
        'iles.config',
      ],
      targetModule: 'unocss/vite',
      parameters: [{command: 'serve', mode: 'development'}],
    }),
    sourcePluginFactory({
      files: [
        'astro.config',
      ],
      targetModule: 'unocss/astro',
    }),
    sourceObjectFields({
      files: 'nuxt.config',
      fields: 'unocss',
    }),
  ]);

  if (loadResult.sources.some(s => s.includes('nuxt.config'))) {
    resolveNuxtOptions(loadResult.config);
  }
  deprecationCheck(loadResult.config);

  generator.setConfig(loadResult.config, defaultConfig);
  autocomplete = createAutocomplete(generator);

  return generator.config;
}

export async function getComplete(content: string) {
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

type ResolveCSSResult = GenerateResult & {
  matchedTokens?: string[]
  remToPxPreview?: number
}

export async function resolveCSS(item: string) {
  const result: ResolveCSSResult
    = await generator.generate(item, {preflights: false, safelist: false});
  result.matchedTokens = Array.from(result.matched || []);
  // todo add remToPxPreview
  return result;
}

export async function resolveCSSByOffset(content: string, cursor: number) {
  const boundaryContent = searchUsageBoundary(content, cursor).content;
  const result: ResolveCSSResult
    = await generator.generate(boundaryContent, {preflights: false, safelist: false});
  result.matchedTokens = Array.from(result.matched || []);
  // todo add remToPxPreview
  return result;
}

/**
 *
 * @param id
 * @param code
 * @see https://github.com/unocss/unocss/blob/main/packages/shared-common/src/index.ts#L188
 */
export async function resolveAnnotations(id: string, code: string) {
  return await getMatchedPositionsFromCode(generator, code, id);
}

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
})

rl.on('line', async (input) => {
  if (input === 'test') {
    console.log(JSON.stringify(await resolveCSS('color-red flex items-center')))
    return
  }
  const command = JSON.parse(input.trim());
  const action = command.action;
  const data = command.data;
  const id = command.id;

  try {
    const result = await handle_command(action, data);
    console.log(JSON.stringify({id, action, result}));
  } catch (e: any) {
    console.log(JSON.stringify({
      id,
      error: e.message || JSON.stringify(e),
    }));
  }
})

async function handle_command(command: string, data: any) {
  switch (command) {
    case "resolveConfig":
      return await resolveConfig(data?.rootDir || '');
    case "getComplete":
      return await getComplete(data.content);
    case "resolveCSSByOffset":
      return await resolveCSSByOffset(data.content, data.cursor);
    case "resolveCSS":
      return await resolveCSS(data.content);
    case "resolveAnnotations":
      return await resolveAnnotations(data.id, data.content)
  }
}