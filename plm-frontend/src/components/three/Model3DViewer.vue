<template>
  <div class="three-viewer" ref="containerRef">
    <div class="viewer-toolbar">
      <el-button-group>
        <el-tooltip content="重置视角" placement="bottom"><el-button icon="Refresh" @click="resetView" /></el-tooltip>
        <el-tooltip content="爆炸视图" placement="bottom"><el-button :type="exploded ? 'primary' : ''" @click="toggleExplode">
          <el-icon><Connection /></el-icon>&nbsp;爆炸
        </el-button></el-tooltip>
        <el-tooltip content="线框模式" placement="bottom"><el-button :type="wireframe ? 'primary' : ''" @click="toggleWireframe">
          <el-icon><Grid /></el-icon>
        </el-button></el-tooltip>
        <el-tooltip content="剖切" placement="bottom"><el-button :type="clipping ? 'primary' : ''" @click="toggleClipping">
          <el-icon><Scissor /></el-icon>
        </el-button></el-tooltip>
        <el-tooltip content="全屏" placement="bottom"><el-button icon="FullScreen" @click="toggleFullscreen" /></el-tooltip>
      </el-button-group>
      <div class="viewer-info">
        <span v-if="partCount > 0">零件: {{ partCount }} | 面数: {{ faceCount.toLocaleString() }}</span>
        <span v-else-if="unsupportedFormat" style="color:#e6a23c">{{ unsupportedFormat.toUpperCase() }} 格式不支持在线预览</span>
        <span v-else-if="loading" style="color:#e6a23c">加载中...</span>
        <span v-else style="color:#f56c6c">未加载</span>
      </div>
    </div>
    <div class="viewer-canvas" ref="canvasRef"></div>
    <div class="viewer-unsupported" v-if="unsupportedFormat">
      <el-icon :size="36" color="#e6a23c"><WarningFilled /></el-icon>
      <h3>{{ unsupportedFormat.toUpperCase() }} 格式暂不支持在线预览</h3>
      <p>Three.js 预览引擎仅支持 <strong>GLB / glTF</strong> 格式</p>
      <p style="font-size:12px;color:#909399;margin-top:4px">STEP / IGES / OBJ / STL 等格式请下载后使用专业 CAD 软件查看</p>
      <el-link type="primary" :href="props.modelUrl" target="_blank" style="margin-top:12px">
        <el-icon><Download /></el-icon>&nbsp;下载原始模型文件
      </el-link>
    </div>
    <div class="viewer-loading" v-if="loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>3D 模型加载中...</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'

const props = defineProps({
  modelUrl: { type: String, default: '' },
  modelFormat: { type: String, default: '' },
  autoRotate: { type: Boolean, default: false }
})

const containerRef = ref(null)
const canvasRef = ref(null)
const loading = ref(true)
const exploded = ref(false)
const wireframe = ref(false)
const clipping = ref(false)
const partCount = ref(0)
const faceCount = ref(0)

let scene, camera, renderer, controls, model, mixer, clock
let originalPositions = []
let explodeDirections = []
let animationId = null
let clipPlane

function initScene() {
  const width = containerRef.value.clientWidth
  const height = containerRef.value.clientHeight

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x2c3e50)
  scene.fog = new THREE.Fog(0x2c3e50, 50, 500)

  camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 2000)
  camera.position.set(80, 60, 120)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.localClippingEnabled = true
  canvasRef.value.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 20
  controls.maxDistance = 400
  controls.autoRotate = props.autoRotate
  controls.autoRotateSpeed = 1.0

  const ambient = new THREE.AmbientLight(0xffffff, 0.5)
  scene.add(ambient)
  const dirLight = new THREE.DirectionalLight(0xffffff, 1.0)
  dirLight.position.set(100, 100, 50)
  dirLight.castShadow = true
  dirLight.shadow.mapSize.set(2048, 2048)
  scene.add(dirLight)
  const dirLight2 = new THREE.DirectionalLight(0xb0c4de, 0.4)
  dirLight2.position.set(-80, 40, -60)
  scene.add(dirLight2)

  const grid = new THREE.GridHelper(400, 40, 0x445566, 0x334455)
  grid.position.y = -0.1
  scene.add(grid)

  clipPlane = new THREE.Plane(new THREE.Vector3(0, 0, 1), 100)

  clock = new THREE.Clock()
}

