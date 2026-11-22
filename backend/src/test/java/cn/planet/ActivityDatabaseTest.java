package cn.planet;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "DEEPSEEK_API_KEY=test-only")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "PLANET_TEST_DB", matches = "true")
@Transactional
class ActivityDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean HttpClient model;

    @Test
    void generatedStepsArePersistedAndInvalidDurationIsRejected() throws Exception {
        var steps = List.of("准备一个玩偶。", "让玩偶和宝宝打招呼。", "请宝宝回应玩偶。");
        String title = "玩偶你好" + java.util.UUID.randomUUID();
        String guide = json.writeValueAsString(new PlanetApp.ActivityDraft(title, steps.get(0), steps.get(1), steps.get(2), "家长代为挥手。"));
        String envelope = json.writeValueAsString(Map.of("choices", List.of(Map.of(
            "finish_reason", "stop", "message", Map.of("content", guide)))));
        @SuppressWarnings("unchecked")
        HttpResponse<String> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn(envelope);
        when(model.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(reply);

        mvc.perform(post("/api/activities").contentType("application/json")
                .content("{\"agent\":0,\"source\":\"打招呼\",\"minutes\":4}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(model);
        mvc.perform(post("/api/activities").contentType("application/json")
                .content("{\"agent\":0,\"source\":\"打招呼\",\"minutes\":3,\"materials\":\"玩偶\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value(title));
        String stored = jdbc.queryForObject("select steps from activity where title=? and agent=0",
            String.class, title);
        assertThat(json.readTree(stored)).isEqualTo(json.valueToTree(List.of("准备（30秒）：" + steps.get(0),
            "一起玩（90秒）：" + steps.get(1) + "\n简化玩法：家长代为挥手。", "再玩一次（60秒）：" + steps.get(2))));
    }
}
