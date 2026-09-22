import { createRouter, createWebHistory } from '@ionic/vue-router';
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('../components/QrCodeGenerator.vue'),
  },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});