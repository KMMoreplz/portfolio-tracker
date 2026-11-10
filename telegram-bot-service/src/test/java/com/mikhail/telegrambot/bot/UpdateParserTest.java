package com.mikhail.telegrambot.bot;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateParserTest {

    private final UpdateParser parser = new UpdateParser(new ObjectMapper());

    @Test
    void parse_extractsChatIdTextAndOffset() {
        String json = """
                {"ok":true,"result":[
                  {"update_id":1001,
                   "message":{"message_id":50,"chat":{"id":42,"type":"private"},"text":"/digest 1"}},
                  {"update_id":1002,
                   "message":{"message_id":51,"chat":{"id":42,"type":"private"},"text":"/help"}}
                ]}""";

        List<BotUpdate> updates = parser.parse(json);

        assertThat(updates).hasSize(2);
        assertThat(updates.get(0)).isEqualTo(new BotUpdate(1001L, 42L, "/digest 1"));
        assertThat(updates.get(1)).isEqualTo(new BotUpdate(1002L, 42L, "/help"));
    }

    @Test
    void parse_skipsNonTextMessages() {
        String json = """
                {"ok":true,"result":[
                  {"update_id":1,"message":{"chat":{"id":42,"type":"private"},"photo":[{"file_id":"x"}]}},
                  {"update_id":2,"callback_query":{"id":"cb","data":"x"}},
                  {"update_id":3,"message":{"chat":{"id":42,"type":"private"},"text":"plain text"}}
                ]}""";

        List<BotUpdate> updates = parser.parse(json);

        assertThat(updates).containsExactly(new BotUpdate(3L, 42L, "plain text"));
    }

    @Test
    void parse_emptyResult_returnsEmptyList() {
        assertThat(parser.parse("{\"ok\":true,\"result\":[]}")).isEmpty();
    }

    @Test
    void parse_invalidJson_throwsJacksonException() {
        assertThatThrownBy(() -> parser.parse("not a json"))
                .isInstanceOf(JacksonException.class);
    }
}
