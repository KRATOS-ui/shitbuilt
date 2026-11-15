package com.example.pasaas2.service;

import com.example.pasaas2.ai.AiDraftParser;
import com.example.pasaas2.dto.EventPreview;
import com.example.pasaas2.dto.GeminiRequest;
import com.example.pasaas2.dto.GeminiResponse;
import com.example.pasaas2.dto.RecurrenceRequest;
import com.example.pasaas2.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * CU-03: convierte texto en lenguaje natural en un BORRADOR de evento. No guarda nada:
 * el usuario debe confirmar explícitamente la vista previa antes de crear el evento.
 */
@Slf4j
@Service
public class GeminiAiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GeminiAiService(
            @Value("${api.gemini.url}") String apiUrl,
            @Value("${api.gemini.key:}") String apiKey,
            @Value("${api.gemini.timeout-seconds:5}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        // Tiempo máximo de espera (RNF de usabilidad: la vista previa debe responder en ~5 s)
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder().baseUrl(apiUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public EventPreview previewEventFromText(String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalServiceException(
                    "La creación con IA no está configurada. Puedes crear el evento manualmente.");
        }

        LocalDateTime now = LocalDateTime.now();
        String currentDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        String dayOfWeek = now.getDayOfWeek().toString();

        String systemPrompt = String.format("""
            Eres un asistente de calendario estricto. Tu única tarea es extraer la información del evento del texto del usuario.
            Hoy es %s, %s.

            Reglas estrictas:
            1. Asume que la zona horaria es local.
            2. Si no se especifica la duración, asume 1 hora.
            3. Si el texto no permite saber un dato, usa null en ese campo. No inventes datos.
            4. Si el evento se repite, completa "recurrence"; si no, usa null.
            5. Responde ÚNICAMENTE con un objeto JSON válido. Nada de texto adicional.

            El JSON debe tener exactamente esta estructura:
            {
              "title": "String (resumen corto del evento) o null",
              "description": "String (detalles adicionales) o null",
              "startTime": "YYYY-MM-DDTHH:mm:ss o null",
              "endTime": "YYYY-MM-DDTHH:mm:ss o null",
              "recurrence": null o {
                "frequency": "DAILY | WEEKLY | MONTHLY",
                "intervalCount": 1,
                "daysOfWeek": ["MONDAY"] (solo para WEEKLY, o null),
                "untilDate": "YYYY-MM-DD o null"
              }
            }

            Texto del usuario: "%s"
            """, dayOfWeek, currentDateTime, userPrompt.replace("\"", "'"));

        GeminiRequest requestPayload = GeminiRequest.builder()
                .contents(List.of(GeminiRequest.Content.builder()
                        .parts(List.of(GeminiRequest.Part.builder().text(systemPrompt).build()))
                        .build()))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .responseMimeType("application/json")
                        .build())
                .build();

        GeminiResponse response;
        try {
            response = restClient.post()
                    // La clave va en cabecera para que no quede registrada en URLs ni logs
                    .header("x-goog-api-key", apiKey)
                    .body(requestPayload)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (Exception e) {
            log.warn("Fallo al consultar el servicio de IA: {}", e.getMessage());
            throw new ExternalServiceException(
                    "El servicio de IA no respondió a tiempo. Intenta de nuevo o crea el evento manualmente.");
        }

        AiDraftParser.Draft draft;
        try {
            String jsonOutput = response.getCandidates().get(0).getContent().getParts().get(0).getText();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonOutput, Map.class);
            draft = AiDraftParser.fromMap(data);
        } catch (Exception e) {
            // Respuesta ilegible: no es un error fatal, el usuario completa los datos en la vista previa
            log.warn("Respuesta de la IA no interpretable: {}", e.getMessage());
            draft = AiDraftParser.fromMap(null);
        }

        RecurrenceRequest recurrence = null;
        if (draft.frequency() != null) {
            recurrence = new RecurrenceRequest(draft.frequency(), draft.intervalCount(),
                    draft.daysOfWeek(), draft.untilDate());
        }

        return EventPreview.builder()
                .title(draft.title())
                .description(draft.description())
                .startTime(draft.startTime())
                .endTime(draft.endTime())
                .recurrence(recurrence)
                .missingFields(draft.missing())
                .complete(draft.complete())
                .build();
    }
}
