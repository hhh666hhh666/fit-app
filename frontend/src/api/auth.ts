import request, { unwrap } from './request'

export interface LoginResult {
  token: string
  userId: number
  username: string
}

export function login(username: string, password: string) {
  return unwrap(request.post<LoginResult>('/auth/login', { username, password }))
}

export function register(username: string, password: string) {
  return unwrap(request.post<LoginResult>('/auth/register', { username, password }))
}
