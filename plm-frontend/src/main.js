import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import OperationGuide from './components/OperationGuide.vue'
import DataIOBar from './components/DataIOBar.vue'
import BatchImport from './components/BatchImport.vue'
import './assets/styles/global.scss'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.component('OperationGuide', OperationGuide)
app.component('DataIOBar', DataIOBar)
app.component('BatchImport', BatchImport)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 前端重新部署后 chunk 哈希变化，旧标签页动态导入会 404；检测到即自动刷新一次
router.onError((error) => {
  const msg = (error && error.message) || ''
  if (msg.includes('Failed to fetch dynamically imported module') || msg.includes('Importing a module script failed')) {
    if (!sessionStorage.getItem('plm_chunk_reload')) {
      sessionStorage.setItem('plm_chunk_reload', '1')
      window.location.reload()
    }
  }
})

app.mount('#app')
