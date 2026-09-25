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
  const isAdmin = computed(() => roles.value.includes('ADMIN'))
  const canWrite = computed(() => roles.value.includes('ADMIN') || roles.value.includes('DEVELOPER'))
  const isViewer = computed(() => roles.value.includes('VIEWER') && !canWrite.value)

  function setSession(session) {
    token.value = session?.token || ''
    user.value = session
      ? {
          userId: session.userId,
          username: session.username,
          displayName: session.displayName,
          roles: session.roles || [],
          mustChangePassword: !!session.mustChangePassword,
        }
      : null
    if (token.value) {
      localStorage.setItem(TOKEN_KEY, token.value)
      localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    } else {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }

  function clearSession() {
    setSession(null)
  }

  function getToken() {
    return token.value
  }

  function hasRole(role) {
    return roles.value.includes(role)
  }

  return {
    token,
    user,
    isLoggedIn,
    roles,
    isAdmin,
    canWrite,
    isViewer,
    setSession,
    clearSession,
    getToken,
    hasRole,
  }
}
