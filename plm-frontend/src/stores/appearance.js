// 外观与模式偏好（依据资料包 02/03：accent/底色/侧栏/密度解耦，schemaVersion 迁移，恢复默认）
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { MODES, isModeKey } from '@/config/modes'

const STORAGE_KEY = 'plm_appearance'
const SCHEMA_VERSION = 1

export const ACCENT_PRESETS = [
  { key: 'tech-blue', label: '科技蓝', color: '#2060f3' },
  { key: 'turquoise', label: '松石绿', color: '#137d83' },
  { key: 'forest', label: '森林绿', color: '#25825b' },
  { key: 'amber', label: '琥珀橙', color: '#c56b19' },
  { key: 'violet', label: '紫罗兰', color: '#7656bb' },
  { key: 'graphite', label: '石墨灰', color: '#525b69' }
]

export const BG_PRESETS = [
  { key: 'pure-white', label: '纯白', color: '#ffffff' },
  { key: 'mist', label: '雾灰', color: '#f4f6f8' },
  { key: 'ice-blue', label: '冰蓝', color: '#edf3fa' },
  { key: 'mint', label: '浅绿', color: '#eef5f1' },
  { key: 'warm-rice', label: '暖米', color: '#f8f4ec' },
  { key: 'lilac', label: '浅紫', color: '#f2eff8' },
  { key: 'rock', label: '岩灰', color: '#e2e6eb' },
  { key: 'deep-slate', label: '深石墨', color: '#202a36' }
]

function loadSaved() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const obj = JSON.parse(raw)
    if (!obj || obj.schemaVersion !== SCHEMA_VERSION) return null
    return obj
  } catch { return null }
}

export const useAppearanceStore = defineStore('appearance', () => {
  const saved = loadSaved()
  const mode = ref(saved?.mode && isModeKey(saved.mode) ? saved.mode : 'classic')
  const accent = ref(saved?.accent || 'tech-blue')
  const customAccent = ref(saved?.customAccent || '')
  const pageBg = ref(saved?.pageBg || 'mist')
  const customBg = ref(saved?.customBg || '')
  const density = ref(saved?.density || 'comfortable') // comfortable | compact
  // 侧栏: mode(跟随模式) | light | dark —— 对齐 demo appearance.sidebar
  const sidebar = ref(['mode', 'light', 'dark'].includes(saved?.sidebar) ? saved.sidebar : 'mode')

  function accentColor() {
    if (customAccent.value) return customAccent.value
    return (ACCENT_PRESETS.find((a) => a.key === accent.value) || ACCENT_PRESETS[0]).color
  }
  function bgColor() {
    if (customBg.value) return customBg.value
    return (BG_PRESETS.find((a) => a.key === pageBg.value) || BG_PRESETS[1]).color
  }
  function sidebarVariant() {
    if (sidebar.value === 'light') return 'light'
    if (sidebar.value === 'dark') return 'dark'
    return MODES[mode.value]?.sidebar || 'dark' // dark | light | gray
  }

  function apply() {
    const root = document.documentElement
    root.style.setProperty('--plm-accent', accentColor())
    root.style.setProperty('--plm-bg', bgColor())
    root.dataset.mode = mode.value
    root.dataset.density = density.value
    root.dataset.sidebar = sidebarVariant()
  }

  function persist() {
    const obj = {
      schemaVersion: SCHEMA_VERSION,
      mode: mode.value, accent: accent.value, customAccent: customAccent.value,
      pageBg: pageBg.value, customBg: customBg.value, density: density.value,
      sidebar: sidebar.value
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(obj))
  }

  function setMode(k) {
    if (!isModeKey(k)) return
    mode.value = k
    persist(); apply()
  }
  function reset() {
    accent.value = 'tech-blue'; customAccent.value = ''
    pageBg.value = 'mist'; customBg.value = ''
    density.value = 'comfortable'; sidebar.value = 'mode'
    persist(); apply()
  }

  watch([accent, customAccent, pageBg, customBg, density, sidebar], () => { persist(); apply() })

  // 初始化即应用
  apply()

  return { mode, accent, customAccent, pageBg, customBg, density, sidebar,
    accentColor, bgColor, sidebarVariant, setMode, reset, apply, persist }
})
