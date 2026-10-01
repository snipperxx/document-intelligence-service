package io.github.snipperxx.docintel.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    protected Api api() {
        return new Api(mvc);
    }
}
