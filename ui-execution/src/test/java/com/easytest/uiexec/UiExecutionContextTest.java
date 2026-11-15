package com.easytest.uiexec;

import com.easytest.uiexec.repository.SkillsPointerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full context load against an in-memory H2 database. The core URL points to a
 * closed port so no test can accidentally reach easytest-server, and the
 * watchdog is disabled to keep the test deterministic.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:uiexec-context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "easytest.uiexec.core-base-url=http://127.0.0.1:1",
        "easytest.uiexec.watchdog-enabled=false",
        "easytest.uiexec.artifact-dir=./target/test-artifacts"
})
class UiExecutionContextTest {

    @Autowired
    private SkillsPointerRepository skillsPointerRepository;

    @Test
    void contextLoadsAndSeedsEmptySkillsPointer() {
        assertThat(skillsPointerRepository.findById(1L)).isPresent()
                .get()
                .satisfies(pointer -> {
                    assertThat(pointer.getGitUrl()).isEmpty();
                    assertThat(pointer.getBranch()).isEqualTo("main");
                    assertThat(pointer.getActiveCommit()).isNull();
                });
    }
}
