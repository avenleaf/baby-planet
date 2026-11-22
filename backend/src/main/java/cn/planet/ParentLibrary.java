package cn.planet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account/library")
class ParentLibrary {
    private static final List<String> COLLECTIONS = List.of("planetLogs", "planetSaved", "planet.storybooks.v1");
    private final JdbcTemplate db;
    private final ObjectMapper json;

    ParentLibrary(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    record Library(int revision, JsonNode data) {}

    @GetMapping
    Library read(Principal parent) throws Exception {
        var records = db.query("""
            SELECT l.revision, l.library FROM parent_library l
            JOIN parent_account p ON p.id = l.account_id WHERE p.email = ?
            """, (row, index) -> new String[]{row.getString(1), row.getString(2)}, parent.getName());
        if (records.isEmpty()) return new Library(0, json.createObjectNode());
        return new Library(Integer.parseInt(records.get(0)[0]), json.readTree(records.get(0)[1]));
    }

    @PutMapping
    @Transactional
    Library save(Principal parent, @RequestBody Library library) throws Exception {
        JsonNode data = library.data();
        if (library.revision() < 0 || data == null || !data.isObject() || data.size() != COLLECTIONS.size()
            || COLLECTIONS.stream().anyMatch(key -> !data.path(key).isArray() || data.path(key).size() > 500))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        byte[] encoded = json.writeValueAsBytes(data);
        if (encoded.length > 1_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        var text = new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
        db.update("""
            INSERT INTO parent_library(account_id, library)
            SELECT id, JSON_OBJECT() FROM parent_account WHERE email = ?
            ON DUPLICATE KEY UPDATE revision = revision
            """, parent.getName());
        int updated = db.update("""
            UPDATE parent_library l JOIN parent_account p ON l.account_id = p.id
            SET l.library = ?, l.revision = l.revision + 1 WHERE p.email = ? AND l.revision = ?
            """, text, parent.getName(), library.revision());
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return new Library(library.revision() + 1, data);
    }
}
