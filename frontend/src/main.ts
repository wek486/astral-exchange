import { createApp,h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory,RouterView } from 'vue-router'
import CrashChallenge from './components/CrashChallenge.vue'
import './style.css'
if(import.meta.env.MODE==='demo'){
  createApp(CrashChallenge).mount('#app')
}else{
  const app=()=>import('./App.vue')
  const router=createRouter({history:createWebHistory(),routes:[
    ...['/','/assets','/ranking'].map(path=>({path,component:app})),
    {path:'/challenge',component:CrashChallenge},
  ]})
  createApp({render:()=>h(RouterView)}).use(createPinia()).use(router).mount('#app')
}
