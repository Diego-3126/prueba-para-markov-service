#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <ctype.h>

#define MAX_WORD_LENGTH 100
#define MAX_WORDS 5000
#define MAX_STATES 2000
#define MAX_NEXT_WORDS 100

// Estructura para un estado de Markov
typedef struct {
    char** words;
    int word_count;
    char** next_words;
    int next_count;
    int* frequencies;
} MarkovState;

// Estructura principal del modelo
typedef struct {
    MarkovState* states;
    int state_count;
    int order;
} MarkovModel;

// Función para convertir a minúsculas
void to_lowercase(char* str) {
    for (int i = 0; str[i]; i++) {
        str[i] = tolower(str[i]);
    }
}

// Función para duplicar strings (como strdup pero portable)
char* duplicate_string(const char* s) {
    if (s == NULL) return NULL;
    size_t len = strlen(s) + 1;
    char* copy = malloc(len);
    if (copy != NULL) {
        memcpy(copy, s, len);
    }
    return copy;
}

// Tokenización mejorada
char** tokenize_text(const char* text, int* word_count) {
    if (text == NULL) {
        *word_count = 0;
        return NULL;
    }
    
    char* text_copy = duplicate_string(text);
    if (text_copy == NULL) {
        *word_count = 0;
        return NULL;
    }
    
    char** tokens = malloc(MAX_WORDS * sizeof(char*));
    if (tokens == NULL) {
        free(text_copy);
        *word_count = 0;
        return NULL;
    }
    
    *word_count = 0;
    
    // Tokenización más robusta
    char* token = strtok(text_copy, " \t\n\r,.;:!?\"'()[]{}");
    while (token != NULL && *word_count < MAX_WORDS) {
        to_lowercase(token);
        tokens[*word_count] = duplicate_string(token);
        if (tokens[*word_count] != NULL) {
            (*word_count)++;
        }
        token = strtok(NULL, " \t\n\r,.;:!?\"'()[]{}");
    }
    
    free(text_copy);
    return tokens;
}

// Buscar estado en el modelo
int find_state_index(MarkovModel* model, char** words, int count) {
    for (int i = 0; i < model->state_count; i++) {
        int match = 1;
        for (int j = 0; j < count; j++) {
            if (strcmp(model->states[i].words[j], words[j]) != 0) {
                match = 0;
                break;
            }
        }
        if (match) return i;
    }
    return -1;
}

// ========== API PÚBLICA - PARA JNI ==========

// Crear nuevo modelo Markov
MarkovModel* markov_create_model(int order) {
    MarkovModel* model = malloc(sizeof(MarkovModel));
    if (model == NULL) return NULL;
    
    model->order = order;
    model->state_count = 0;
    model->states = malloc(MAX_STATES * sizeof(MarkovState));
    
    if (model->states == NULL) {
        free(model);
        return NULL;
    }
    
    return model;
}

// Entrenar modelo con texto
void markov_train_model(MarkovModel* model, const char* text) {
    if (model == NULL || text == NULL) return;
    
    int word_count;
    char** tokens = tokenize_text(text, &word_count);
    if (tokens == NULL || word_count <= model->order) {
        if (tokens) {
            for (int i = 0; i < word_count; i++) free(tokens[i]);
            free(tokens);
        }
        return;
    }
    
    // Entrenar el modelo
    for (int i = 0; i < word_count - model->order; i++) {
        // Crear estado actual
        char** current_words = malloc(model->order * sizeof(char*));
        if (current_words == NULL) continue;
        
        for (int j = 0; j < model->order; j++) {
            current_words[j] = tokens[i + j];
        }
        
        char* next_word = tokens[i + model->order];
        
        // Buscar o crear el estado
        int state_index = find_state_index(model, current_words, model->order);
        
        if (state_index == -1) {
            if (model->state_count >= MAX_STATES) {
                free(current_words);
                continue;
            }
            
            state_index = model->state_count;
            model->states[state_index].words = malloc(model->order * sizeof(char*));
            if (model->states[state_index].words == NULL) {
                free(current_words);
                continue;
            }
            
            for (int j = 0; j < model->order; j++) {
                model->states[state_index].words[j] = duplicate_string(current_words[j]);
                if (model->states[state_index].words[j] == NULL) {
                    // Limpiar en caso de error
                    for (int k = 0; k < j; k++) free(model->states[state_index].words[k]);
                    free(model->states[state_index].words);
                    free(current_words);
                    continue;
                }
            }
            model->states[state_index].word_count = model->order;
            model->states[state_index].next_words = malloc(MAX_NEXT_WORDS * sizeof(char*));
            model->states[state_index].frequencies = malloc(MAX_NEXT_WORDS * sizeof(int));
            model->states[state_index].next_count = 0;
            model->state_count++;
        }
        
        // Agregar palabra siguiente
        MarkovState* state = &model->states[state_index];
        int found = 0;
        for (int j = 0; j < state->next_count; j++) {
            if (strcmp(state->next_words[j], next_word) == 0) {
                state->frequencies[j]++;
                found = 1;
                break;
            }
        }
        
        if (!found && state->next_count < MAX_NEXT_WORDS) {
            state->next_words[state->next_count] = duplicate_string(next_word);
            if (state->next_words[state->next_count] != NULL) {
                state->frequencies[state->next_count] = 1;
                state->next_count++;
            }
        }
        
        free(current_words);
    }
    
    // Liberar tokens
    for (int i = 0; i < word_count; i++) {
        free(tokens[i]);
    }
    free(tokens);
}

