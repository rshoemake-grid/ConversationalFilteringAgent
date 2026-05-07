package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.gemini.GeminiModelsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModelsControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ModelsController(Optional.empty()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void listModels_returns503WhenNoApiKey() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void listModels_returns200WithModelsWhenServicePresent() throws Exception {
        var stubService = new StubGeminiModelsService();
        var controller = new ModelsController(Optional.of(stubService));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$[1]").value("gemini-2.5-pro"));
    }

    private static class StubGeminiModelsService extends GeminiModelsService {
        StubGeminiModelsService() {
            super(mock(Environment.class));
        }

        @Override
        public List<String> listModels() {
            return List.of("gemini-2.5-flash", "gemini-2.5-pro");
        }
    }
}