function loadModel(url) {
  if (!url) { loading.value = false; return }
  const fmt = (props.modelFormat || url.split('.').pop() || '').toLowerCase()
  const glbSupported = ['glb', 'gltf'].includes(fmt)
  if (!glbSupported) {
    loading.value = false
    addFormatWarning(fmt || '未知')
    return
  }
  loading.value = true
  const loader = new GLTFLoader()
  loader.load(
    url,
    (gltf) => {
      if (model) scene.remove(model)
      model = gltf.scene
      const box = new THREE.Box3().setFromObject(model)
      const size = box.getSize(new THREE.Vector3())
      const center = box.getCenter(new THREE.Vector3())
      const maxDim = Math.max(size.x, size.y, size.z)
      const scale = 80 / maxDim
      model.scale.setScalar(scale)
      model.position.sub(center.multiplyScalar(scale))
      model.traverse((child) => {
        if (child.isMesh) {
          child.castShadow = true
          child.receiveShadow = true
          if (child.material) {
            child.material.side = THREE.DoubleSide
            child.material.clipShadows = true
            child.material.clippingPlanes = []
          }
        }
      })
      scene.add(model)
      computeExplodeData(model)
      countGeometry(model)
      loading.value = false
    },
    (xhr) => {
      if (xhr.lengthComputable) {
        const pct = Math.round((xhr.loaded / xhr.total) * 100)
      }
    },
    (error) => {
      console.warn('3D模型加载失败(可能为非GLB格式文件,已显示占位模型)', error?.message || '')
      loading.value = false
      addPlaceholderBox()
    }
  )
}

function addPlaceholderBox() {
  const geo = new THREE.BoxGeometry(40, 40, 40)
  const mat = new THREE.MeshPhongMaterial({ color: 0x409eff, transparent: true, opacity: 0.5, wireframe: false })
  model = new THREE.Mesh(geo, mat)
  scene.add(model)
  partCount.value = 1
  faceCount.value = 12
  const sprite = makeTextSprite('占位模型 (需上传 GLB/glTF 格式文件才能预览)')
  sprite.position.set(0, 40, 0)
  scene.add(sprite)
}

const unsupportedFormat = ref('')

function addFormatWarning(format) {
  unsupportedFormat.value = format
  const geo = new THREE.BoxGeometry(40, 40, 40)
  const mat = new THREE.MeshPhongMaterial({ color: 0xe6a23c, transparent: true, opacity: 0.3, wireframe: true })
  model = new THREE.Mesh(geo, mat)
  scene.add(model)
  partCount.value = 0
  faceCount.value = 0
  const sprite = makeTextSprite(`${format.toUpperCase()} 格式不支持在线预览，仅支持 GLB/glTF`)
  sprite.position.set(0, 45, 0)
  sprite.scale.set(120, 15, 1)
  scene.add(sprite)
}

function makeTextSprite(text) {
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 64
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = 'rgba(0,0,0,0.7)'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.font = '20px Arial'
  ctx.fillStyle = '#ff9900'
  ctx.textAlign = 'center'
  ctx.fillText(text, canvas.width / 2, 40)
  const texture = new THREE.CanvasTexture(canvas)
  const mat = new THREE.SpriteMaterial({ map: texture, transparent: true })
  const sprite = new THREE.Sprite(mat)
  sprite.scale.set(80, 10, 1)
  return sprite
}

