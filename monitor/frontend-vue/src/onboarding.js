// 新手引导 (FTUE / Onboarding) 相关的共享常量与工具方法
export const ONBOARDING_KEY = 'ftue_completed'
export const ONBOARDING_EVENT = 'start-onboarding'

// 是否已完成新手引导
export function isOnboardingCompleted() {
  return localStorage.getItem(ONBOARDING_KEY) === 'true'
}

// 标记新手引导已完成（完成后不再自动显示）
export function markOnboardingCompleted() {
  localStorage.setItem(ONBOARDING_KEY, 'true')
}

// 重置新手引导（用于“重新开始引导”）
export function resetOnboarding() {
  localStorage.removeItem(ONBOARDING_KEY)
}
