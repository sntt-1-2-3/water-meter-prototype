import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAvatar, ElBadge, ElButton, ElDialog, ElDrawer, ElForm, ElFormItem, ElIcon,
  ElInput, ElInputNumber, ElMenu, ElMenuItem, ElOption, ElPagination, ElProgress,
  ElRadioButton, ElRadioGroup, ElResult, ElSelect, ElSkeleton, ElTable,
  ElTableColumn, ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

const elementComponents = [
  ElAvatar, ElBadge, ElButton, ElDialog, ElDrawer, ElForm, ElFormItem, ElIcon,
  ElInput, ElInputNumber, ElMenu, ElMenuItem, ElOption, ElPagination, ElProgress,
  ElRadioButton, ElRadioGroup, ElResult, ElSelect, ElSkeleton, ElTable,
  ElTableColumn, ElTag,
]

elementComponents.forEach((component) => app.component(component.name!, component))

app
  .use(createPinia())
  .use(router)
  .mount('#app')
