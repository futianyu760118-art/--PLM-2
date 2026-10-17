<template>
  <el-drawer v-model="visible" title="外观设置" size="380px" :append-to-body="true">
    <div class="theme-panel">
      <section>
        <h4>强调色</h4>
        <div class="swatches">
          <button v-for="a in ACCENT_PRESETS" :key="a.key" class="swatch" :class="{ on: !customAccent && accent === a.key }"
            :style="{ background: a.color }" :title="a.label" @click="accent = a.key; customAccent = ''" />
        </div>
        <div class="custom-row">
          <span>自定义</span>
          <input v-model="customAccent" type="color" aria-label="自定义强调色" />
          <el-button v-if="customAccent" link size="small" @click="customAccent = ''">清除</el-button>
        </div>
      </section>

      <section>
        <h4>页面底色</h4>
        <div class="swatches">
          <button v-for="b in BG_PRESETS" :key="b.key" class="swatch bgsw" :class="{ on: !customBg && pageBg === b.key }"
            :style="{ background: b.color }" :title="b.label" @click="pageBg = b.key; customBg = ''" />
        </div>
        <div class="custom-row">
          <span>自定义</span>
          <input v-model="customBg" type="color" aria-label="自定义页面底色" />
          <el-button v-if="customBg" link size="small" @click="customBg = ''">清除</el-button>
        </div>
      </section>

      <section>
        <h4>侧栏</h4>
        <el-radio-group v-model="sidebar">
          <el-radio-button value="mode">跟随模式</el-radio-button>
          <el-radio-button value="light">浅色</el-radio-button>
          <el-radio-button value="dark">深色</el-radio-button>
        </el-radio-group>
      </section>

      <section>
        <h4>密度</h4>
        <el-radio-group v-model="density">
          <el-radio-button value="comfortable">舒适</el-radio-button>
          <el-radio-button value="compact">紧凑</el-radio-button>
        </el-radio-group>
      </section>

      <section class="note">
        <p>侧栏配色随模式自动适配；当前模式：{{ MODES[mode]?.label }}。</p>
        <p>设置仅保存在本机浏览器（schemaVersion={{ 1 }}），可随时恢复默认。</p>
        <el-button size="small" @click="reset">恢复默认外观</el-button>
      </section>
    </div>
  </el-drawer>
</template>

<script setup>
import { useAppearanceStore, ACCENT_PRESETS, BG_PRESETS } from '@/stores/appearance'
import { MODES } from '@/config/modes'
import { storeToRefs } from 'pinia'

const visible = defineModel({ type: Boolean, default: false })
const store = useAppearanceStore()
const { accent, customAccent, pageBg, customBg, density, sidebar, mode } = storeToRefs(store)
</script>

<style scoped lang="scss">
.theme-panel { display: flex; flex-direction: column; gap: 22px;
  h4 { margin: 0 0 8px; font-size: 13px; color: #606266; }
  .swatches { display: flex; gap: 8px; flex-wrap: wrap; }
  .swatch { width: 30px; height: 30px; border-radius: 6px; border: 2px solid transparent; cursor: pointer;
    transition: transform .12s cubic-bezier(.34,1.56,.64,1);
    &:active { transform: scale(.92); }
    &.on { border-color: #303133; box-shadow: 0 0 0 2px #fff inset; }
    &.bgsw { border: 1px solid #dcdfe6; &.on { border: 2px solid #303133; } } }
  .custom-row { margin-top: 8px; display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266;
    input[type='color'] { width: 36px; height: 26px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; padding: 2px; cursor: pointer; } }
  .note { font-size: 12px; color: #909399; p { margin: 0 0 6px; } }
}
</style>
