import { IonicVue } from '@ionic/vue';
import { createPinia } from 'pinia';
import { createApp } from 'vue';

import '@ionic/vue/css/core.css';
import '@ionic/vue/css/normalize.css';
import '@ionic/vue/css/structure.css';
import '@ionic/vue/css/typography.css';

import App from './App.vue';
import router from './router';

const app = createApp(App)
  .use(IonicVue)
  .use(router)
  .use(createPinia());

router.isReady().then(() => {
  app.mount('#app');
});