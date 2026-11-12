import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/api/request'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/auros-skin/views/login/LoginPage.vue'),
      meta: { level: 0, noAuth: true },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('@/auros-skin/views/home/HomePage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/calendar',
      name: 'calendar',
      component: () => import('@/auros-skin/views/calendar/CalendarPage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/discover',
      name: 'discover',
      component: () => import('@/auros-skin/views/discover/DiscoverPage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/auros-skin/views/profile/ProfilePage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/holding/:id',
      name: 'holding-detail',
      component: () => import('@/auros-skin/views/holding-detail/HoldingDetailPage.vue'),
      props: true,
      meta: { level: 2 },
    },
    {
      path: '/trade/add',
      name: 'trade-add',
      component: () => import('@/auros-skin/views/trade-add/TradeAddPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/holding/add',
      name: 'holding-add',
      component: () => import('@/auros-skin/views/holding-add/HoldingAddPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/coverage',
      name: 'coverage',
      component: () => import('@/auros-skin/views/coverage/CoveragePage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/accounts',
      name: 'accounts',
      component: () => import('@/auros-skin/views/accounts/AccountsPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/coverage/settings',
      name: 'coverage-settings',
      component: () => import('@/auros-skin/views/coverage/SettingsPage.vue'),
      meta: { level: 2 },
    },
    // 合并到"我的"页面
    {
      path: '/metrics/settings',
      name: 'metric-settings',
      component: () => import('@/auros-skin/views/metrics/MetricSettings.vue'),
      meta: { level: 2 },
    },
    {
      path: '/holding/:id/transactions',
      name: 'transaction-list',
      component: () => import('@/auros-skin/views/transactions/TransactionListPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/holding/:id/dividends',
      name: 'dividend-history',
      component: () => import('@/auros-skin/views/dividends/DividendHistoryPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/dca-plans/:id',
      name: 'dca-plan-detail',
      component: () => import('@/auros-skin/views/dca/DcaPlanDetailPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/asset-history',
      name: 'asset-history',
      component: () => import('@/auros-skin/views/asset-history/AssetHistoryPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/asset-history/holding/:id',
      name: 'holding-history',
      component: () => import('@/auros-skin/views/asset-history/HoldingHistoryPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/scheduler',
      name: 'scheduler',
      component: () => import('@/auros-skin/views/scheduler/SchedulerPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/auros-skin/views/home/HomePage.vue'),
      meta: { level: 1 },
    },
  ],
})

// 导航守卫：未登录时重定向到登录页
router.beforeEach((to, _from, next) => {
  if (to.meta.noAuth) {
    next() // 登录页不需要认证
    return
  }
  const token = getToken()
  if (!token) {
    next({ name: 'login' })
    return
  }
  next()
})

export default router