// Generar texto usando el modelo entrenado
char* markov_generate_text(MarkovModel* model, int length, const char* start) {
    if (model == NULL || model->state_count == 0) {
        return duplicate_string("Modelo no entrenado. Entrene el modelo primero.");
    }
    
    // Inicializar semilla para números aleatorios
    srand(time(NULL));
    
    int start_word_count = 0;
    char** start_words = NULL;
    
    if (start != NULL) {
        start_words = tokenize_text(start, &start_word_count);
    }
    
    // Preparar estado inicial
    char** current_state = malloc(model->order * sizeof(char*));
    if (current_state == NULL) {
        if (start_words) {
            for (int i = 0; i < start_word_count; i++) free(start_words[i]);
            free(start_words);
        }
        return duplicate_string("Error de memoria");
    }
    
    if (start_words != NULL && start_word_count >= model->order) {
        // Usar las últimas palabras del texto inicial
        for (int i = 0; i < model->order; i++) {
            current_state[i] = duplicate_string(start_words[start_word_count - model->order + i]);
            if (current_state[i] == NULL) {
                // Limpiar en caso de error
                for (int j = 0; j < i; j++) free(current_state[j]);
                free(current_state);
                for (int j = 0; j < start_word_count; j++) free(start_words[j]);
                free(start_words);
                return duplicate_string("Error de memoria");
            }
        }
    } else {
        // Estado aleatorio
        int random_index = rand() % model->state_count;
        for (int i = 0; i < model->order; i++) {
            current_state[i] = duplicate_string(model->states[random_index].words[i]);
            if (current_state[i] == NULL) {
                for (int j = 0; j < i; j++) free(current_state[j]);
                free(current_state);
                return duplicate_string("Error de memoria");
            }
        }
    }
    
    // Buffer para el resultado
    char* result = malloc(length * MAX_WORD_LENGTH * sizeof(char));
    if (result == NULL) {
        for (int i = 0; i < model->order; i++) free(current_state[i]);
        free(current_state);
        if (start_words) {
            for (int i = 0; i < start_word_count; i++) free(start_words[i]);
            free(start_words);
        }
        return duplicate_string("Error de memoria");
    }
    result[0] = '\0';
    
    // Generar texto
    for (int i = 0; i < length; i++) {
        int state_index = find_state_index(model, current_state, model->order);
        
        if (state_index == -1) break;
        
        MarkovState* state = &model->states[state_index];
        if (state->next_count == 0) break;
        
        // Selección basada en frecuencia (probabilística)
        int total_freq = 0;
        for (int j = 0; j < state->next_count; j++) {
            total_freq += state->frequencies[j];
        }
        
        int random_val = rand() % total_freq;
        int cumulative_freq = 0;
        char* next_word = NULL;
        
        for (int j = 0; j < state->next_count; j++) {
            cumulative_freq += state->frequencies[j];
            if (random_val < cumulative_freq) {
                next_word = state->next_words[j];
                break;
            }
        }
        
        if (next_word) {
            strcat(result, next_word);
            strcat(result, " ");
            
            // Actualizar estado (desplazar ventana)
            for (int j = 0; j < model->order - 1; j++) {
                free(current_state[j]);
                current_state[j] = duplicate_string(current_state[j + 1]);
                if (current_state[j] == NULL) break;
            }
            if (current_state[model->order - 1]) free(current_state[model->order - 1]);
            current_state[model->order - 1] = duplicate_string(next_word);
        } else {
            break;
        }
    }
    
    // Liberar memoria
    for (int i = 0; i < model->order; i++) {
        if (current_state[i]) free(current_state[i]);
    }
    free(current_state);
    
    if (start_words) {
        for (int i = 0; i < start_word_count; i++) {
            free(start_words[i]);
        }
        free(start_words);
    }
    
    return result;
}

// Liberar memoria del modelo
void markov_free_model(MarkovModel* model) {
    if (model == NULL) return;
    
    for (int i = 0; i < model->state_count; i++) {
        for (int j = 0; j < model->states[i].word_count; j++) {
            free(model->states[i].words[j]);
        }
        free(model->states[i].words);
        
        for (int j = 0; j < model->states[i].next_count; j++) {
            free(model->states[i].next_words[j]);
        }
        free(model->states[i].next_words);
        free(model->states[i].frequencies);
    }
    free(model->states);
    free(model);
}
