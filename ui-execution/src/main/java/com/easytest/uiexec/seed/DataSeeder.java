package com.easytest.uiexec.seed;

import com.easytest.uiexec.domain.SkillsPointerEntity;
import com.easytest.uiexec.repository.SkillsPointerRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Inserts the empty single-row Skills pointer when the database has none. */
@Component
public class DataSeeder implements ApplicationRunner {

    private final SkillsPointerRepository skillsPointerRepository;

    public DataSeeder(SkillsPointerRepository skillsPointerRepository) {
        this.skillsPointerRepository = skillsPointerRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (skillsPointerRepository.findById(SkillsPointerEntity.SINGLETON_ID).isEmpty()) {
            skillsPointerRepository.save(new SkillsPointerEntity(SkillsPointerEntity.SINGLETON_ID,
                    "", "main", null, "system", Instant.now(), null));
        }
    }
}
