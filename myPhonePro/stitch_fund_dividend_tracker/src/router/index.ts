import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/home/HomePage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/calendar',
      name: 'calendar',
      component: () => import('@/views/calendar/CalendarPage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/discover',
      name: 'discover',
      component: () => import('@/views/discover/DiscoverPage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/profile/ProfilePage.vue'),
      meta: { level: 1 },
    },
    {
      path: '/holding/:id',
      name: 'holding-detail',
      component: () => import('@/views/holding-detail/HoldingDetailPage.vue'),
      props: true,
      meta: { level: 2 },
    },
    {
      path: '/trade/add',
      name: 'trade-add',
      component: () => import('@/views/trade-add/TradeAddPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/holding/add',
      name: 'holding-add',
      component: () => import('@/views/holding-add/HoldingAddPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/coverage',
      name: 'coverage',
      component: () => import('@/views/coverage/CoveragePage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/coverage/settings',
      name: 'coverage-settings',
      component: () => import('@/views/coverage/SettingsPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/profile/tools',
      name: 'profile-tools',
      component: () => import('@/views/profile/ToolboxPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/metrics/settings',
      name: 'metric-settings',
      component: () => import('@/views/metrics/MetricSettings.vue'),
      meta: { level: 2 },
    },
    {
      path: '/holding/:id/transactions',
      name: 'transaction-list',
      component: () => import('@/views/transactions/TransactionListPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/holding/:id/dividends',
      name: 'dividend-history',
      component: () => import('@/views/dividends/DividendHistoryPage.vue'),
      meta: { level: 2 },
    },
    {
      path: '/dca-plans/:id',
      name: 'dca-plan-detail',
      component: () => import('@/views/dca/DcaPlanDetailPage.vue'),
      meta: { level: 2 },
    },
  ],
})

export default router
