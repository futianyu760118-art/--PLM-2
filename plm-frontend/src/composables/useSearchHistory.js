// 搜索历史：选择/输入框点开时弹出历史输入与相关推荐，选中即搜
import { ref, computed } from 'vue'

const STORE = 'plm_search_history'
const MAX = 12

function readAll() {
  try { return JSON.parse(localStorage.getItem(STORE) || '{}') } catch { return {} }
}

export function useSearchHistory(key) {
  const history = ref(readAll()[key] || [])

  function record(value) {
    const v = (value || '').trim()
    if (!v) return
    const all = readAll()
    const list = [v, ...(all[key] || []).filter((x) => x !== v)].slice(0, MAX)
    all[key] = list
    localStorage.setItem(STORE, JSON.stringify(all))
    history.value = list
  }

  function clear() {
    const all = readAll()
    delete all[key]
    localStorage.setItem(STORE, JSON.stringify(all))
    history.value = []
  }

  return { history, record, clear }
}
