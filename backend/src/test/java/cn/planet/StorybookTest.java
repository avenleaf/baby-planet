package cn.planet;

import java.net.http.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorybookTest {
    @Test
    void optionalEpisodeAndPageValidation() throws Exception {
        ObjectMapper json = new ObjectMapper();
        HttpClient http = mock(HttpClient.class);
        PlanetApp app = new PlanetApp(null, json, http, "test-only");
        assertEquals(400, assertThrows(ResponseStatusException.class, () ->
            app.storybook(new PlanetApp.BookInput(" ", null, null))).getStatusCode().value());
        verifyNoInteractions(http);
        var page = new PlanetApp.BookPage("小熊出门了。","你看见谁？","forest","bear");
        var book = new PlanetApp.Book("森林早晨", Collections.nCopies(4, page));
        var envelope = Map.of("choices", List.of(Map.of("finish_reason","stop","message",
            Map.of("content",json.writeValueAsString(book)))));
        @SuppressWarnings("unchecked") HttpResponse<String> reply = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(reply);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn(json.writeValueAsString(envelope),
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}");
        assertEquals(book, app.storybook(new PlanetApp.BookInput("森林朋友", null, null)));
        assertEquals(502, assertThrows(ResponseStatusException.class, () ->
            app.storybook(new PlanetApp.BookInput("森林朋友","第2集","看见小鸟"))).getStatusCode().value());
    }
}
