import { defineStore } from 'pinia'
import router, { constantRoutes } from '@/router'
import { getRouters } from '@/api/menu'
import type { MenuRecord } from '@/api/menu'

const viewModules = import.meta.glob('../../views/**/*.vue')

const viewAliases: Record<string, string> = {
  supplier_product_list: 'vendor/product/goods_list',
}

function loadView(view: string) {
  if (view === 'Layout') return () => import('@/layout/index.vue')
  if (view === 'ParentView') return () => import('@/components/ParentView/index.vue')

  const normalized = (viewAliases[view] || view)
    .replace(/\\/g, '/')
    .replace(/\.vue$/, '')
    .replace(/\/+$/, '')
  const targets = new Set([normalized, `${normalized}/index`])

  for (const path of Object.keys(viewModules)) {
    const dir = path.split('views/')[1]?.replace(/\.vue$/, '')
    if (dir && targets.has(dir)) {
      return viewModules[path]
    }
  }

  console.error(`[permission.ts] 找不到组件: ${view}，请确认 vue3/src/views 下有对应文件`)
  return () => import('@/views/error/ComponentNotFound.vue')
}

function cleanPath(path: string): string {
  if (!path) return ''
  let result = path
  while (result.includes('//')) {
    result = result.replace('//', '/')
  }
  if (result.endsWith('/')) {
    result = result.slice(0, -1)
  }
  return result
}

function resolveRoutePath(path: string, parentPath: string): string {
  if (!path) return parentPath || ''
  if (path.startsWith('/')) return cleanPath(path)
  if (!parentPath || parentPath === '/') {
    return cleanPath('/' + path)
  }
  let result = parentPath
  if (!result.endsWith('/')) result += '/'
  result += path
  return cleanPath(result)
}

function filterAsyncRouter(routes: MenuRecord[], parentPath: string = ''): any[] {
  return routes
    .filter((route) => route.component || route.children?.length)
    .map((route) => {
      const r: Record<string, any> = { ...route }
      r.path = resolveRoutePath(r.path, parentPath)
      if (r.component) {
        r.component = loadView(r.component)
      }
      if (r.children?.length) {
        r.children = filterAsyncRouter(r.children, r.path)
      } else {
        delete r.children
        delete r.redirect
      }
      return r
    })
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    routes: [] as any[],
    addRoutes: [] as any[],
  }),
  actions: {
    async GenerateRoutes() {
      const res = await getRouters()
      const rewriteRoutes = filterAsyncRouter(res.data || [])
      rewriteRoutes.push({ path: '/:pathMatch(.*)*', redirect: '/404', hidden: true })
      rewriteRoutes.forEach((route) => router.addRoute(route))
      this.addRoutes = rewriteRoutes
      this.routes = constantRoutes.concat(rewriteRoutes)
      return rewriteRoutes
    },
    setSidebarRouters(routes: any[]) {
      this.routes = routes
    },
  },
})
