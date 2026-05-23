package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.database=H2",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
class ImportExportCsvFileIT {

    private static final Path OUTPUT_DIR = Path.of("target", "import-export-csv").toAbsolutePath().normalize();
    private static final Path IMPORT_INPUT = OUTPUT_DIR.resolve("import-input.csv");
    private static final Path IMPORT_RESPONSE = OUTPUT_DIR.resolve("import-response.json");
    private static final Path EXPORT_OUTPUT = OUTPUT_DIR.resolve("export-output.csv");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void importAndExportUseRealCsvFilesYouCanOpen() throws Exception {
        Files.createDirectories(OUTPUT_DIR);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = seedUser("csv-user-" + suffix);
        Project project = seedProject("CSV Project " + suffix, user);
        String token = "Bearer " + jwtTokenService.generateToken(user);

        String importCsv = """
                id,title,description,status,priority,type,assigneeId
                ,Imported bug,"Imported from a real CSV file",TODO,HIGH,BUG,
                ,"Imported feature, quoted title","Description with, comma",IN_PROGRESS,MEDIUM,FEATURE,
                ,Broken row,,TODO,LOW,BUG,
                """;
        Files.writeString(IMPORT_INPUT, importCsv, StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                IMPORT_INPUT.getFileName().toString(),
                "text/csv",
                Files.readAllBytes(IMPORT_INPUT)
        );

        MvcResult importResult = mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", project.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        String importResponse = importResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(IMPORT_RESPONSE, importResponse, StandardCharsets.UTF_8);

        assertThat(importResponse).contains("\"created\":2");
        assertThat(importResponse).contains("\"failed\":1");
        assertThat(importResponse).contains("description is required");

        MvcResult exportResult = mockMvc.perform(get("/tickets/export")
                        .param("projectId", project.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        String exportCsv = exportResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(EXPORT_OUTPUT, exportCsv, StandardCharsets.UTF_8);

        assertThat(Files.readString(IMPORT_INPUT, StandardCharsets.UTF_8))
                .contains("Imported bug")
                .contains("\"Imported feature, quoted title\"");
        assertThat(Files.readString(EXPORT_OUTPUT, StandardCharsets.UTF_8))
                .startsWith("id,title,description,status,priority,type,assigneeId")
                .contains("Imported bug")
                .contains("\"Imported feature, quoted title\"")
                .doesNotContain("Broken row");
    }

    private User seedUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("CSV Test User");
        user.setRole(Role.DEVELOPER);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Project seedProject(String name, User owner) {
        Project project = new Project();
        project.setKey("CSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        project.setName(name);
        project.setDescription("Project for import/export CSV file test");
        project.setOwner(owner);
        return projectRepository.saveAndFlush(project);
    }
}
