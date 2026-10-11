package com.example.fitapp.module.workout.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkoutNoteDTO {

    @Size(max = 500, message = "备注过长")
    private String note = "";
}
