package com.SpringResumeParser.ai_chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiChatbotApplication {

	public static void main(String[] args) {

		/**
		 * Application entry point.
		 *
		 * SpringApplication.run() does all the heavy lifting:
		 *
		 * 1. Creates appropriate ApplicationContext (web vs non-web)
		 * 2. Registers a shutdown hook for graceful shutdown
		 * 3. Refreshes the context (instantiates all beans)
		 * 4. Triggers ApplicationRunner/CommandLineRunner beans
		 * 5. Starts listening for HTTP requests
		 *
		 * STARTUP FLOW:
		 * ─────────────
		 * main() called
		 *     │
		 *     ▼
		 * SpringApplication.run()
		 *     │
		 *     ├──► Load application.properties / application.yml
		 *     │
		 *     ├──► Component scan (find @Service, @Controller, etc.)
		 *     │
		 *     ├──► Auto-configuration (create beans based on classpath)
		 *     │        └──► OpenAiChatModel auto-configured here!
		 *     │
		 *     ├──► Create user-defined beans (@Bean methods)
		 *     │        └──► MultiModelConfig beans created here!
		 *     │
		 *     ├──► Dependency injection (wire everything together)
		 *     │
		 *     └──► Start embedded Tomcat on port
		 *              │
		 *              ▼
		 *         Ready to serve requests!
		 */
		 SpringApplication.run(AiChatbotApplication.class, args);
	}

}
