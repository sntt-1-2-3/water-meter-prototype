import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAvatar, ElBadge, ElButton, ElDialog, ElDrawer, ElDropdown, ElDropdownItem,
  ElDropdownMenu, ElForm, ElFormItem, ElIcon,
  ElInput, ElInputNumber, ElMenu, ElMenuItem, ElOption, ElPagination, ElProgress,
  ElRadioButton, ElRadioGroup, ElResult, ElSelect, ElSkeleton, ElTable,
  ElTableColumn, ElTag,
} from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { useSystemStore } from './stores/system'
import { authExpiredEvent } from './utils/session'

const app = createApp(App)

const elementComponents = [
  ElAvatar, ElBadge, ElButton, ElDialog, ElDrawer, ElDropdown, ElDropdownItem,
  ElDropdownMenu, ElForm, ElFormItem, ElIcon,
  ElInput, ElInputNumber, ElMenu, ElMenuItem, ElOption, ElPagination, ElProgress,
  ElRadioButton, ElRadioGroup, ElResult, ElSelect, ElSkeleton, ElTable,
  ElTableColumn, ElTag,
]

elementComponents.forEach((component) => app.component(component.name!, component))

const pinia = createPinia()
app.use(pinia)
window.addEventListener(authExpiredEvent, () => {
  useSystemStore(pinia).logout()
  if (router.currentRoute.value.path !== '/login') void router.replace('/login')
})
app
  .use(router)
  .mount('#app')
