import {type GenerateResult, presetUno} from "unocss";
import {createGenerator} from "@unocss/core";
import {type AutoCompleteMatchType, createAutocomplete, searchUsageBoundary} from "@unocss/autocomplete";
import readline from "readline";
import {deprecationCheck, resolveNuxtOptions} from "./utils";
import {applyTransformers, getMatchedPositionsFromCode} from "./match";
import {loadConfig} from "@unocss/config";
import {sourceObjectFields, sourcePluginFactory} from "unconfig/presets";
import {log} from "./log";

console.log('[UnoProcess]', `Hello from service.js! ${process.cwd()}`);

const defaultConfig = {presets: [presetUno()]};

let matchType: AutoCompleteMatchType = 'prefix';
let generator = createGenerator({}, defaultConfig);
let autocomplete = createAutocomplete(generator);

async function resolveConfig(rootDir: string) {
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
  autocomplete = createAutocomplete(generator, {matchType});

  // we need to trim all fields except the name to avoid circular references
  // which will cause problems when calling JSON.stringify()
  return {
    presets: generator.config.presets?.map(item => {
      return {
        name: item.name,
      }
    }) ?? [],
    transformers: generator.config.transformers?.map(item => {
      return {
        name: item.name,
      }
    }) ?? [],
    theme: generator.config.theme
  };
}

async function updateSettings(newMatchType: AutoCompleteMatchType) {
  log('updateSettings: matchType', newMatchType)
  matchType = newMatchType;

  autocomplete = createAutocomplete(generator, {matchType});
}

async function getComplete(content: string, maxItems: number | undefined) {
  let suggestions = await autocomplete.suggest(content, true);
  return Promise.all(suggestions.slice(0, maxItems).map(async (suggestion) => {
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

async function resolveCSS(item: string) {
  const annotations = await applyTransformers(generator, item)
  const input = annotations && annotations.length > 0
    ? annotations.map(it => it.className).join(' ')
    : item

  const result: ResolveCSSResult
    = await generator.generate(input, {preflights: false, safelist: false});
  result.matchedTokens = Array.from(result.matched || []);
  return result;
}

async function resolveCSSByOffset(content: string, cursor: number) {
  const boundaryContent = searchUsageBoundary(content, cursor)?.content;
  if (!boundaryContent)
    return null;
  const result: ResolveCSSResult
    = await generator.generate(boundaryContent, {preflights: false, safelist: false});
  result.matchedTokens = Array.from(result.matched || []);
  return result;
}

/**
 *
 * @param id
 * @param code
 * @see https://github.com/unocss/unocss/blob/main/packages/shared-common/src/index.ts#L188
 */
async function resolveAnnotations(id: string, code: string) {
  return await getMatchedPositionsFromCode(generator, code, id);
}

async function resolveBreakpoints() {
  let breakpoints;
  if (generator.userConfig && generator.userConfig.theme)
    breakpoints = generator.userConfig.theme.breakpoints;
  if (!breakpoints)
    breakpoints = generator.config.theme.breakpoints;
  return { breakpoints };
}

async function resolveToken(raw: string, alias?: string) {
  let result = await generator.parseToken(raw, alias);
  if (!result) {
    return {result: []}
  }

  return {
    result: result.map(([index, selector, body, parent, _, __, noMerge]) => ({
      index, selector, body, parent, noMerge
    }))
  };
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
    case "updateSettings":
      return await updateSettings(data.matchType);
    case "getComplete":
      return await getComplete(data.content, data.maxItems);
    case "resolveCSSByOffset":
      return await resolveCSSByOffset(data.content, data.cursor);
    case "resolveCSS":
      return await resolveCSS(data.content);
    case "resolveAnnotations":
      return await resolveAnnotations(data.id, data.content)
    case "resolveBreakpoints":
      return await resolveBreakpoints()
    case "resolveToken":
      return await resolveToken(data.raw, data.alias)
  }
}