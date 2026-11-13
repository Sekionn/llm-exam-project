package dk.ek.gruppe_i.llm_project;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling // without this our schedules wont work
public class LlmProjectApplication {
	public static void main(String[] args) {
		SpringApplication.run(LlmProjectApplication.class, args);
	}
}
