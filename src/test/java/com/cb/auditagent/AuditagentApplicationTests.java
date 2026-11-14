package com.cb.auditagent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"auditagent.memory.fts-enabled=false",
		"spring.rabbitmq.listener.simple.auto-startup=false"
})
class AuditagentApplicationTests {

	@MockitoBean
	ChatModel chatModel;

	@MockitoBean
	StreamingChatModel streamingChatModel;

	@Test
	void contextLoads() {
	}

}
