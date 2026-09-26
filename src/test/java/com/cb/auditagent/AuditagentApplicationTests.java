package com.cb.auditagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.ai.model.chat=none",
		"spring.ai.openai.api-key=dummy",
		"auditagent.memory.fts-enabled=false",
		"auditagent.database.path=target/test-context.duckdb"
})
class AuditagentApplicationTests {
	@MockitoBean
	ChatModel chatModel;

	@MockitoBean
	dev.langchain4j.model.chat.ChatModel agentModel;

	@Test
	void contextLoads() {
	}

}
