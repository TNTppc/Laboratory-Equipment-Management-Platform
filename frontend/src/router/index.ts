import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from '../stores/auth'
import Shell from '../layouts/Shell.vue'
import Login from '../views/Login.vue'
import Workspace from '../views/Workspace.vue'

const router = createRouter({ history: createWebHashHistory(), routes: [{ path: '/login', component: Login }, { path: '/', component: Shell, children: [{ path: '', component: Workspace }] }] })
router.beforeEach(to => { const auth = useAuth(); if (to.path !== '/login' && !auth.token) return '/login'; if (to.path === '/login' && auth.token) return '/' })
export default router
