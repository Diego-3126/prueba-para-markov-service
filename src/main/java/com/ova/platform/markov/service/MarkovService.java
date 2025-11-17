package com.ova.platform.markov.service;

import com.ova.platform.markov.model.request.MarkovGenerateRequest;
import com.ova.platform.markov.model.request.TrainModelRequest;
import com.ova.platform.markov.model.response.MarkovGenerateResponse;
import com.ova.platform.markov.model.response.TrainModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MarkovService {

    private static final Logger logger = LoggerFactory.getLogger(MarkovService.class);

    @Autowired
    private MarkovNativeService nativeService;

    // ✅ NUEVO: Estado compartido del modelo entrenado
    private String ultimoTextoEntrenamiento;
    private int ultimoOrdenEntrenado;
    private boolean modeloEntrenado = false;

    // ✅ METODO NUEVO - HU-301: ENTRENAR MODELO
    public TrainModelResponse entrenarModelo(TrainModelRequest request) {
        long startTime = System.currentTimeMillis();
        TrainModelResponse response = new TrainModelResponse();

        try {
            logger.info("Iniciando entrenamiento de modelo - Orden: {}, Longitud texto: {}",
                    request.getOrden(), request.getTextoEntrenamiento().length());

            // ✅ Entrenar modelo con parámetros proporcionados
            nativeService.initializeModel(request.getOrden());
            nativeService.trainModel(request.getTextoEntrenamiento());

            // ✅ GUARDAR ESTADO COMPARTIDO para usar en generación
            this.ultimoTextoEntrenamiento = request.getTextoEntrenamiento();
            this.ultimoOrdenEntrenado = request.getOrden();
            this.modeloEntrenado = true;

            long endTime = System.currentTimeMillis();

            // Estadísticas
            int vocabularioSize = calcularTamanoVocabulario(request.getTextoEntrenamiento());
            int estadosCount = calcularNumeroEstados(request.getTextoEntrenamiento(), request.getOrden());

            response.setExito(true);
            response.setMensaje("Modelo entrenado exitosamente. Ahora puedes generar texto usando este modelo.");
            response.setOrdenModelo(request.getOrden());
            response.setLongitudTexto(request.getTextoEntrenamiento().length());
            response.setVocabularioSize(vocabularioSize);
            response.setEstadosCount(estadosCount);
            response.setTiempoEntrenamientoMs(endTime - startTime);
            response.setModeloId("markov-model-" + System.currentTimeMillis());

            logger.info("Entrenamiento completado - Tiempo: {}ms, Vocabulario: {} palabras, Estados: {}",
                    response.getTiempoEntrenamientoMs(), vocabularioSize, estadosCount);

        } catch (Exception e) {
            logger.error("Error en entrenamiento de modelo Markov", e);
            response.setExito(false);
            response.setMensaje("Error entrenando modelo: " + e.getMessage());
            response.setTiempoEntrenamientoMs(System.currentTimeMillis() - startTime);
        }

        return response;
    }

    // ✅ MÉTODO ACTUALIZADO - HU-302: GENERAR TEXTO USANDO EXCLUSIVAMENTE MODELO ENTRENADO
    public MarkovGenerateResponse generarTexto(MarkovGenerateRequest request) {
        long startTime = System.currentTimeMillis();
        MarkovGenerateResponse response = new MarkovGenerateResponse();

        try {
            logger.info("Iniciando generación de texto - Inicio: '{}', Longitud: {}",
                    request.getTextoInicio(), request.getLongitud());

            // ✅ VERIFICAR SI HAY MODELO ENTRENADO
            if (!modeloEntrenado) {
                response.setExito(false);
                response.setMensaje("No hay modelo entrenado. Por favor, entrena un modelo primero usando el endpoint /api/markov/train");
                response.setTiempoProcesamientoMs(System.currentTimeMillis() - startTime);
                return response;
            }

            // ✅ VERIFICAR COMPATIBILIDAD DE ORDEN
            if (request.getOrden() != ultimoOrdenEntrenado) {
                logger.warn("Orden solicitado ({}) diferente al orden entrenado ({}). Usando orden entrenado.",
                        request.getOrden(), ultimoOrdenEntrenado);
            }

            // ✅ USAR EXCLUSIVAMENTE EL MODELO YA ENTRENADO - SIN RE-ENTRENAR
            logger.info("Generando texto con modelo previamente entrenado - Orden: {}, Texto entrenamiento: {} caracteres",
                    ultimoOrdenEntrenado, ultimoTextoEntrenamiento.length());

            // ✅ IMPORTANTE: No inicializar ni entrenar de nuevo - usar el modelo existente
            // El modelo YA está listo desde el endpoint de entrenamiento

            // Generar texto usando el modelo entrenado
            String textoGenerado = nativeService.generateText(
                    request.getLongitud(),
                    request.getTextoInicio()
            );

            long endTime = System.currentTimeMillis();

            // Construir respuesta
            response.setExito(true);
            response.setTextoGenerado(textoGenerado);
            response.setTextoInicio(request.getTextoInicio());
            response.setLongitudSolicitada(request.getLongitud());
            response.setLongitudGenerada(textoGenerado != null ? textoGenerado.split("\\s+").length : 0);
            response.setTiempoProcesamientoMs(endTime - startTime);
            response.setModeloUtilizado("markov-order-" + ultimoOrdenEntrenado);
            response.setMensaje("Texto generado usando modelo entrenado con: " +
                    ultimoTextoEntrenamiento.length() + " caracteres");

            logger.info("Generación completada - Tiempo: {}ms, Longitud generada: {}, Texto: {}...",
                    response.getTiempoProcesamientoMs(), response.getLongitudGenerada(),
                    textoGenerado != null ? textoGenerado.substring(0, Math.min(50, textoGenerado.length())) : "null");

        } catch (Exception e) {
            logger.error("Error en generación de texto Markov", e);
            response.setExito(false);
            response.setMensaje("Error generando texto: " + e.getMessage());
            response.setTiempoProcesamientoMs(System.currentTimeMillis() - startTime);
        }

        return response;
    }

    // ✅ NUEVO METODO: Verificar estado del modelo
    public boolean isModeloEntrenado() {
        return modeloEntrenado;
    }

    // ✅ NUEVO METODO: Obtener información del modelo entrenado
    public String getInfoModeloEntrenado() {
        if (!modeloEntrenado) {
            return "No hay modelo entrenado";
        }
        return String.format("Modelo orden-%d entrenado con %d caracteres",
                ultimoOrdenEntrenado, ultimoTextoEntrenamiento.length());
    }

    // Métodos auxiliares (sin cambios)
    private int calcularTamanoVocabulario(String texto) {
        if (texto == null || texto.trim().isEmpty()) return 0;
        String[] palabras = texto.toLowerCase().split("\\s+");
        return (int) java.util.Arrays.stream(palabras).distinct().count();
    }

    private int calcularNumeroEstados(String texto, int orden) {
        if (texto == null || texto.trim().isEmpty()) return 0;
        String[] palabras = texto.toLowerCase().split("\\s+");
        return Math.max(0, palabras.length - orden);
    }

    public boolean isNativeIntegrationActive() {
        return nativeService.isNativeLibraryLoaded();
    }
}