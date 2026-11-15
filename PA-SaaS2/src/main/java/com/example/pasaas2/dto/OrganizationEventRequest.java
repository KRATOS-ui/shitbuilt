package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.UUID;

/** Evento organizacional (CU-02): los datos del evento más los empleados a quienes se asigna. */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationEventRequest extends EventRequest {
    @NotEmpty(message = "Debe seleccionar al menos un empleado")
    private List<UUID> userIds;
}
