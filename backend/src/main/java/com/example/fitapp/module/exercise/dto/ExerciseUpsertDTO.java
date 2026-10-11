package com.example.fitapp.module.exercise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExerciseUpsertDTO {

    @NotBlank(message = "动作名不能为空")
    @Size(max = 64, message = "动作名过长")
    private String name;

    @NotBlank(message = "肌群不能为空")
    private String muscle;
}
