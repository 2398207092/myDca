import { createRouter, createWebHashHistory } from 'vue-router'
import { authState, bootstrapAuth } from '@/shared/auth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/now' },
    {
      path: '/now',
      name: 'now',
      component: () => import('@/features/now/NowView.vue'),
      meta: { chrome: true, face: '现在', title: '现在' },
    },
    {
      path: '/past',
      name: 'past',
      component: () => import('@/features/past/PastView.vue'),
      meta: { chrome: true, face: '过去', title: '过去' },
    },
    {
      path: '/future',
      name: 'future',
      component: () => import('@/features/future/FutureView.vue'),
      meta: { chrome: true, face: '未来', title: '未来' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/auth/LoginView.vue'),
      meta: { chrome: false, title: '登录' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/now' },
  ],
})

router.beforeEach(async (to) => {
  await bootstrapAuth()
  if (to.name === 'login') {
    return authState.authed ? { name: 'now' } : true
  }
  if (!authState.authed) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) ?? 'myDca'
  document.title = `${title} · myDca`
})

export default router