function computeExplodeData(root) {
  originalPositions = []
  explodeDirections = []
  root.updateMatrixWorld(true)
  root.traverse((child) => {
    if (child.isMesh) {
      const worldPos = new THREE.Vector3()
      child.getWorldPosition(worldPos)
      originalPositions.push({
        mesh: child,
        position: child.position.clone(),
        worldPos: worldPos
      })
      const dir = worldPos.clone().normalize()
      explodeDirections.push(dir)
    }
  })
  partCount.value = originalPositions.length
}

function countGeometry(root) {
  let faces = 0
  root.traverse((child) => {
    if (child.isMesh && child.geometry) {
      const g = child.geometry
      if (g.index) {
        faces += g.index.count / 3
      } else if (g.attributes.position) {
        faces += g.attributes.position.count / 3
      }
    }
  })
  faceCount.value = Math.floor(faces)
}

function animate() {
  animationId = requestAnimationFrame(animate)
  const delta = clock.getDelta()
  if (mixer) mixer.update(delta)
  controls.update()
  renderer.render(scene, camera)
}

function onResize() {
  if (!containerRef.value) return
  const width = containerRef.value.clientWidth
  const height = containerRef.value.clientHeight
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

function resetView() {
  if (!camera || !controls) return
  camera.position.set(80, 60, 120)
  controls.target.set(0, 0, 0)
  controls.update()
}

function toggleExplode() {
  exploded.value = !exploded.value
  if (!model || originalPositions.length === 0) return
  const factor = exploded.value ? 60 : 0
  originalPositions.forEach((item, i) => {
    const dir = explodeDirections[i]
    const newPos = item.position.clone().add(dir.multiplyScalar(factor))
    animatePosition(item.mesh, item.position.clone(), newPos, 500)
  })
}

function animatePosition(mesh, from, to, duration) {
  const start = performance.now()
  function step() {
    const elapsed = performance.now() - start
    const t = Math.min(elapsed / duration, 1)
    const ease = 1 - Math.pow(1 - t, 3)
    mesh.position.lerpVectors(from, to, ease)
    if (t < 1) requestAnimationFrame(step)
  }
  step()
}

function toggleWireframe() {
  wireframe.value = !wireframe.value
  if (!model) return
  model.traverse((child) => {
    if (child.isMesh && child.material) {
      child.material.wireframe = wireframe.value
    }
  })
}

function toggleClipping() {
  clipping.value = !clipping.value
  if (!model) return
  const planes = clipping.value ? [clipPlane] : []
  model.traverse((child) => {
    if (child.isMesh && child.material) {
      child.material.clippingPlanes = planes
      child.material.needsUpdate = true
    }
  })
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    containerRef.value.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

watch(() => props.modelUrl, (url) => {
  if (url) loadModel(url)
})

onMounted(() => {
  initScene()
  animate()
  if (props.modelUrl) loadModel(props.modelUrl)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  if (animationId) cancelAnimationFrame(animationId)
  window.removeEventListener('resize', onResize)
  if (renderer) {
    renderer.dispose()
    if (canvasRef.value && renderer.domElement.parentNode) {
      canvasRef.value.removeChild(renderer.domElement)
    }
  }
})
</script>

<style scoped lang="scss">
.three-viewer {
  position: relative; width: 100%; height: 100%; min-height: 400px;
  background: #2c3e50; border-radius: 4px; overflow: hidden;
}
.viewer-toolbar {
  position: absolute; top: 12px; left: 12px; z-index: 10;
  display: flex; align-items: center; gap: 12px;
  .viewer-info { color: #ecf0f1; font-size: 12px; background: rgba(0,0,0,0.4); padding: 4px 10px; border-radius: 4px; }
}
.viewer-canvas { width: 100%; height: 100%; }
.viewer-loading {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; color: #ecf0f1;
  background: rgba(44,62,80,0.7);
  p { margin-top: 12px; }
}
.viewer-unsupported {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; color: #ecf0f1; text-align: center;
  background: rgba(44,62,80,0.85);
  h3 { margin: 12px 0 4px; }
  p { margin: 4px 0; font-size: 13px; }
}
</style>
