import request, { unwrap } from './request'

export interface WorkoutSetItem {
  weight: number
  reps: number
}

export interface WorkoutBlockItem {
  name: string
  sets: WorkoutSetItem[]
}

export interface WorkoutDetail {
  id: number
  title: string
  createdAt: string
  durationSec: number
  totalVolume: number
  note: string
  blocks: WorkoutBlockItem[]
}

export interface WorkoutListItem {
  id: number
  title: string
  firstExercise: string | null
  durationSec: number
  totalVolume: number
  createdAt: string
}

export interface LastRecord {
  weight: number
  reps: number
  performedAt: string
}

export interface CreateWorkoutPayload {
  /** 来源计划（自由训练不传）。 */
  planId?: number | null
  title: string
  durationSec: number
  note?: string
  blocks: {
    exerciseId?: number | null
    name: string
    sets: { weight: number; reps: number }[]
  }[]
}

export function createWorkout(payload: CreateWorkoutPayload) {
  return unwrap(request.post<WorkoutDetail>('/workouts', payload))
}

export function listWorkouts(limit = 50) {
  return unwrap(request.get<WorkoutListItem[]>('/workouts', { limit }))
}

export function getWorkout(id: number) {
  return unwrap(request.get<WorkoutDetail>(`/workouts/${id}`))
}

export function getLastRecord(exerciseId: number) {
  return unwrap(request.get<LastRecord | null>('/workouts/last-record', { exerciseId }))
}

export function updateWorkoutNote(id: number, note: string) {
  return unwrap(request.put<void>(`/workouts/${id}/note`, { note }))
}
