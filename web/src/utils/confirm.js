import { ElMessageBox } from 'element-plus'

export async function askConfirm(message, title = '确认', options = {}) {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      ...options,
    })
    return true
  } catch {
    return false
  }
}
