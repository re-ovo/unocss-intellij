import type {UnoGenerator, UserConfig} from '@unocss/core'
import {cssIdRE} from "@unocss/core";
import {UnocssNuxtOptions} from "@unocss/nuxt";
import {
    presetAttributify,
    presetIcons,
    presetTagify,
    presetTypography,
    presetUno,
    presetWebFonts,
    presetWind
} from "unocss";

export function throttle<T extends ((...args: any) => any)>(func: T, timeFrame: number): T {
    let lastTime = 0
    let timer: any
    return function (...args) {
        const now = Date.now()
        clearTimeout(timer)
        if (now - lastTime >= timeFrame) {
            lastTime = now
            return func(...args)
        } else {
            timer = setTimeout(func, timeFrame, ...args)
        }
    } as T
}

export async function getCSS(uno: UnoGenerator, utilName: string) {
    const {css} = await uno.generate(utilName, {preflights: false, safelist: false})
    return css
}

export function deprecationCheck(config: UserConfig) {
    let warned = false

    function warn(msg: string) {
        warned = true
        console.warn(`[unocss] ${msg}`)
    }

    if (config.include)
        warn('`include` option is deprecated, use `content.pipeline.include` instead.')

    if (config.exclude)
        warn('`exclude` option is deprecated, use `content.pipeline.exclude` instead.')

    if (config.extraContent)
        warn('`extraContent` option is deprecated, use `content` instead.')

    if (config.content?.plain)
        warn('`content.plain` option is renamed to `content.inline`.')

    if (warned && typeof process !== 'undefined' && process.env.CI)
        throw new Error('deprecation warning')
}

export const defaultPipelineExclude = [cssIdRE]

export function resolveNuxtOptions(options: UnocssNuxtOptions) {
    if (options.presets == null) {
        options.presets = []
        const presetMap = {
            uno: presetUno,
            attributify: presetAttributify,
            tagify: presetTagify,
            icons: presetIcons,
            webFonts: presetWebFonts,
            typography: presetTypography,
            wind: presetWind,
        }
        for (const [key, preset] of Object.entries(presetMap)) {
            const option = options[key as keyof UnocssNuxtOptions]
            if (option)
                options.presets.push(preset(typeof option === 'boolean' ? {} as any : option))
        }
    }

    options.content ??= {}
    options.content.pipeline ??= {}
    if (options.content.pipeline !== false) {
        options.content.pipeline.exclude ??= defaultPipelineExclude
        if (Array.isArray(options.content.pipeline.exclude))
            // ignore macro files created by Nuxt
            options.content.pipeline.exclude.push(/\?macro=true/)
    }
}