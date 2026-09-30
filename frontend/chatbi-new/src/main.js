import Vue from 'vue'
import App from './App.vue'
import router from './router'
import './permission'

document.title = '数据分析平台'
Vue.config.productionTip = false



import ElementUI from 'element-ui';
import 'element-ui/lib/theme-chalk/index.css';


import markdownitVue from 'markdown-it-vue';
Vue.component('markdown-it-vue', markdownitVue);

import { download } from '@/utils/request'
Vue.prototype.download = download

import "@/styles/design-tokens.scss";
import "@/styles/element-override.scss";
import "@/styles/global.scss";

import Pagination from "@/components/Pagination";
import BaseIcon from "@/components/BaseIcon";
Vue.component('Pagination', Pagination)
Vue.component('BaseIcon', BaseIcon)


//import { messageDuration } from "@/utils/common.js";
import { toolTipOpenDelay,toolTipPlacement,toolTipEffect } from "@/utils/common.js";
Vue.prototype.$toolTipOpenDelay = toolTipOpenDelay
Vue.prototype.$toolTipPlacement = toolTipPlacement
Vue.prototype.$toolTipEffect = toolTipEffect


//Vue.prototype.$messageDuration = messageDuration
import ElTableColumnWithTooltip from '@/components/ElTableColumnWithTooltip'

// 注册全局组件
Vue.component('ElTableColumnWithTooltip', ElTableColumnWithTooltip)

Vue.use(ElementUI);


import Vant, { Lazyload } from 'vant';
import 'vant/lib/index.css';

import '@/styles/web/base.scss';
import '@/styles/web/animations.scss';
import '@/styles/web/vant-theme.scss';
import '@/styles/web/markdown.scss';

import AppIcon from '@/components/web/AppIcon.vue';
import toast from '@/utils/toast';

Vue.use(Lazyload);
Vue.component('AppIcon', AppIcon);

Vue.prototype.$toast2 = toast;

Vue.use(Vant);



new Vue({
  router,
 
  render: h => h(App),
}).$mount('#app')


// 移除启动动画
requestAnimationFrame(() => {
  setTimeout(() => {
    const boot = document.getElementById('boot');
    if (boot) {
      boot.classList.add('done');
      setTimeout(() => boot.remove(), 400);
    }
  }, 260);
});