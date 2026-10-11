// 后端统一响应信封：{ code, message, data, timestamp }
// 实现见 backend 的 common/Result（Record 结构）；成功码 0，401 也在 body 里。
export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}
