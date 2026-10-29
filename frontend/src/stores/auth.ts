import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'

export const useAuth = defineStore('auth', () => {
  const token = ref(localStorage.getItem('lab-token') || '')
  const user = ref<any>(null)
  async function login(username: string, password: string) { const result = await api.post('/auth/login', { username, password }); token.value = result.token; user.value = result.user; localStorage.setItem('lab-token', result.token) }
  function logout() { token.value = ''; user.value = null; localStorage.removeItem('lab-token') }
  return { token, user, login, logout }
})
