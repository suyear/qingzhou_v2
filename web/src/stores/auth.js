import { ref, computed } from 'vue'

const TOKEN_KEY = 'qz_auth_token'
const USER_KEY = 'qz_auth_user'

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const user = ref(readUser())

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function useAuthStore() {
  const isLoggedIn = computed(() => !!token.value)
  const roles = computed(() => user.value?.roles || [])
  const permissions = computed(() => user.value?.permissions || [])
  const isAdmin = computed(() => roles.value.includes('ADMIN') || permissions.value.includes('system:admin'))
  const canWrite = computed(() =>
    roles.value.includes('ADMIN')
    || roles.value.includes('DEVELOPER')
    || permissions.value.some((p) => p.endsWith(':write')))
  const isViewer = computed(() => roles.value.includes('VIEWER') && !canWrite.value)

  function setSession(session) {
    token.value = session?.token || token.value || ''
    if (session?.token) {
      token.value = session.token
    }
    if (session && (session.userId || session.username)) {
      user.value = {
        userId: session.userId,
        username: session.username,
        displayName: session.displayName,
        roles: session.roles || [],
        permissions: session.permissions || [],
        mustChangePassword: !!session.mustChangePassword,
      }
    } else if (!session) {
      user.value = null
      token.value = ''
    }
    if (token.value && user.value) {
      localStorage.setItem(TOKEN_KEY, token.value)
      localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    } else if (!token.value) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }

  function clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  function getToken() {
    return token.value
  }

  function hasRole(role) {
    return roles.value.includes(role)
  }

  function hasPermission(code) {
    if (isAdmin.value) return true
    return permissions.value.includes(code)
  }

  function hasAnyPermission(codes) {
    return codes.some((c) => hasPermission(c))
  }

  return {
    token,
    user,
    isLoggedIn,
    roles,
    permissions,
    isAdmin,
    canWrite,
    isViewer,
    setSession,
    clearSession,
    getToken,
    hasRole,
    hasPermission,
    hasAnyPermission,
  }
}
