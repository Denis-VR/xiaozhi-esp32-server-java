/**
 * 国际化配置
 * 注意：需要安装 vue-i18n
 * npm install vue-i18n@9
 */
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'
import ruRU from "./ru-RU";

// 默认语言
const defaultLocale = localStorage.getItem('locale') || 'ru-RU'

// 创建 i18n 实例
export const i18n = createI18n({
  legacy: false, // 使用 Composition API 模式
  locale: defaultLocale,
  fallbackLocale: 'ru-RU',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
    'ru-RU': ruRU,
  },
})

// 导出 t 函数，方便在 JS/TS 中使用
export const { t } = i18n.global

export default i18n

