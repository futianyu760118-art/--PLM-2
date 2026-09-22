import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo, login as loginApi, logout as logoutApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('plm_token') || '')
  const userId = ref(null)
  const username = ref('')
  const realName = ref('')
  const avatar = ref('')
  const roles = ref([])
  const permissions = ref([])

  async function login(loginForm) {
    const res = await loginApi(loginForm)
    token.value = res.data.token
    localStorage.setItem('plm_token', res.data.token)
    userId.value = res.data.userId
    username.value = res.data.username
    realName.value = res.data.realName
    avatar.value = res.data.avatar
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
    return res
  }

  async function fetchInfo() {
    const res = await getUserInfo()
    userId.value = res.data.userId
    username.value = res.data.username
    realName.value = res.data.realName
    avatar.value = res.data.avatar
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
    return res
  }

  function logout() {
    return logoutApi().finally(() => {
      reset()
    })
  }

  function reset() {
    token.value = ''
    userId.value = null
    username.value = ''
    realName.value = ''
    avatar.value = ''
    roles.value = []
    permissions.value = []
    localStorage.removeItem('plm_token')
  }

  function hasPermission(code) {
    return permissions.value.includes(code) || roles.value.includes('ADMIN')
  }

  return { token, userId, username, realName, avatar, roles, permissions, login, fetchInfo, logout, reset, hasPermission }
})
