import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from './api'

// hash 路由：和 App 一样，nginx 不需要 SPA 回退（ADR 0048）。
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
    {
      path: '/',
      component: () => import('./views/LayoutView.vue'),
      redirect: '/foods',
      children: [
        { path: 'foods', name: 'foods', component: () => import('./views/FoodsView.vue') },
        { path: 'foods/new', name: 'food-new', component: () => import('./views/FoodEditView.vue') },
        { path: 'foods/:id', name: 'food-edit', component: () => import('./views/FoodEditView.vue') },
        { path: 'exercises', name: 'exercises', component: () => import('./views/ExercisesView.vue') },
        { path: 'users', name: 'users', component: () => import('./views/UsersView.vue') }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/foods' }
  ]
})

router.beforeEach((to) => {
  if (to.name !== 'login' && !getToken()) return { name: 'login' }
})
