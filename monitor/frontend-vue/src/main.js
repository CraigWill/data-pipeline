import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import './assets/main.css'
import { install } from '@icon-park/vue-next/es/all'
import '@icon-park/vue-next/styles/index.css'

const app = createApp(App)
install(app)

app.use(createPinia())
app.use(router)

app.mount('#app')
