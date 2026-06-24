import { describe, it, expect } from 'vitest'
import { hasAllPermissions } from '@/utils/permission'
import { sidebarMenuConfig } from '@/config/sidebar-menu'

describe('Sidebar permission filtering', () => {
  it('hasAllPermissions requires ALL keys for hierarchical permissionKeys', () => {
    const userPerms = ['dashboard', 'knowledge', 'knowledge-qualification']

    expect(hasAllPermissions(userPerms, ['knowledge', 'knowledge-archive'])).toBe(false)
    expect(hasAllPermissions(userPerms, ['knowledge', 'knowledge-qualification'])).toBe(true)
  })

  it('hasAllPermissions with all perm grants access to everything', () => {
    expect(hasAllPermissions(['all'], ['knowledge', 'knowledge-archive'])).toBe(true)
  })

  it('hasAllPermissions with empty required returns true', () => {
    expect(hasAllPermissions([], [])).toBe(true)
  })
})

describe('sidebar-menu permissionKeys changes', () => {
  it('submenu permissionKeys should only contain self permission, not parent', () => {
    const knowledgeMenu = sidebarMenuConfig.find(m => m.name === 'Knowledge')
    expect(knowledgeMenu).toBeDefined()
    
    const qualificationChild = knowledgeMenu.children.find(c => c.name === 'Qualification')
    expect(qualificationChild.meta.permissionKeys).toEqual(['knowledge-qualification'])
    expect(qualificationChild.meta.permissionKeys).not.toContain('knowledge')
  })

  it('resource submenus should only contain their own permission keys', () => {
    const resourceMenu = sidebarMenuConfig.find(m => m.name === 'Resource')
    expect(resourceMenu).toBeDefined()
    
    const accountChild = resourceMenu.children.find(c => c.name === 'Account')
    expect(accountChild.meta.permissionKeys).toEqual(['resource-account'])
    expect(accountChild.meta.permissionKeys).not.toContain('resource')
  })

  it('settings submenus should only contain their own permission keys', () => {
    const settingsMenu = sidebarMenuConfig.find(m => m.name === 'Settings')
    expect(settingsMenu).toBeDefined()
    
    const workflowChild = settingsMenu.children.find(c => c.name === 'WorkflowFormDesigner')
    expect(workflowChild.meta.permissionKeys).toEqual(['settings-workflow-forms'])
    expect(workflowChild.meta.permissionKeys).not.toContain('settings')
  })

  it('user with only submenu permission can see parent menu via child visibility', () => {
    const userPerms = ['knowledge-qualification']
    
    const knowledgeMenu = sidebarMenuConfig.find(m => m.name === 'Knowledge')
    const qualificationChild = knowledgeMenu.children.find(c => c.name === 'Qualification')
    
    expect(hasAllPermissions(userPerms, qualificationChild.meta.permissionKeys)).toBe(true)
  })

  it('user without any submenu permission cannot see parent menu', () => {
    const userPerms = ['dashboard']
    
    const knowledgeMenu = sidebarMenuConfig.find(m => m.name === 'Knowledge')
    const hasVisibleChildren = knowledgeMenu.children.some(child => 
      hasAllPermissions(userPerms, child.meta.permissionKeys)
    )
    
    expect(hasVisibleChildren).toBe(false)
  })

  it('top-level menu without children still checks its own permissionKeys', () => {
    const userPerms = []
    const dashboardMenu = sidebarMenuConfig.find(m => m.name === 'Dashboard')
    
    expect(hasAllPermissions(userPerms, dashboardMenu.meta.permissionKeys)).toBe(false)
  })
})
