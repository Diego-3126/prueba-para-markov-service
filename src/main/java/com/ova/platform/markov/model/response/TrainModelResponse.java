package com.ova.platform.markov.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrainModelResponse {
    private boolean exito;
    private String mensaje;
    private int ordenModelo;
    private int longitudTexto;
    private int vocabularioSize;
    private int estadosCount;
    private long tiempoEntrenamientoMs;
    private String modeloId;

    // Constructores
    public TrainModelResponse() {}

    public TrainModelResponse(boolean exito, String mensaje) {
        this.exito = exito;
        this.mensaje = mensaje;
    }

    // Getters and Setters
    public boolean isExito() { return exito; }
    public void setExito(boolean exito) { this.exito = exito; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public int getOrdenModelo() { return ordenModelo; }
    public void setOrdenModelo(int ordenModelo) { this.ordenModelo = ordenModelo; }

    public int getLongitudTexto() { return longitudTexto; }
    public void setLongitudTexto(int longitudTexto) { this.longitudTexto = longitudTexto; }

    public int getVocabularioSize() { return vocabularioSize; }
    public void setVocabularioSize(int vocabularioSize) { this.vocabularioSize = vocabularioSize; }

    public int getEstadosCount() { return estadosCount; }
    public void setEstadosCount(int estadosCount) { this.estadosCount = estadosCount; }

    public long getTiempoEntrenamientoMs() { return tiempoEntrenamientoMs; }
    public void setTiempoEntrenamientoMs(long tiempoEntrenamientoMs) { this.tiempoEntrenamientoMs = tiempoEntrenamientoMs; }

    public String getModeloId() { return modeloId; }
    public void setModeloId(String modeloId) { this.modeloId = modeloId; }
}