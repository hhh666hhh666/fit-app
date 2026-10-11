import request, { unwrap } from './request'

export interface ExerciseItem {
  id: number
  name: string
  muscle: string
  preset: boolean
}

export function listExercises(params?: { muscle?: string; keyword?: string }) {
  return unwrap(request.get<ExerciseItem[]>('/exercises', params))
}
