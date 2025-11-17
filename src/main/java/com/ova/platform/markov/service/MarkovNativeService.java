package com.ova.platform.markov.service;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class MarkovNativeService {

    private static final Logger logger = LoggerFactory.getLogger(MarkovNativeService.class);


    @Value("${markov.native.library-path:src/main/resources/native/libmarkovova.so}")
    private String libraryPath;

    @Value("${markov.native.library-name:markov}")
    private String libraryName;

    @Value("${markov.native.fallback-enabled:true}")
    private boolean fallbackEnabled;

    @Value("${markov.native.debug-mode:true}")
    private boolean debugMode;

    private boolean modeloInicializado = false;
    private int ordenModeloActual = -1;


    public interface MarkovLibrary extends Library {
        Pointer markov_create_model(int order);
        void markov_train_model(Pointer model, String text);
        String markov_generate_text(Pointer model, int length, String start);
        void markov_free_model(Pointer model);
    }

    private MarkovLibrary nativeLibrary;
    private Pointer model;
    private boolean libraryLoaded = false;

    public MarkovNativeService() {
        loadNativeLibrary();
    }

    private void loadNativeLibrary() {
        try {
            if (debugMode) {
                logger.info("Buscando librería nativa en: {}", libraryPath);
            }

            File libraryFile = new File(libraryPath);
            if (libraryFile.exists()) {
                System.load(libraryFile.getAbsolutePath());
                libraryLoaded = true;
                logger.info("✅ Librería nativa Markov cargada: {}", libraryPath);
            } else {
                logger.warn("⚠️  Librería nativa no encontrada en: {}", libraryPath);


                try {
                    System.loadLibrary(libraryName);
                    libraryLoaded = true;
                    logger.info("✅ Librería nativa cargada por nombre: {}", libraryName);
                } catch (UnsatisfiedLinkError e) {
                    logger.warn("❌ No se pudo cargar la librería nativa. Modo simulación activado.");
                }
            }

            // ✅ CORRECCIÓN: Crear la instancia después de cargar la librería
            if (libraryLoaded) {
                nativeLibrary = Native.load(libraryName, MarkovLibrary.class);
                logger.info("✅ Interfaz JNA inicializada para: {}", libraryName);
            }

        } catch (UnsatisfiedLinkError e) {
            logger.warn("❌ Error cargando librería nativa. Modo simulación: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("❌ Error inicializando JNA: {}", e.getMessage());
        }
    }

    public void initializeModel(int order) {
        if (libraryLoaded && nativeLibrary != null) {
            try {
                // ✅ SOLO inicializar si es diferente al orden actual
                if (modeloInicializado && ordenModeloActual == order) {
                    logger.debug("Modelo ya inicializado con orden {}. Saltando inicialización.", order);
                    return;
                }

                // Liberar modelo anterior si existe
                if (model != null) {
                    markov_free_model(model);
                }

                // Crear nuevo modelo
                model = nativeLibrary.markov_create_model(order);
                ordenModeloActual = order;
                modeloInicializado = true;

                logger.info("Modelo Markov inicializado con orden: {}", order);
            } catch (Exception e) {
                logger.error("Error inicializando modelo Markov nativo", e);
                modeloInicializado = false;
            }
        } else {
            logger.info("Modo simulación: Modelo Markov inicializado (orden {})", order);
            modeloInicializado = true;
            ordenModeloActual = order;
        }
    }

    public void trainModel(String trainingText) {
        if (libraryLoaded && model != null && nativeLibrary != null && modeloInicializado) {
            try {
                nativeLibrary.markov_train_model(model, trainingText);
                logger.info("Modelo Markov entrenado con texto de longitud: {}", trainingText.length());
            } catch (Exception e) {
                logger.error("Error entrenando modelo Markov", e);
            }
        } else if (modeloInicializado) {
            logger.info("Modo simulación: Modelo entrenado con texto de {} caracteres", trainingText.length());
        } else {
            logger.warn("Intento de entrenar modelo no inicializado");
        }
    }

    public String generateText(int length, String startText) {
        // ✅ VERIFICAR que el modelo esté listo
        if (!modeloInicializado) {
            logger.warn("Intento de generar texto con modelo no inicializado");
            return "Error: Modelo no inicializado. Entrene un modelo primero.";
        }

        if (libraryLoaded && model != null && nativeLibrary != null) {
            try {
                String result = nativeLibrary.markov_generate_text(model, length, startText);
                if (debugMode) {
                    logger.debug("Texto generado ({} chars) desde '{}': {}",
                            result != null ? result.length() : 0, startText, result);
                }
                return result;
            } catch (Exception e) {
                logger.error("Error generando texto con Markov", e);
                return "Error en generación: " + e.getMessage();
            }
        } else {
            logger.info("Usando modo simulación para generación de texto desde: '{}'", startText);
            return generateSimulatedText(length, startText);
        }
    }

    private void markov_free_model(Pointer model) {
        if (libraryLoaded && model != null && nativeLibrary != null) {
            try {
                nativeLibrary.markov_free_model(model);
            } catch (Exception e) {
                logger.error("Error liberando modelo Markov", e);
            }
        }
    }

    private String generateSimulatedText(int length, String startText) {
        StringBuilder text = new StringBuilder();

        if (startText != null && !startText.trim().isEmpty()) {
            text.append(startText).append(" ");
            length -= startText.split("\\s+").length;
        }

        String[] words = {"las", "cadenas", "de", "markov", "son", "muy", "útiles",
                "para", "generar", "texto", "automático", "con", "modelos",
                "probabilísticos", "que", "aprenden", "patrones", "del",
                "lenguaje", "natural"};

        for (int i = 0; i < length && i < words.length; i++) {
            text.append(words[i]).append(" ");
        }

        String result = text.toString().trim();
        if (debugMode) {
            logger.debug("Texto simulado generado: {}", result);
        }
        return result;
    }

    public int getOrdenModeloActual() {
        return ordenModeloActual;
    }

    public boolean isModeloListo() {
        return modeloInicializado && (libraryLoaded ? model != null : true);
    }

    public boolean isNativeLibraryLoaded() {
        return libraryLoaded && nativeLibrary != null;
    }

    public String getLibraryStatus() {
        return (libraryLoaded && nativeLibrary != null) ? "ACTIVA" : "SIMULACIÓN";
    }

    public void cleanup() {
        if (libraryLoaded && model != null) {
            markov_free_model(model);
            model = null;
            logger.info("Modelo Markov liberado");
        }
    }
}